package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * DiscountPolicy Domain Object
 * Defines discount rules for an event (percentage, fixed amount, bulk offers, etc.)
 */
public class DiscountPolicy implements Serializable {
    private final EventId eventId;
    private final DiscountType type;
    private final BigDecimal discountValue;
    private final LocalDate validUntil;
    private final String description;

    public DiscountPolicy(EventId eventId, DiscountType type, BigDecimal discountValue, LocalDate validUntil, String description) {
        if (validUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Discount expiry date cannot be in the past");
        }
        if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount value must be positive");
        }
        this.eventId = Objects.requireNonNull(eventId);
        this.type = Objects.requireNonNull(type);
        this.discountValue = discountValue;
        this.validUntil = validUntil;
        this.description = Objects.requireNonNull(description);
    }

    public EventId eventId() {
        return eventId;
    }

    public DiscountType type() {
        return type;
    }

    public BigDecimal discountValue() {
        return discountValue;
    }

    public LocalDate validUntil() {
        return validUntil;
    }

    public String description() {
        return description;
    }

    public boolean isExpired() {
        return LocalDate.now().isAfter(validUntil);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiscountPolicy that = (DiscountPolicy) o;
        return Objects.equals(eventId, that.eventId) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, type);
    }

    @Override
    public String toString() {
        return "DiscountPolicy{" +
                "eventId=" + eventId +
                ", type=" + type +
                ", discountValue=" + discountValue +
                ", validUntil=" + validUntil +
                '}';
    }
}
