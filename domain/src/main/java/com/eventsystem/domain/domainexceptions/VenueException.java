package com.eventsystem.domain.domainexceptions;

public class VenueException extends RuntimeException {
    public VenueException(String message) {
        super(message);
    }

    public VenueException(String message, Throwable cause) {
        super(message, cause);
    }
}
