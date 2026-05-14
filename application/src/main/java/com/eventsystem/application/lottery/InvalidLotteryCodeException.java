package com.eventsystem.application.lottery;

public class InvalidLotteryCodeException extends RuntimeException {
    public InvalidLotteryCodeException() {
        super("Invalid or expired lottery code");
    }
}
