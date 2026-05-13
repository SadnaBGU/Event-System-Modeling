package com.eventsystem.domain.domainexceptions;

public class ActiveOrderHasExpiredException extends RuntimeException {
    public ActiveOrderHasExpiredException(String message) {
        super(message);
    }
    
}
