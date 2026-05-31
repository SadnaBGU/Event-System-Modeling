package com.eventsystem.application.admin;

import java.time.Instant;

/**
 * Read-only view of a member suspension for UC II.6.9.
 */
public record SuspensionDto(
        String memberId,
        String username,
        Instant suspendedAt,
        String duration,
        Instant endsAt
) {
    /** {@code duration} is "PERMANENT" when no time limit applies; {@code endsAt} is null. */
}
