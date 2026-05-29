package com.eventsystem.domain.policy;

import java.util.Objects;
import java.util.UUID;

public record PurchasePolicyId(String value) {

    public PurchasePolicyId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static PurchasePolicyId random() {
        return new PurchasePolicyId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}