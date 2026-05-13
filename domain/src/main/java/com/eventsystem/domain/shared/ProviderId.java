package com.eventsystem.domain.shared;

import java.util.Objects;

/**
 * Value Object identifying an external payment or ticket-issuance provider.
 */
public record ProviderId(String value) {

    public ProviderId {
        Objects.requireNonNull(value, "ProviderId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProviderId value must not be blank");
        }
    }
}
