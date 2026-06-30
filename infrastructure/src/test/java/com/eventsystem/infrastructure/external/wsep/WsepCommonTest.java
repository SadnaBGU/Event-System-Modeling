package com.eventsystem.infrastructure.external.wsep;
import com.eventsystem.infrastructure.external.wsep.common.*;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsepCommonTest {

    // REQ: SYS-03, SYS-04 - exact WSEP action_type strings.
    @Test
    void actionType_returnsExactWsepStrings() {
        assertEquals("handshake", WsepAction.HANDSHAKE.actionType());
        assertEquals("pay", WsepAction.PAY.actionType());
        assertEquals("refund", WsepAction.REFUND.actionType());
        assertEquals("issue_ticket", WsepAction.ISSUE_TICKET.actionType());
        assertEquals("cancel_ticket", WsepAction.CANCEL_TICKET.actionType());
    }

    // REQ: SYS-03, SYS-04, ROB-01 - WSEP protocol response meanings.
    @Test
    void responseParser_mapsFailureResponses() {
        assertTrue(WsepResponseParser.isFailure(null));
        assertTrue(WsepResponseParser.isFailure(""));
        assertTrue(WsepResponseParser.isFailure("   "));
        assertTrue(WsepResponseParser.isFailure("-1"));
        assertTrue(WsepResponseParser.isFailure(" -1 "));

        assertFalse(WsepResponseParser.isFailure("1"));
        assertFalse(WsepResponseParser.isFailure("12345"));
        assertFalse(WsepResponseParser.isFailure("TIX-ABC"));
    }

    // REQ: SYS-03, ROB-01 - a successful pay is a numeric transaction id; -1 and any other
    // body (an unexpected response) are not, so they must not be accepted as a payment.
    @Test
    void responseParser_mapsPayTransactionId() {
        assertTrue(WsepResponseParser.isPayTransactionId("12345"));
        assertTrue(WsepResponseParser.isPayTransactionId(" 100 "));

        assertFalse(WsepResponseParser.isPayTransactionId("-1"));
        assertFalse(WsepResponseParser.isPayTransactionId("986-UNRECOGNIZED"));
        assertFalse(WsepResponseParser.isPayTransactionId("OK"));
        assertFalse(WsepResponseParser.isPayTransactionId("  "));
        assertFalse(WsepResponseParser.isPayTransactionId(null));
    }

    // REQ: SYS-03 - refund success is represented by "1".
    @Test
    void responseParser_mapsSuccessOne() {
        assertTrue(WsepResponseParser.isSuccessOne("1"));
        assertTrue(WsepResponseParser.isSuccessOne(" 1 "));

        assertFalse(WsepResponseParser.isSuccessOne("-1"));
        assertFalse(WsepResponseParser.isSuccessOne("OK"));
        assertFalse(WsepResponseParser.isSuccessOne(null));
    }

    // REQ: SYS-01, SYS-03, SYS-04, UC 1 - handshake success is represented by OK.
    @Test
    void responseParser_mapsHandshakeOk() {
        assertTrue(WsepResponseParser.isHandshakeOk("OK"));
        assertTrue(WsepResponseParser.isHandshakeOk(" ok "));
        assertTrue(WsepResponseParser.isHandshakeOk("Ok"));

        assertFalse(WsepResponseParser.isHandshakeOk("NO"));
        assertFalse(WsepResponseParser.isHandshakeOk("-1"));
        assertFalse(WsepResponseParser.isHandshakeOk(null));
    }

    //
    //Payment Details tests:
    //
        // REQ: SYS-03, UC 9, UAT-26 - payment adapter extracts all WSEP-required payment fields.
    @Test
    void fromJson_whenValid_parsesAllRequiredFields() {
        String json = """
                {
                  "card_number": "2222333344445555",
                  "month": "4",
                  "year": "2026",
                  "holder": "Israel Israelovice",
                  "cvv": "262",
                  "id": "20444444"
                }
                """;

        WsepPaymentDetails details = WsepPaymentDetails.fromJson(json);

        assertEquals("2222333344445555", details.cardNumber());
        assertEquals("4", details.month());
        assertEquals("2026", details.year());
        assertEquals("Israel Israelovice", details.holder());
        assertEquals("262", details.cvv());
        assertEquals("20444444", details.id());
    }

    // REQ: ROB-01, UC 9 - invalid payment payload fails safely before external call.
    @Test
    void fromJson_whenBlank_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> WsepPaymentDetails.fromJson(""));
    }

    // REQ: SYS-03, UC 9 - WSEP pay requires card_number, month, year, holder, cvv, id.
    @Test
    void fromJson_whenRequiredFieldMissing_throwsIllegalArgumentException() {
        String missingCvv = """
                {
                  "card_number": "2222333344445555",
                  "month": "4",
                  "year": "2026",
                  "holder": "Israel Israelovice",
                  "id": "20444444"
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> WsepPaymentDetails.fromJson(missingCvv));
    }

    // REQ: ROB-01 - malformed payment JSON fails safely before external call.
    @Test
    void fromJson_whenMalformedJson_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> WsepPaymentDetails.fromJson("{not-json"));
    }

    private static String paymentJson(String cvv) {
        return """
                {
                  "card_number": "2222333344445555",
                  "month": "4",
                  "year": "2026",
                  "holder": "Israel Israelovice",
                  "cvv": "%s",
                  "id": "20444444"
                }
                """.formatted(cvv);
    }

    // REQ: ROB-01, UC 9 - a missing CVV yields a short, CVV-specific message (not a generic failure).
    @Test
    void fromJson_whenCvvMissing_messageNamesCvv() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WsepPaymentDetails.fromJson(paymentJson("")));
        assertTrue(ex.getMessage().contains("CVV"), "message should name CVV: " + ex.getMessage());
    }

    // REQ: ROB-01, UC 9 - a non-numeric CVV is rejected with a CVV-specific message.
    @Test
    void fromJson_whenCvvNotNumeric_messageNamesCvv() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WsepPaymentDetails.fromJson(paymentJson("12a")));
        assertTrue(ex.getMessage().contains("CVV"), "message should name CVV: " + ex.getMessage());
    }

    // REQ: ROB-01, UC 9 - a wrong-length CVV is rejected with a CVV-specific message.
    @Test
    void fromJson_whenCvvWrongLength_messageNamesCvv() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WsepPaymentDetails.fromJson(paymentJson("12")));
        assertTrue(ex.getMessage().contains("CVV"), "message should name CVV: " + ex.getMessage());
    }

    // REQ: ROB-01 - a malformed JSON message stays generic and does NOT leak field-level wording.
    @Test
    void fromJson_whenMalformedJson_messageIsFormatGeneric() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WsepPaymentDetails.fromJson("{not-json"));
        assertTrue(ex.getMessage().contains("valid format"), "message: " + ex.getMessage());
    }
}