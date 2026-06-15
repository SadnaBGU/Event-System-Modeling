package com.eventsystem.infrastructure.persistence.entities;





import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Embeddable;

@Embeddable
public class SuspensionEmbeddable {

    private Instant suspendedAt;
    private Long durationSeconds; // null = permanent
    private String reason;

    protected SuspensionEmbeddable() {}

    public SuspensionEmbeddable(Instant suspendedAt, Duration duration, String reason) {
        this.suspendedAt = suspendedAt;
        this.durationSeconds = duration == null ? null : duration.getSeconds();
        this.reason = reason;
    }

    public Duration getDuration() {
        return durationSeconds == null ? null : Duration.ofSeconds(durationSeconds);
    }

    // getters
}