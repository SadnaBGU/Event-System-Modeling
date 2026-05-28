package com.eventsystem.infrastructure.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.eventsystem.application.member.NotificationBroadcaster;
import com.eventsystem.application.member.NotificationService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import com.eventsystem.infrastructure.api.notifications.NotificationDto;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=servlet")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class NotificationsStompIntegrationTest {

    @MockBean
    NotificationService notificationService;

    @MockBean
    ITokenService tokenService;

    @Autowired
    NotificationBroadcaster broadcaster;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext ctx;

    @SuppressWarnings("deprecation")
    @Test
    public void connect_and_receive_broadcast() throws Exception {
        // start client
        Transport webSocketTransport = new WebSocketTransport(new StandardWebSocketClient());
        @SuppressWarnings("null")
        SockJsClient sockJsClient = new SockJsClient(List.of(webSocketTransport));
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<StompSession> sessionFuture = new CompletableFuture<>();

        int port = ((WebServerApplicationContext) ctx).getWebServer().getPort();
        String url = "ws://localhost:" + port + "/api/notifications/stream";
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer test-token");

        // token service mock: verifyToken called with token part after "Bearer "
        Mockito.when(tokenService.verifyToken("test-token"))
            .thenReturn(new ITokenService.TokenClaims(new MemberId("member-xyz"), Instant.now(), Instant.now().plusSeconds(3600)));

        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();

        stompClient.connect(url, wsHeaders, connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(@SuppressWarnings("null") StompSession stompSession, @SuppressWarnings("null") StompHeaders connectedHeaders) {
                sessionFuture.complete(stompSession);
            }
        }).get(5, TimeUnit.SECONDS);

        StompSession stomp = sessionFuture.get(5, TimeUnit.SECONDS);
        assertThat(stomp).isNotNull();

        CompletableFuture<NotificationDto> received = new CompletableFuture<>();

        stomp.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @SuppressWarnings("null")
            @Override
            public Type getPayloadType(@SuppressWarnings("null") StompHeaders headers) {
                return NotificationDto.class;
            }

            @Override
            public void handleFrame(@SuppressWarnings("null") StompHeaders headers, @SuppressWarnings("null") Object payload) {
                received.complete((NotificationDto) payload);
            }
        });

        // Give the broker a short moment to register the subscription.
        // Without receipts, subscribe() may return before the server-side subscription is fully active in CI.
        Thread.sleep(500);

        // Broadcast with a small retry loop to avoid CI timing races.
        Notification notification = Notification.create(NotificationType.PURCHASE_COMPLETED, "hello");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!received.isDone() && System.nanoTime() < deadline) {
            broadcaster.broadcastToUser("member-xyz", notification);
            Thread.sleep(200);
        }

        NotificationDto dto = received.get(2, TimeUnit.SECONDS);
        assertThat(dto).isNotNull();
        assertThat(dto.type()).isEqualTo("PURCHASE_COMPLETED");

        verify(notificationService).clientConnected(org.mockito.ArgumentMatchers.eq("member-xyz"), org.mockito.ArgumentMatchers.anyString());
        stomp.disconnect();
    }
}
