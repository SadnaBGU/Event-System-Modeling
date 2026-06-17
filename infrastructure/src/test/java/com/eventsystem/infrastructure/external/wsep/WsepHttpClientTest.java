package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.infrastructure.external.wsep.common.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WsepHttpClientTest {

    // REQ: SYS-03, SYS-04, ROB-02, UC 1/9 - WSEP requests use mocked local HTTP server, not real external service.
    @Test
    void post_sendsFormUrlEncodedPostAndReturnsTrimmedBody() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "  OK  ");

            WsepHttpClient client = new WsepHttpClient(server.properties());

            String response = client.post(Map.of(
                    "action_type", WsepAction.HANDSHAKE.actionType(),
                    "zone", "VIP Balcony"
            ));

            assertEquals("OK", response);
            assertEquals(1, server.requestCount());
            assertEquals("POST", server.request(0).method());
            assertTrue(server.request(0).contentType().contains("application/x-www-form-urlencoded"));
            assertEquals("handshake", server.request(0).form().get("action_type"));
            assertEquals("VIP Balcony", server.request(0).form().get("zone"));
        }
    }

    // REQ: ROB-01, TST-17, UC 1, UAT-02 - external service HTTP failure becomes communication exception.
    @Test
    void post_whenHttpStatusIsNotSuccessful_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(503, "Service unavailable");

            WsepHttpClient client = new WsepHttpClient(server.properties());

            assertThrows(WsepCommunicationException.class, () ->
                    client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType()))
            );
        }
    }

    // REQ: ROB-01, TST-17 - incompatible/empty external response is treated as communication failure.
    @Test
    void post_whenResponseBodyIsEmpty_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "   ");

            WsepHttpClient client = new WsepHttpClient(server.properties());

            assertThrows(WsepCommunicationException.class, () ->
                    client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType()))
            );
        }
    }

    // REQ: SYS-03, SYS-04 - every WSEP call must include action_type.
    @Test
    void post_whenActionTypeMissing_throwsIllegalArgumentException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            WsepHttpClient client = new WsepHttpClient(server.properties());

            assertThrows(IllegalArgumentException.class, () ->
                    client.post(Map.of("amount", "1000"))
            );

            assertEquals(0, server.requestCount());
        }
    }
}