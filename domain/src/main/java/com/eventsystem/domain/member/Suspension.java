package com.eventsystem.domain.member;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Embeddable;

/**
 * Immutable record of a member suspension.
 * {@code duration} is null for permanent suspensions.
 *
 * NOTE: component order (duration, reason, suspendedAt) is intentionally alphabetical to match
 * the order Hibernate uses when hydrating an {@code @Embeddable} record from its columns. Keeping
 * these aligned avoids a record-instantiation "argument type mismatch" on load.
 */

@Embeddable
public record Suspension(Duration duration, String reason, Instant suspendedAt) {

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
        return !isPermanent() && ! now.isBefore(endsAt());
    }
}
