package com.eventsystem.application.appexceptions;

/**
 * AccountSuspendedException
 */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException() {
        super("Account is suspended. No access allowed.");
    }

}
