package com.eventsystem.application.member;

import org.springframework.lang.NonNull;

import com.eventsystem.domain.member.Notification;

public interface NotificationBroadcaster {
    void broadcastToUser(@NonNull String memberId, Notification notification);
}
