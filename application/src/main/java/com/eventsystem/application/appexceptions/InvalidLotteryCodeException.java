package com.eventsystem.application.appexceptions;

public class InvalidLotteryCodeException extends RuntimeException {
    public InvalidLotteryCodeException() {
        super("Invalid or expired lottery code");
    }
}
