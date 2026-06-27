package com.eventsystem.infrastructure.config.startup;

public class StartupConfigException extends RuntimeException {

    public StartupConfigException(String message) {
        super(message);
    }

    public StartupConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}