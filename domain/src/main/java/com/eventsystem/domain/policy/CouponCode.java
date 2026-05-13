package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * CouponCode Domain Object
 * Represents a single-use or limited-use discount coupon
 */
public class CouponCode implements Serializable {
    private final String code;
    private final EventId eventId;
    private final BigDecimal discountAmount;
    private int remainingUses;
    private final LocalDate expiryDate;

    public CouponCode(String code, EventId eventId, BigDecimal discountAmount, int remainingUses, LocalDate expiryDate) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code cannot be blank");
        }
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Discount amount must be positive");
        }
        if (remainingUses < 0) {
            throw new IllegalArgumentException("Remaining uses cannot be negative");
        }
        if (expiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Coupon expiry date cannot be in the past");
        }
        
        this.code = code;
        this.eventId = Objects.requireNonNull(eventId);
        this.discountAmount = discountAmount;
        this.remainingUses = remainingUses;
        this.expiryDate = expiryDate;
    }

    public String code() {
        return code;
    }

    public EventId eventId() {
        return eventId;
    }

    public BigDecimal discountAmount() {
        return discountAmount;
    }

    public int remainingUses() {
        return remainingUses;
    }

    public LocalDate expiryDate() {
        return expiryDate;
    }

    public boolean isValid() {
        return !isExpired() && remainingUses > 0;
    }

    public boolean isExpired() {
        return LocalDate.now().isAfter(expiryDate);
    }

    public boolean hasRemainingUses() {
        return remainingUses > 0;
    }

    public void consumeUse() {
        if (remainingUses <= 0) {
            throw new IllegalStateException("Coupon has no remaining uses");
        }
        this.remainingUses--;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CouponCode that = (CouponCode) o;
        return Objects.equals(code, that.code) &&
                Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, eventId);
    }

    @Override
    public String toString() {
        return "CouponCode{" +
                "code='" + code + '\'' +
                ", eventId=" + eventId +
                ", remainingUses=" + remainingUses +
                ", expiryDate=" + expiryDate +
                '}';
    }
}
