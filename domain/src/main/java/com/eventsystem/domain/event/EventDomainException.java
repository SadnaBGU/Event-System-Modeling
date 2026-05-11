package com.eventsystem.domain.event;

public class EventDomainException extends RuntimeException {
    public EventDomainException(String message) {
        super(message);
    }
}