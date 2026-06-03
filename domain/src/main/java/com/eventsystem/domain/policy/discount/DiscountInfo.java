package com.eventsystem.domain.policy.discount;

import java.time.LocalDate;
import java.util.Objects;
import java.math.BigDecimal;

public record DiscountInfo(String discountName, BigDecimal discountPercent, LocalDate endDate) {
    
    public DiscountInfo {
        Objects.requireNonNull(discountName, "discountName must not be null");
        Objects.requireNonNull(discountPercent, "discountPercent must not be null");
    }

    public BigDecimal amountOffWithDiscount(BigDecimal baseCost) {
        Objects.requireNonNull(baseCost, "baseCost must not be null");
        if (baseCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("baseCost cannot be negative");
        }
        return (discountPercent.divide(BigDecimal.valueOf(100))).multiply(baseCost);
    }
}
