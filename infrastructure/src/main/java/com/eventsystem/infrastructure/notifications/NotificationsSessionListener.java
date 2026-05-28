package com.eventsystem.infrastructure.notifications;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.eventsystem.application.member.NotificationService;

@Component
public class NotificationsSessionListener {

    private final NotificationService notificationService;

    public NotificationsSessionListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = sha.getUser();
        if (user != null) {
            notificationService.clientConnected(user.getName(), sha.getSessionId());
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = sha.getUser();
        if (user != null) {
            notificationService.clientDisconnected(user.getName(), sha.getSessionId());
        }
    }
}
