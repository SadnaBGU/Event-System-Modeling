package com.eventsystem.application.appexceptions;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.LotteryId;

public class LotteryNotFoundException extends RuntimeException {
    public LotteryNotFoundException(LotteryId id) {
        super("Lottery not found: " + id.value());
    }

    public LotteryNotFoundException(EventId eventId) {
        super("Lottery not found for event: " + eventId.value());
    }
}
