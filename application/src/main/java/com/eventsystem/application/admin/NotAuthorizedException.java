package com.eventsystem.application.admin;

/** Thrown when an actor is not a system administrator and tries to invoke an admin operation. */
public class NotAuthorizedException extends RuntimeException {
    public NotAuthorizedException(String actor) {
        super("Actor " + actor + " is not a system administrator");
    }
}
