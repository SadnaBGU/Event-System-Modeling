package com.eventsystem.domain.company;

import java.util.Objects;
import java.util.UUID;

public record CompanyId(String value) {

    public CompanyId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static CompanyId random() {
        return new CompanyId(UUID.randomUUID().toString());
    }
}
