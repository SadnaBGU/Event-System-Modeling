package com.eventsystem.domain.domainexceptions;

public class EventDomainException extends RuntimeException {
    public EventDomainException(String message) {
        super(message);
    }
}