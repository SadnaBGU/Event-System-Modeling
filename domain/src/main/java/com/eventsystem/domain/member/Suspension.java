package com.eventsystem.domain.member;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a member suspension.
 * {@code duration} is null for permanent suspensions.
 */
public record Suspension(Instant suspendedAt, Duration duration, String reason) {

    public Suspension {
        Objects.requireNonNull(suspendedAt, "suspendedAt must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (duration != null && (duration.isNegative() || duration.isZero())) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    public boolean isPermanent() {
        return duration == null;
    }

    /** Returns the instant the suspension ends, or {@code null} if permanent. */
    public Instant endsAt() {
        return isPermanent() ? null : suspendedAt.plus(duration);
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !isPermanent() && now.isAfter(endsAt());
    }
}
