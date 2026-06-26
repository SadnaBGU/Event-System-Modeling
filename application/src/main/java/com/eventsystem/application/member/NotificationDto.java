package com.eventsystem.application.member;

public record NotificationDto(
        String notificationId,
        String type,
        String content,
        String createdAt,
        boolean delivered
) {
        public NotificationDto convertAll(NotificationDto n) {
            return new NotificationDto(n.notificationId(), n.type(), n.content(), n.createdAt(), n.delivered());
        }
}
