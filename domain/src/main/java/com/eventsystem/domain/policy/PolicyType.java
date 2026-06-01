package com.eventsystem.domain.policy;

public enum PolicyType {
    ALWAYS_TRUE,
    NEVER_ALLOW,

    MIN_TICKETS,
    MAX_TICKETS,
    MIN_AGE,

    AFTER_DATE,
    UNTIL_DATE,

    CODE,

    NO_SINGLE_EMPTY_SEAT,

    AND,
    OR,
    ZONE_SPECIFIC_0_PASS,
    ZONE_SPECIFIC_0_FAIL,

    UNKNOWN;

    public boolean isTicketQuantityRule() {
        return this == MIN_TICKETS || this == MAX_TICKETS;
    }

    public boolean isCompositeRule() {
        return this == AND || this == OR || this == ZONE_SPECIFIC_0_PASS || this == ZONE_SPECIFIC_0_FAIL;
    }

    public boolean isCouponRule() {
        return this == CODE;
    }

    public boolean isDateRule() {
        return this == AFTER_DATE || this == UNTIL_DATE;
    }
}