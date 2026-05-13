package com.eventsystem.application.appexceptions;

public class AlreadyExistsOrderException extends RuntimeException {
    public AlreadyExistsOrderException(String message) {
        super(message);
    }
    
}
