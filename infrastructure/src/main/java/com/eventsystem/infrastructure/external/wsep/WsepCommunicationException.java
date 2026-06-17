package com.eventsystem.infrastructure.external.wsep;

public class WsepCommunicationException extends RuntimeException {

    public WsepCommunicationException(String message) {
        super(message);
    }

    public WsepCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}