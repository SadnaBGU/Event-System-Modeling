package com.eventsystem.application.appexceptions;

public class OrderViolatesPolicyException extends RuntimeException {
    public OrderViolatesPolicyException(String message) {
        super(message);
    }
    
}
