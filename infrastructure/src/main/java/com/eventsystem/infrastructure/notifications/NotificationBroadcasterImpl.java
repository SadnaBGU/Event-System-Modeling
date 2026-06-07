package com.eventsystem.infrastructure.notifications;

import com.eventsystem.application.member.NotificationBroadcaster;
import com.eventsystem.domain.member.Notification;
import com.eventsystem.infrastructure.api.notifications.NotificationDto;

import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.ZoneOffset;

public class NotificationBroadcasterImpl implements NotificationBroadcaster {

    private final SimpMessagingTemplate template;

    public NotificationBroadcasterImpl(SimpMessagingTemplate template) {
        this.template = template;
    }

    @Override
    public void broadcastToUser(@NonNull String memberId, Notification notification) {
        NotificationDto dto = new NotificationDto(
                notification.getNotificationId(),
                notification.getType().name(),
                notification.getContent(),
                notification.getCreatedAt().atOffset(ZoneOffset.UTC).toString(),
                notification.isDelivered());
        template.convertAndSendToUser(memberId, "/queue/notifications", dto);
    }
}