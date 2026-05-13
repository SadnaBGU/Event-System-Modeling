package com.eventsystem.application.auth;

/** Thrown when registering a member with a username that is already in use. */
public class UsernameAlreadyTakenException extends RuntimeException {
    public UsernameAlreadyTakenException(String username) {
        super("Username already taken: " + username);
    }
}
