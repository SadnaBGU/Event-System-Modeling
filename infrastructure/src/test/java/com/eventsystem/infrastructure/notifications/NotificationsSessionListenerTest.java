package com.eventsystem.infrastructure.notifications;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import org.springframework.messaging.simp.stomp.StompCommand;

import com.eventsystem.application.member.NotificationService;

public class NotificationsSessionListenerTest {

    @Test
    public void connected_should_notify_notificationService() {
        NotificationService mockService = Mockito.mock(NotificationService.class);
        NotificationsSessionListener listener = new NotificationsSessionListener(mockService);

        StompHeaderAccessor sha = StompHeaderAccessor.create(StompCommand.CONNECT);
        sha.setSessionId("sess-1");
        sha.setUser((java.security.Principal) () -> "member-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], sha.getMessageHeaders());

        SessionConnectedEvent ev = new SessionConnectedEvent(this, message);
        listener.handleSessionConnected(ev);

        verify(mockService, times(1)).clientConnected("member-123", "sess-1");
    }

    @Test
    public void disconnected_should_notify_notificationService() {
        NotificationService mockService = Mockito.mock(NotificationService.class);
        NotificationsSessionListener listener = new NotificationsSessionListener(mockService);

        StompHeaderAccessor sha = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        sha.setSessionId("sess-2");
        sha.setUser((java.security.Principal) () -> "member-456");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], sha.getMessageHeaders());

        SessionDisconnectEvent ev = Mockito.mock(SessionDisconnectEvent.class);
        Mockito.when(ev.getMessage()).thenReturn(message);
        listener.handleSessionDisconnected(ev);

        verify(mockService, times(1)).clientDisconnected("member-456", "sess-2");
    }
}
