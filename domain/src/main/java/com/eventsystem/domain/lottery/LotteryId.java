package com.eventsystem.domain.lottery;

import java.util.Objects;
import java.util.UUID;

/** Identifier for a Lottery. */
public record LotteryId(String value) {

    public LotteryId {
        Objects.requireNonNull(value, "LotteryId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("LotteryId value must not be blank");
        }
    }

    public static LotteryId generate() {
        return new LotteryId(UUID.randomUUID().toString());
    }
}
