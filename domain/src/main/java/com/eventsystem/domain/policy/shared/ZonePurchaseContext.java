package com.eventsystem.domain.policy.shared;

import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;

import java.util.Objects;

public record ZonePurchaseContext(
        ZoneId zoneId,
        int quantity,
        Money subtotal
) {

    public ZonePurchaseContext {
        Objects.requireNonNull(zoneId, "zoneId must not be null");
        Objects.requireNonNull(subtotal, "subtotal must not be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}