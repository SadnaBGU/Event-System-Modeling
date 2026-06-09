package com.eventsystem.infrastructure.config;

import com.eventsystem.domain.member.Notification;
import com.eventsystem.domain.member.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NotificationBroadcasterBeanTest {

    @Mock
    private SimpMessagingTemplate template;

    @Mock
    private com.eventsystem.application.security.ITokenService tokenService;

    @SuppressWarnings("null")
    @Test
    public void broadcaster_sendsToUserQueue() {
        NotificationsWebSocketConfig config = new NotificationsWebSocketConfig(tokenService);

        Notification n = Notification.create(NotificationType.PURCHASE_COMPLETED, "payload");
        config.notificationBroadcaster(template).broadcastToUser("user-1", n);

        verify(template).convertAndSendToUser(eq("user-1"), eq("/queue/notifications"), any());
    }
}
