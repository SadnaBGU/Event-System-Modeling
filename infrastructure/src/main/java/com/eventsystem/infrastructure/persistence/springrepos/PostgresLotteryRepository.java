package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;

import java.util.Objects;
import java.util.Optional;

public class PostgresLotteryRepository implements ILotteryRepository {

    private final SpringDataLotteryRepository jpaRepository;

    public PostgresLotteryRepository(SpringDataLotteryRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    public void save(Lottery lottery) {
        Objects.requireNonNull(lottery, "lottery must not be null");
        jpaRepository.save(lottery);
    }

    public Optional<Lottery> findById(LotteryId lotteryId) {
        Objects.requireNonNull(lotteryId, "lotteryId must not be null");
        return jpaRepository.findById(lotteryId);
    }

    public Optional<Lottery> findByEventId(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return jpaRepository.findByEventId(eventId);
    }

    public void delete(Lottery lottery) {
        Objects.requireNonNull(lottery, "lottery must not be null");
        jpaRepository.delete(lottery);
    }
}