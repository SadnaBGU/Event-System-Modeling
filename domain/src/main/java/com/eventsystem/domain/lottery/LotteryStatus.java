package com.eventsystem.domain.lottery;

/** Lottery lifecycle: REGISTRATION_OPEN -> CLOSED -> DRAWN. */
public enum LotteryStatus {
    REGISTRATION_OPEN,
    CLOSED,
    DRAWN
}
