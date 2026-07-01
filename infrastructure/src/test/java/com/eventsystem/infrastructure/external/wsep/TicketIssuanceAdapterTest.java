package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.IssuanceResult;
import com.eventsystem.application.order.TicketIssuanceItem;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TicketIssuanceAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final BuyerReference MEMBER_BUYER =
            new BuyerReference(BuyerType.MEMBER, null, "849302");

    private static final BuyerReference GUEST_BUYER =
            new BuyerReference(BuyerType.GUEST, "session-abc", null);

    // REQ: SYS-04, USR-10, UC 9, UAT-26 - general admission ticket issuance sends quantity.
    @Test
    void issueTickets_forGeneralAdmission_sendsIssueTicketWithQuantity() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-GA-1");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-ga-1",
                    "Golden Ring",
                    2,
                    null,
                    null,
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER
            );

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

    // REQ: SYS-04, USR-07, UC 9, UAT-26 - assigned seating sends real rowLabel/seatNumber, not parsed seatId.
    @Test
    void issueTickets_forAssignedSeat_sendsIssueTicketWithSeatsJson() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-SEAT-1");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-seat-1",
                    "VIP Balcony",
                    1,
                    "seat-internal-id-123",
                    "A",
                    12
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER
            );

            assertTrue(result.success());
            assertEquals("TIX-SEAT-1", result.issuanceConfirmationId());
            assertEquals(List.of("TIX-SEAT-1"), result.issuedTicketCodes());

            assertEquals(1, server.requestCount());
            assertEquals("issue_ticket", server.request(0).form().get("action_type"));
            assertEquals("849302", server.request(0).form().get("customer_id"));
            assertEquals("EVT-9923", server.request(0).form().get("event_id"));
            assertEquals("VIP Balcony", server.request(0).form().get("zone"));
            assertEquals("true", server.request(0).form().get("is_seating"));
            assertSeatsJson(server.request(0).form().get("seats"), "A", 12);
            assertFalse(server.request(0).form().containsKey("quantity"));
        }
    }

    // REQ: SYS-04, USR-07, UC 9, UAT-26 - regression test for UUID seatId bug.
    @Test
    void issueTickets_forAssignedSeatWithUuidSeatId_doesNotParseUuidAsRowAndSeat() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-SEAT-UUID");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            String uuidSeatId = "d1af8172-5598-44aa-abe9-ac4be309cb1c";

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "2b3579bb-199c-48ff-9c70-c5936d7a7255",
                    "VIP Balcony",
                    1,
                    uuidSeatId,
                    "A",
                    12
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-9923",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER
            );

            assertTrue(result.success());
            assertEquals(List.of("TIX-SEAT-UUID"), result.issuedTicketCodes());

            String seats = server.request(0).form().get("seats");

            assertSeatsJson(seats, "A", 12);
            assertFalse(seats.contains("d1af8172"));
            assertFalse(seats.contains("5598-44aa-abe9-ac4be309cb1c"));
        }
    }

    // REQ: SYS-04, UC 9 - guest buyer uses session id as WSEP customer_id.
    @Test
    void issueTickets_forGuestBuyer_usesSessionIdAsCustomerId() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-GUEST-1");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-standing",
                    "Standing",
                    1,
                    null,
                    null,
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    GUEST_BUYER
            );

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

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-standing",
                    "Standing",
                    1,
                    null,
                    null,
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER
            );

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());
            assertEquals("", result.issuanceConfirmationId());
            assertTrue(result.errorMessage().contains("Ticket issuance rejected"));
            assertEquals(1, server.requestCount());
        }
    }

    // REQ: INV-10, SYS-04, ROB-01, UC 9, UAT-30 - partial WSEP issuance rollback uses cancel_ticket.
    @Test
    void issueTickets_whenSecondTicketFails_cancelsAlreadyIssuedTicket() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-FIRST");
            server.enqueue(200, "-1");
            server.enqueue(200, "1");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem first = new TicketIssuanceItem(
                    "zone-a",
                    "Standing A",
                    1,
                    null,
                    null,
                    null
            );

            TicketIssuanceItem second = new TicketIssuanceItem(
                    "zone-b",
                    "Standing B",
                    1,
                    null,
                    null,
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(first, second),
                    MEMBER_BUYER
            );

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

    // REQ: ROB-01, TST-17, UC 9, UAT-30 - communication failure after partial success triggers best-effort cancel_ticket.
    @Test
    void issueTickets_whenCommunicationFailsAfterPartialSuccess_cancelsAlreadyIssuedTicketAndThrows() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-FIRST");
            server.enqueue(503, "ticket system down");
            server.enqueue(200, "1");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem first = new TicketIssuanceItem(
                    "zone-a",
                    "Standing A",
                    1,
                    null,
                    null,
                    null
            );

            TicketIssuanceItem second = new TicketIssuanceItem(
                    "zone-b",
                    "Standing B",
                    1,
                    null,
                    null,
                    null
            );

            assertThrows(
                    WsepCommunicationException.class,
                    () -> adapter.issueTickets("EVT-1", "order-1", List.of(first, second), MEMBER_BUYER)
            );

            assertEquals(3, server.requestCount());
            assertEquals("cancel_ticket", server.request(2).form().get("action_type"));
            assertEquals("TIX-FIRST", server.request(2).form().get("ticket_id"));
        }
    }

    // REQ: ROB-01, UC 9, UAT-30 - failed best-effort cancel_ticket does not hide original issuance failure.
    @Test
    void issueTickets_whenRollbackCancelFails_stillReturnsOriginalIssuanceFailure() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "TIX-FIRST");
            server.enqueue(200, "-1");
            server.enqueue(200, "-1");

            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem first = new TicketIssuanceItem(
                    "zone-a",
                    "Standing A",
                    1,
                    null,
                    null,
                    null
            );

            TicketIssuanceItem second = new TicketIssuanceItem(
                    "zone-b",
                    "Standing B",
                    1,
                    null,
                    null,
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(first, second),
                    MEMBER_BUYER
            );

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
            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(),
                    MEMBER_BUYER
            );

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());
            assertEquals(0, server.requestCount());
        }
    }

    // REQ: SYS-04 - buyer reference must resolve to member id or session id.
    @Test
    void issueTickets_whenBuyerIsNull_returnsFailureWithoutHttpCall() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-standing",
                    "Standing",
                    1,
                    null,
                    null,
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    null
            );

            assertFalse(result.success());
            assertTrue(result.issuedTicketCodes().isEmpty());
            assertEquals(0, server.requestCount());
        }
    }

    // REQ: SYS-04, USR-07 - assigned seating item must include rowLabel.
    @Test
    void issueTickets_forAssignedSeatWithoutRowLabel_returnsFailureWithoutHttpCall() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-seat",
                    "VIP Balcony",
                    1,
                    "seat-id-1",
                    null,
                    12
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER
            );

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("rowLabel"));
            assertEquals(0, server.requestCount());
        }
    }

    // REQ: SYS-04, USR-07 - assigned seating item must include seatNumber.
    @Test
    void issueTickets_forAssignedSeatWithoutSeatNumber_returnsFailureWithoutHttpCall() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            TicketIssuanceHttpAdapter adapter =
                    new TicketIssuanceHttpAdapter(new WsepHttpClient(server.properties()));

            TicketIssuanceItem item = new TicketIssuanceItem(
                    "zone-seat",
                    "VIP Balcony",
                    1,
                    "seat-id-1",
                    "A",
                    null
            );

            IssuanceResult result = adapter.issueTickets(
                    "EVT-1",
                    "order-1",
                    List.of(item),
                    MEMBER_BUYER
            );

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("seatNumber"));
            assertEquals(0, server.requestCount());
        }
    }

    private static void assertSeatsJson(String seatsJson, String expectedRow, int expectedSeat) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(seatsJson);

        assertTrue(root.isArray(), "seats must be a JSON array");
        assertEquals(1, root.size(), "seats must contain exactly one seat");

        JsonNode seat = root.get(0);

        assertEquals(expectedRow, seat.get("row").asText());
        assertEquals(expectedSeat, seat.get("seat").asInt());
    }
}