package com.eventsystem.application.appexceptions;

public class QueueAdmissionRequiredException extends RuntimeException {
    public QueueAdmissionRequiredException(String eventId) {
        super("Virtual queue admission required for event: " + eventId);
    }
}