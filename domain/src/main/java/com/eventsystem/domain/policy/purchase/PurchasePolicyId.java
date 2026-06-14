package com.eventsystem.domain.policy.purchase;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public record PurchasePolicyId(String value) implements Serializable {

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