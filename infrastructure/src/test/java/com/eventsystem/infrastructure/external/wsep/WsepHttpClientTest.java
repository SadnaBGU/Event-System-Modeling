package com.eventsystem.infrastructure.external.wsep;

import com.eventsystem.infrastructure.config.WsepProperties;
import com.eventsystem.infrastructure.external.wsep.common.*;
import org.junit.jupiter.api.Test;

import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.*;

class WsepHttpClientTest {

    // REQ: SYS-03, SYS-04, ROB-02, UC 1/9 - WSEP requests use mocked local HTTP
    // server, not real external service.
    @Test
    void post_sendsFormUrlEncodedPostAndReturnsTrimmedBody() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "  OK  ");

            WsepHttpClient client = new WsepHttpClient(server.properties());

            String response = client.post(Map.of(
                    "action_type", WsepAction.HANDSHAKE.actionType(),
                    "zone", "VIP Balcony"));

            assertEquals("OK", response);
            assertEquals(1, server.requestCount());
            assertEquals("POST", server.request(0).method());
            assertTrue(server.request(0).contentType().contains("application/x-www-form-urlencoded"));
            assertEquals("handshake", server.request(0).form().get("action_type"));
            assertEquals("VIP Balcony", server.request(0).form().get("zone"));
        }
    }

    // REQ: ROB-01, TST-17, UC 1, UAT-02 - external service HTTP failure becomes
    // communication exception.
    @Test
    void post_whenHttpStatusIsNotSuccessful_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(503, "Service unavailable");

            WsepHttpClient client = new WsepHttpClient(server.properties());

            assertThrows(WsepCommunicationException.class,
                    () -> client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType())));
        }
    }

    // REQ: ROB-01, TST-17 - incompatible/empty external response is treated as
    // communication failure.
    @Test
    void post_whenResponseBodyIsEmpty_throwsCommunicationException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            server.enqueue(200, "   ");

            WsepHttpClient client = new WsepHttpClient(server.properties());

            assertThrows(WsepCommunicationException.class,
                    () -> client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType())));
        }
    }

    // REQ: SYS-03, SYS-04 - every WSEP call must include action_type.
    @Test
    void post_whenActionTypeMissing_throwsIllegalArgumentException() throws Exception {
        try (WsepTestServer server = new WsepTestServer()) {
            WsepHttpClient client = new WsepHttpClient(server.properties());

            assertThrows(IllegalArgumentException.class, () -> client.post(Map.of("amount", "1000")));

            assertEquals(0, server.requestCount());
        }
    }

    // REQ: ROB-01, TST-17, UC 1/9 - low-level IO failure is wrapped as WSEP
    // communication failure.
    @Test
    void post_whenHttpClientThrowsIOException_wrapsInCommunicationException() throws Exception {
        WsepHttpClient client = new WsepHttpClient(testProperties("http://localhost/wsep"));
        IOException ioException = new IOException("network down");
        replaceHttpClient(client, ThrowingHttpClient.throwing(ioException));

        WsepCommunicationException ex = assertThrows(WsepCommunicationException.class,
                () -> client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType())));

        assertTrue(ex.getMessage().contains("Could not reach WSEP service"));
        assertSame(ioException, ex.getCause());
    }

    // REQ: ROB-01, TST-17 - interrupted WSEP call restores interrupt flag and wraps
    // failure.
    @Test
    void post_whenHttpClientIsInterrupted_restoresInterruptFlagAndWrapsInCommunicationException() throws Exception {
        WsepHttpClient client = new WsepHttpClient(testProperties("http://localhost/wsep"));
        InterruptedException interruptedException = new InterruptedException("interrupted");
        replaceHttpClient(client, ThrowingHttpClient.throwing(interruptedException));

        try {
            WsepCommunicationException ex = assertThrows(WsepCommunicationException.class,
                    () -> client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType())));

            assertTrue(ex.getMessage().contains("Interrupted while calling WSEP service"));
            assertSame(interruptedException, ex.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear interrupt flag so this test does not affect following tests.
            Thread.interrupted();
        }
    }

    // REQ: ROB-01, TST-17 - invalid WSEP base URL is exposed as
    // communication/configuration failure.
    @Test
    void post_whenBaseUrlIsInvalid_wrapsInvalidUrlAsCommunicationException() {
        WsepHttpClient client = new WsepHttpClient(testProperties("http://[bad-url"));

        WsepCommunicationException ex = assertThrows(WsepCommunicationException.class,
                () -> client.post(Map.of("action_type", WsepAction.HANDSHAKE.actionType())));

        assertTrue(ex.getMessage().contains("Invalid WSEP URL"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    // helpers:

    private static WsepProperties testProperties(String baseUrl) {
        WsepProperties properties = new WsepProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static void replaceHttpClient(WsepHttpClient client, HttpClient replacement) throws Exception {
        Field field = WsepHttpClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, replacement);
    }

    private static final class ThrowingHttpClient extends HttpClient {

        private final IOException ioException;
        private final InterruptedException interruptedException;

        private ThrowingHttpClient(IOException ioException, InterruptedException interruptedException) {
            this.ioException = ioException;
            this.interruptedException = interruptedException;
        }

        static ThrowingHttpClient throwing(IOException exception) {
            return new ThrowingHttpClient(exception, null);
        }

        static ThrowingHttpClient throwing(InterruptedException exception) {
            return new ThrowingHttpClient(null, exception);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            if (ioException != null) {
                throw ioException;
            }
            throw interruptedException;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("sendAsync is not used by this test");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("sendAsync is not used by this test");
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException("WebSocket is not used by this test");
        }
    }
}