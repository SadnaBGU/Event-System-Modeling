package com.eventsystem.domain.domainexceptions;

public class PurchasePolicyException extends RuntimeException {
    public PurchasePolicyException(String message) {
        super(message);
    }
}