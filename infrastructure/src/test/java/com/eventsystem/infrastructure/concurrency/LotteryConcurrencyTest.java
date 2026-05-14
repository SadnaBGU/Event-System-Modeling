package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.InMemoryLotteryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LotteryConcurrencyTest {

    private InMemoryLotteryRepository repo;
    private LotteryService service;
    private LotteryId lotteryId;

    @BeforeEach
    void setUp() {
        repo = new InMemoryLotteryRepository();
        service = new LotteryService(repo, new Random(1L), Clock.systemUTC(), Duration.ofMinutes(15));
        lotteryId = service.openLottery(EventId.random());
    }

    @Test
    void concurrentRegistrations_allRecordedExactlyOnce() throws InterruptedException {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try {
            for (int i = 0; i < threads; i++) {
                final MemberId memberId = new MemberId("member-" + i);
                pool.submit(() -> {
                    try {
                        start.await();
                        service.register(memberId, lotteryId);
                    } catch (Exception e) {
                        throw new AssertionError("Unexpected exception", e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        Lottery stored = repo.findById(lotteryId).orElseThrow();
        assertThat(stored.getRegistrations()).hasSize(threads);
    }

    @Test
    void concurrentDuplicateRegistrations_onlyOneRecorded() throws InterruptedException {
        int threads = 50;
        MemberId shared = new MemberId("shared-member");
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        service.register(shared, lotteryId);
                    } catch (Exception e) {
                        throw new AssertionError("Unexpected exception", e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        Lottery stored = repo.findById(lotteryId).orElseThrow();
        assertThat(stored.getRegistrations()).containsExactly(shared);
    }
}
