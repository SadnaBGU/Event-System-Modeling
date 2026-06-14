package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface SpringDataLotteryRepository extends JpaRepository<Lottery, LotteryId> {
    
    Optional<Lottery> findByEventId(EventId eventId);
}