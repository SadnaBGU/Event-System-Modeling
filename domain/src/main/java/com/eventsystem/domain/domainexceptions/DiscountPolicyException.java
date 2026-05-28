package com.eventsystem.domain.domainexceptions;

public class DiscountPolicyException extends RuntimeException {
    public DiscountPolicyException(String message) {
        super(message);
    }
}