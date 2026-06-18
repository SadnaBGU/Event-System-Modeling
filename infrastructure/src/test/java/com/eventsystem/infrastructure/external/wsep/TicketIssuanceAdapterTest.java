package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketIssuanceAdapterTest {

    private static final Money USD_100 = Money.of(new BigDecimal("100"), "USD");
    private static final BuyerReference MEMBER_BUYER = new BuyerReference(BuyerType.MEMBER, null, "849302");
    private static final BuyerReference GUEST_BUYER = new BuyerReference(BuyerType.GUEST, "session-abc", null);

    // REQ: SYS-04, USR-10, UC 9, UAT-26 - general admission ticket issuance sends
    // quantity.
    @Test
    void issueTickets_forGeneralAdmission_sendsIssueTicketWithQuantity() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-GA-1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("Golden Ring", null, 2, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER);

            assertTrue(result.success());
            assertEquals("TIX-GA-1", result.issuanceConfirmationId());
            assertEquals(List.of("TIX-GA-1"), result.issuedTicketCodes());

            assertEquals(1, server.requestCount());
            assertEquals("issue_ticket", server.request(0).form().get("action_type"));
            assertEquals("849302", server.request(0).form().get("customer_id"));
            assertEquals("EVT-9923", server.request(0).form().get("event_id"));
            assertEquals("Golden Ring", server.request(0).form().get("zone"));
            assertEquals("2", server.request(0).form().get("quantity"));
            assertFalse(server.request(0).form().containsKey("is_seating"));
            assertFalse(server.request(0).form().containsKey("seats"));
        }
    }

    // REQ: SYS-04, USR-07, UC 9, UAT-26 - assigned seating ticket issuance sends
    // is_seating and seats.
    @Test
    void issueTickets_forAssignedSeat_sendsIssueTicketWithSeatsJson() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-SEAT-1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("VIP Balcony", "4:12", 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER);

            assertTrue(result.success());
            assertEquals("TIX-SEAT-1", result.issuanceConfirmationId());
            assertEquals(List.of("TIX-SEAT-1"), result.issuedTicketCodes());

            assertEquals("issue_ticket", server.request(0).form().get("action_type"));
            assertEquals("VIP Balcony", server.request(0).form().get("zone"));
            assertEquals("true", server.request(0).form().get("is_seating"));
            assertEquals("[{\"row\":4,\"seat\":12}]", server.request(0).form().get("seats"));
            assertFalse(server.request(0).form().containsKey("quantity"));
        }
    }

    // REQ: SYS-04, UC 9 - guest buyer uses session id as WSEP customer_id.
    @Test
    void issueTickets_forGuestBuyer_usesSessionIdAsCustomerId() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-GUEST-1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("Standing", null, 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    GUEST_BUYER);

            assertTrue(result.success());
            assertEquals("session-abc", server.request(0).form().get("customer_id"));
            assertEquals(List.of("TIX-GUEST-1"), result.issuedTicketCodes());
        }
    }

    // REQ: SYS-04, UC 9, UAT-30 - WSEP -1 means ticket supply failure.
    @Test
    void issueTickets_whenWsepRejects_returnsFailedIssuanceResult() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "-1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("Standing", null, 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER);

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());
            assertEquals("", result.issuanceConfirmationId());
            assertTrue(result.errorMessage().contains("Ticket issuance rejected"));
            assertEquals(1, server.requestCount());
        }
    }

    // REQ: INV-10, SYS-04, ROB-01, UC 9, UAT-30 - partial WSEP issuance rollback
    // uses cancel_ticket.
    @Test
    void issueTickets_whenSecondTicketFails_cancelsAlreadyIssuedTicket() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-FIRST");
            server.enqueue(200, "-1");
            server.enqueue(200, "1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem first = new OrderItem("Standing A", null, 1, USD_100);
            OrderItem second = new OrderItem("Standing B", null, 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(first, second),
                    MEMBER_BUYER);

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());

            assertEquals(3, server.requestCount());

            assertEquals("issue_ticket", server.request(0).form().get("action_type"));
            assertEquals("Standing A", server.request(0).form().get("zone"));

            assertEquals("issue_ticket", server.request(1).form().get("action_type"));
            assertEquals("Standing B", server.request(1).form().get("zone"));

            assertEquals("cancel_ticket", server.request(2).form().get("action_type"));
            assertEquals("TIX-FIRST", server.request(2).form().get("ticket_id"));
        }
    }

    // REQ: ROB-01, TST-17, UC 9, UAT-30 - communication failure after partial
    // success triggers best-effort cancel_ticket.
    @Test
    void issueTickets_whenCommunicationFailsAfterPartialSuccess_cancelsAlreadyIssuedTicketAndThrows() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-FIRST");
            server.enqueue(503, "ticket system down");
            server.enqueue(200, "1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem first = new OrderItem("Standing A", null, 1, USD_100);
            OrderItem second = new OrderItem("Standing B", null, 1, USD_100);

            assertThrows(WsepCommunicationException.class,
                    () -> adapter.issueTickets("EVT-1", "order-1", List.of(first, second), MEMBER_BUYER));

            assertEquals(3, server.requestCount());
            assertEquals("cancel_ticket", server.request(2).form().get("action_type"));
            assertEquals("TIX-FIRST", server.request(2).form().get("ticket_id"));
        }
    }

    // REQ: ROB-01, UC 9, UAT-30 - failed best-effort cancel_ticket does not hide
    // original issuance failure.
    @Test
    void issueTickets_whenRollbackCancelFails_stillReturnsOriginalIssuanceFailure() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-FIRST");
            server.enqueue(200, "-1");
            server.enqueue(200, "-1");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem first = new OrderItem("Standing A", null, 1, USD_100);
            OrderItem second = new OrderItem("Standing B", null, 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(first, second),
                    MEMBER_BUYER);

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());

            assertEquals(3, server.requestCount());
            assertEquals("cancel_ticket", server.request(2).form().get("action_type"));
        }
    }

    // REQ: SYS-04 - empty ticket order is rejected before external WSEP call.
    @Test
    void issueTickets_whenItemsEmpty_returnsFailureWithoutHttpCall() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(),
                    MEMBER_BUYER);

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());
            assertEquals(0, server.requestCount());
        }
    }

    // REQ: SYS-04 - buyer reference must resolve to member id or session id.
    @Test
    void issueTickets_whenBuyerIsNull_returnsFailureWithoutHttpCall() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("Standing", null, 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    null);

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());
            assertEquals(0, server.requestCount());
        }
    }

    // REQ: SYS-04, USR-07 - assigned seat IDs using dash format are converted into
    // WSEP seats JSON.
    @Test
    void issueTickets_forAssignedSeatWithDashFormat_sendsParsedSeatsJson() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-SEAT-DASH");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("VIP Balcony", "7-18", 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER);

            assertTrue(result.success());
            assertEquals(List.of("TIX-SEAT-DASH"), result.issuedTicketCodes());
            assertEquals("true", server.request(0).form().get("is_seating"));
            assertEquals("[{\"row\":7,\"seat\":18}]", server.request(0).form().get("seats"));
        }
    }

    // REQ: SYS-04, USR-07 - assigned seat IDs using row/seat key-value format are
    // converted into WSEP seats JSON.
    @Test
    void issueTickets_forAssignedSeatWithKeyValueFormat_sendsParsedSeatsJson() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-SEAT-KV");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("VIP Balcony", "row=9, seat=41", 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER);

            assertTrue(result.success());
            assertEquals(List.of("TIX-SEAT-KV"), result.issuedTicketCodes());
            assertEquals("true", server.request(0).form().get("is_seating"));
            assertEquals("[{\"row\":9,\"seat\":41}]", server.request(0).form().get("seats"));
        }
    }

    // REQ: SYS-04, USR-07 - plain assigned seat ID defaults row to 0.
    @Test
    void issueTickets_forAssignedSeatWithPlainSeatId_defaultsRowToZero() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-SEAT-PLAIN");

            TicketIssuanceHttpAdapter adapter = new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            OrderItem item = new OrderItem("VIP Balcony", "55", 1, USD_100);

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER);

            assertTrue(result.success());
            assertEquals(List.of("TIX-SEAT-PLAIN"), result.issuedTicketCodes());
            assertEquals("true", server.request(0).form().get("is_seating"));
            assertEquals("[{\"row\":0,\"seat\":55}]", server.request(0).form().get("seats"));
        }
    }
}