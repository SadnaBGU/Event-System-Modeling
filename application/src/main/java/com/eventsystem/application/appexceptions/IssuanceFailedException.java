package com.eventsystem.application.appexceptions;

public class IssuanceFailedException extends RuntimeException {
    public IssuanceFailedException(String message) {
        super(message);
    }
    
}
