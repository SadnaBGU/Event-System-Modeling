package com.eventsystem.application.member;

import com.eventsystem.domain.member.Notification;

public interface NotificationBroadcaster {
    void broadcastToUser(String memberId, Notification notification);
}
