package com.eventsystem.application.appexceptions;

import com.eventsystem.domain.lottery.LotteryId;

public class LotteryNotFoundException extends RuntimeException {
    public LotteryNotFoundException(LotteryId id) {
        super("Lottery not found: " + id.value());
    }
}
