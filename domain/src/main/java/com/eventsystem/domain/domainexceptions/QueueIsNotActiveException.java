package com.eventsystem.domain.domainexceptions;

public class QueueIsNotActiveException extends RuntimeException {
    public QueueIsNotActiveException(String message) {
        super(message);
    }
    
}
