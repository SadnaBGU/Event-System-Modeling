package com.eventsystem.infrastructure.persistence.entities;




import java.time.Instant;

import com.eventsystem.domain.member.NotificationType;

import jakarta.persistence.*;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private String notificationId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String content;

    private Instant createdAt;

    private boolean delivered;

    public NotificationEntity() {}

    // getters/setters


    // ---------------- GETTERS ----------------

    public String getNotificationId() {
        return notificationId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDelivered() {
        return delivered;
    }

    // ---------------- SETTERS ----------------

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }
}

