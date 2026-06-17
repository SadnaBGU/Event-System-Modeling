package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.application.order.PaymentResult;
import com.eventsystem.application.order.RefundResult;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayHttpAdapterTest {

    private static final Money USD_1000 = Money.of(new BigDecimal("1000"), "USD");
    private static final BuyerReference BUYER = new BuyerReference(BuyerType.MEMBER, null, "849302");

    private static final String VALID_PAYMENT_JSON = """
            {
              "card_number": "2222333344445555",
              "month": "4",
              "year": "2026",
              "holder": "Israel Israelovice",
              "cvv": "262",
              "id": "20444444"
            }
            """;

    // REQ: SYS-03, USR-10, UC 9, UAT-26 - successful checkout payment sends WSEP pay params.
    @Test
    void charge_whenWsepApproves_sendsPayParamsAndReturnsTransactionId() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "12345");

            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            PaymentResult result = adapter.charge("order-1", USD_1000, BUYER, VALID_PAYMENT_JSON);

            assertTrue(result.success());
            assertEquals("12345", result.transactionId());
            assertNull(result.errorMessage());

            assertEquals(1, server.requestCount());
            assertEquals("pay", server.request(0).form().get("action_type"));
            assertEquals("1000", server.request(0).form().get("amount"));
            assertEquals("USD", server.request(0).form().get("currency"));
            assertEquals("2222333344445555", server.request(0).form().get("card_number"));
            assertEquals("4", server.request(0).form().get("month"));
            assertEquals("2026", server.request(0).form().get("year"));
            assertEquals("Israel Israelovice", server.request(0).form().get("holder"));
            assertEquals("262", server.request(0).form().get("cvv"));
            assertEquals("20444444", server.request(0).form().get("id"));
        }
    }

    // REQ: SYS-03, UC 9, UAT-28 - WSEP -1 means payment declined, not infrastructure crash.
    @Test
    void charge_whenWsepDeclines_returnsFailedPaymentResult() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "-1");

            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            PaymentResult result = adapter.charge("order-1", USD_1000, BUYER, VALID_PAYMENT_JSON);

            assertFalse(result.success());
            assertNull(result.transactionId());
            assertTrue(result.errorMessage().contains("Payment declined"));
        }
    }

    // REQ: ROB-01, TST-17, UC 9, UAT-29 - payment gateway timeout/down is propagated as communication failure.
    @Test
    void charge_whenWsepHttpFails_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(503, "down");

            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            assertThrows(WsepCommunicationException.class, () ->
                    adapter.charge("order-1", USD_1000, BUYER, VALID_PAYMENT_JSON)
            );
        }
    }

    // REQ: ROB-01, UC 9 - invalid payment details fail safely and no WSEP call is sent.
    @Test
    void charge_whenPaymentDetailsInvalid_returnsFailureWithoutHttpCall() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            PaymentResult result = adapter.charge("order-1", USD_1000, BUYER, "{bad-json");

            assertFalse(result.success());
            assertEquals(0, server.requestCount());
        }
    }

    // REQ: SYS-03, INV-10, UC 9, UAT-30 - refund sends WSEP refund action with transaction_id.
    @Test
    void refund_whenWsepApproves_sendsRefundParamsAndReturnsSuccess() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "1");

            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            RefundResult result = adapter.refund("12345", USD_1000, "ticket issuance failed");

            assertTrue(result.success());
            assertNull(result.errorMessage());

            assertEquals(1, server.requestCount());
            assertEquals("refund", server.request(0).form().get("action_type"));
            assertEquals("12345", server.request(0).form().get("transaction_id"));
            assertFalse(server.request(0).form().containsKey("amount"));
            assertFalse(server.request(0).form().containsKey("reason"));
        }
    }

    // REQ: SYS-03, ROB-01, UC 9, UAT-30 - rejected refund is returned as failed compensation result.
    @Test
    void refund_whenWsepRejects_returnsFailedRefundResult() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "-1");

            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            RefundResult result = adapter.refund("12345", USD_1000, "ticket issuance failed");

            assertFalse(result.success());
            assertTrue(result.errorMessage().contains("Refund rejected"));
        }
    }

    // REQ: ROB-01, TST-17, UC 9, UAT-30 - refund communication failure is propagated.
    @Test
    void refund_whenWsepHttpFails_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(503, "down");

            PaymentGatewayHttpAdapter adapter =
                    new PaymentGatewayHttpAdapter(new WsepHttpClient(server.properties()));

            assertThrows(WsepCommunicationException.class, () ->
                    adapter.refund("12345", USD_1000, "ticket issuance failed")
            );
        }
    }
}