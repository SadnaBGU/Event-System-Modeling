package com.eventsystem.domain.lottery;

import com.eventsystem.domain.member.MemberId;

import java.time.Instant;
import java.util.Objects;

/** A lottery winner with a time-limited permission code. */
public record LotteryWinner(MemberId memberId, String permissionCode, Instant codeExpiry) {

    public LotteryWinner {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(permissionCode, "permissionCode must not be null");
        Objects.requireNonNull(codeExpiry, "codeExpiry must not be null");
        if (permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(codeExpiry);
    }
}
