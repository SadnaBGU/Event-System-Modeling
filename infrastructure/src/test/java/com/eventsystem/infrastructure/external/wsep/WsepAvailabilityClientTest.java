package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.infrastructure.external.wsep.common.WsepCommunicationException;
import com.eventsystem.infrastructure.external.wsep.common.WsepHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsepAvailabilityClientTest {

    // REQ: SYS-01, SYS-03, SYS-04, UC 1, UAT-01 - initialization can verify WSEP availability.
    @Test
    void isAvailable_whenHandshakeReturnsOk_returnsTrue() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "OK");

            WsepAvailabilityClient client =
                    new WsepAvailabilityClient(new WsepHttpClient(server.properties()));

            assertTrue(client.isAvailable());

            assertEquals(1, server.requestCount());
            assertEquals("handshake", server.request(0).form().get("action_type"));
        }
    }

    // REQ: SYS-01, ROB-01, UC 1, UAT-02 - unexpected handshake response means unavailable.
    @Test
    void isAvailable_whenHandshakeReturnsUnexpectedBody_returnsFalse() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "NO");

            WsepAvailabilityClient client =
                    new WsepAvailabilityClient(new WsepHttpClient(server.properties()));

            assertFalse(client.isAvailable());
        }
    }

    // REQ: ROB-01, TST-17, UC 1, UAT-02 - external service down is communication failure.
    @Test
    void isAvailable_whenHttpFails_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(503, "down");

            WsepAvailabilityClient client =
                    new WsepAvailabilityClient(new WsepHttpClient(server.properties()));

            assertThrows(WsepCommunicationException.class, client::isAvailable);
        }
    }
}