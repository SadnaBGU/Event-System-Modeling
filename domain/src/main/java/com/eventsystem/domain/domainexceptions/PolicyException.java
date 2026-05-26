package com.eventsystem.domain.domainexceptions;

public class PolicyException extends RuntimeException {
    public PolicyException(String message) {
        super(message);
    }
}