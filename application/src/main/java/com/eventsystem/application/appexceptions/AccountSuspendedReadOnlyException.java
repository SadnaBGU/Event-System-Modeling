package com.eventsystem.application.appexceptions;

/** Raised when a suspended account attempts a non-read action. */
public class AccountSuspendedReadOnlyException extends RuntimeException {
    public AccountSuspendedReadOnlyException() {
        super("Account is suspended. Read-only access only.");
    }
}
