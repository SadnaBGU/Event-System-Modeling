package com.eventsystem.application.appexceptions;

/**
 * AccountSuspendedReadOnlyException
 */
public class AccountSuspendedReadOnlyException extends RuntimeException {
    public AccountSuspendedReadOnlyException() {
        super("Account is suspended. Read-only access allowed.");
    }

}
