package com.eventsystem.infrastructure.external.wsep.common;

public class WsepCommunicationException extends RuntimeException {

    public WsepCommunicationException(String message) {
        super(message);
    }

    public WsepCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}