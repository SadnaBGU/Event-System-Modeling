package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresLotteryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresLotteryRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LotteryConcurrencyTest {

    @Autowired
    private PostgresLotteryRepository repo;

    @Autowired
    private TransactionTemplate txTemplate;

    private LotteryService service;
    private LotteryId lotteryId;

    @BeforeEach
    void setUp() {
        service = new LotteryService(repo, new Random(1L), Clock.systemUTC(), Duration.ofMinutes(15));
        
        txTemplate.executeWithoutResult(status -> {
            lotteryId = service.openLottery(EventId.random());
        });
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
                        
                        boolean success = false;
                        while (!success) {
                            try {
                                txTemplate.executeWithoutResult(status -> {
                                    service.register(memberId, lotteryId);
                                });
                                success = true;
                            } catch (OptimisticLockingFailureException e) {
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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

        txTemplate.executeWithoutResult(status -> {
            Lottery stored = repo.findById(lotteryId).orElseThrow();
            assertThat(stored.getRegistrations()).hasSize(threads);
        });
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
                        
                        boolean success = false;
                        while (!success) {
                            try {
                                txTemplate.executeWithoutResult(status -> {
                                    service.register(shared, lotteryId);
                                });
                                success = true;
                            } catch (OptimisticLockingFailureException e) {
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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

        txTemplate.executeWithoutResult(status -> {
            Lottery stored = repo.findById(lotteryId).orElseThrow();
            assertThat(stored.getRegistrations()).containsExactly(shared);
        });
    }
}