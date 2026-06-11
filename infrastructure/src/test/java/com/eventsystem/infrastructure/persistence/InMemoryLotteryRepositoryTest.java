package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryLotteryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLotteryRepositoryTest {

    private InMemoryLotteryRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryLotteryRepository();
    }

    @Test
    void saveAndFindById() {
        Lottery l = new Lottery(LotteryId.generate(), EventId.random());
        repo.save(l);

        assertThat(repo.findById(l.getLotteryId())).contains(l);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(repo.findById(LotteryId.generate())).isEmpty();
    }

    @Test
    void saveAndFindByEventId() {
        EventId eventId = EventId.random();
        Lottery l = new Lottery(LotteryId.generate(), eventId);
        repo.save(l);

        assertThat(repo.findByEventId(eventId)).contains(l);
    }

    @Test
    void saveTwiceForSameLotteryIsAllowed() {
        Lottery l = new Lottery(LotteryId.generate(), EventId.random());
        repo.save(l);
        repo.save(l);

        assertThat(repo.findById(l.getLotteryId())).isPresent();
    }

    @Test
    void secondLotteryForSameEventIsRejected() {
        EventId eventId = EventId.random();
        Lottery first = new Lottery(LotteryId.generate(), eventId);
        Lottery second = new Lottery(LotteryId.generate(), eventId);
        repo.save(first);

        assertThatThrownBy(() -> repo.save(second))
                .isInstanceOf(IllegalStateException.class);
    }
}
