package com.eventsystem.application.appexceptions;

/**
 * Thrown when authentication fails (bad credentials, unknown user, cancelled account).
 * Message is intentionally generic to avoid leaking which field was wrong.
 */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
