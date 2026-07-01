package com.eventsystem.application.appexceptions;

/** Raised when a suspended account attempts to authenticate or use an authenticated endpoint. */
public class AccountSuspendedException extends AuthenticationException {
    public AccountSuspendedException() {
        super("User account has been suspended");
    }
}
