package com.eventsystem.domain.member;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.EnumType;

/**
 * Entity — a single notification queued in a member's inbox.
 * Identity is the {@code notificationId}. Mutable only via {@link #markDelivered()}.
 */

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private String notificationId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String content;
    private Instant createdAt;
    private boolean delivered;

    /**
     * Optional id of the entity this notification refers to (e.g. the event id for a
     * queue-turn notification). Transient: it is only carried on the real-time push so
     * the client can act on it (e.g. redirect to that event); it is not persisted.
     */
    @Transient
    private String relatedEntityId;

    protected Notification() {}

    public Notification(String notificationId,
                        NotificationType type,
                        String content,
                        Instant createdAt) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.delivered = false;
    }

    public static Notification create(NotificationType type, String content) {
        return new Notification(UUID.randomUUID().toString(), type, content, Instant.now());
    }

    public static Notification create(NotificationType type, String content, String relatedEntityId) {
        Notification n = new Notification(UUID.randomUUID().toString(), type, content, Instant.now());
        n.relatedEntityId = relatedEntityId;
        return n;
    }

    void markDelivered() {
        this.delivered = true;
    }

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

    public String getRelatedEntityId() {
        return relatedEntityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification other)) return false;
        return notificationId.equals(other.notificationId);
    }

    @Override
    public int hashCode() {
        return notificationId.hashCode();
    }
}
