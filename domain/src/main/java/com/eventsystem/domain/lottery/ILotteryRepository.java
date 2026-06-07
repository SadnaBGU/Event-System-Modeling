package com.eventsystem.domain.lottery;

import com.eventsystem.domain.event.EventId;

import java.util.Optional;

/** Persistence for {@link Lottery}. */
public interface ILotteryRepository {

    Optional<Lottery> findById(LotteryId lotteryId);

    Optional<Lottery> findByEventId(EventId eventId);

    void save(Lottery lottery);
}
