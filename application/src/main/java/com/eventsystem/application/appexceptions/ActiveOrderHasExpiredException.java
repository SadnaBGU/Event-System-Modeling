package com.eventsystem.application.appexceptions;

public class ActiveOrderHasExpiredException extends RuntimeException {
    public ActiveOrderHasExpiredException(String message) {
        super(message);
    }
    
}
