package com.eventsystem.application.lottery;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;

import java.util.Optional;

/** Persistence for {@link Lottery}. */
public interface ILotteryRepository {

    Optional<Lottery> findById(LotteryId lotteryId);

    Optional<Lottery> findByEventId(EventId eventId);

    void save(Lottery lottery);
}
