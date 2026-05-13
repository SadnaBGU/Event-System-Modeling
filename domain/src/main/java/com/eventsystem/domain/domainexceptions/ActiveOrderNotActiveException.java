package com.eventsystem.domain.domainexceptions;

public class ActiveOrderNotActiveException extends RuntimeException {
    public ActiveOrderNotActiveException(String message) {
        super(message);
    }
    
}
