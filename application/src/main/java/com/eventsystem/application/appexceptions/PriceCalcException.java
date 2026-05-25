package com.eventsystem.application.appexceptions;

public class PriceCalcException extends RuntimeException {
    public PriceCalcException(String message) {
        super("Price calculation error: " + message);
    }
    
}
