package com.eventsystem.domain.policy;

/**
 * DiscountType Enum
 * Defines types of discounts available in the system
 */
public enum DiscountType {
    VISIBLE_PERCENTAGE("Visible discount - percentage off"),
    VISIBLE_FIXED("Visible discount - fixed amount off"),
    CONDITIONAL_BULK("Conditional discount - buy X get Y free"),
    COUPON_CODE("Coupon code - hidden discount"),
    EARLY_BIRD("Early bird discount - time-based");

    private final String description;

    DiscountType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
