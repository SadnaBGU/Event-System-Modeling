package com.eventsystem.domain.member;

import java.util.Objects;
import java.util.UUID;

public record MemberId(String value) {

    public MemberId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static MemberId random() {
        return new MemberId(UUID.randomUUID().toString());
    }
}
