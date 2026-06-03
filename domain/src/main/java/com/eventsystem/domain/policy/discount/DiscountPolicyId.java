package com.eventsystem.domain.policy.discount;

import java.util.Objects;
import java.util.UUID;

public record DiscountPolicyId(String value) {

    public DiscountPolicyId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static DiscountPolicyId random() {
        return new DiscountPolicyId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}