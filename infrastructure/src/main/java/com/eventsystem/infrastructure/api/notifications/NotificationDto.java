package com.eventsystem.infrastructure.api.notifications;

public record NotificationDto(
        String notificationId,
        String type,
        String content,
        String createdAt,
        boolean delivered
) {}
