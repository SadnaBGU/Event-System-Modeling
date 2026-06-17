package com.eventsystem.domain.policy.discount;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public record DiscountPolicyId(String value) implements Serializable {

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