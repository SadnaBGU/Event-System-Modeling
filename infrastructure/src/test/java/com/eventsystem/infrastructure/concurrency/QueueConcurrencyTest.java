package com.eventsystem.infrastructure.concurrency;

import com.eventsystem.application.order.QueueService;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.queue.VirtualQueue;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresVirtualQueueRepository;
import com.eventsystem.application.member.INotificationPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresVirtualQueueRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QueueConcurrencyTest {

    @Autowired
    private PostgresVirtualQueueRepository repository;

    @Autowired
    private TransactionTemplate txTemplate;

    private QueueService service;
    private final String eventId = "EVT-CONCURRENT";

    @BeforeEach
    void setUp() {
        INotificationPort mockNotificationPort = mock(INotificationPort.class);
        service = new QueueService(repository, mockNotificationPort);

        txTemplate.executeWithoutResult(status -> {
            service.enqueueVisitor(eventId, new BuyerReference(BuyerType.MEMBER, "init-session", "init-user"));
        });
    }

    @Test
    void concurrentEnqueue_AllVisitorsGetUniquePositions() throws InterruptedException {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            pool.submit(() -> {
                BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, "sess-" + index, "user-" + index);
                try {
                    startGun.await();
                    
                    boolean success = false;
                    while (!success) {
                        try {
                            txTemplate.executeWithoutResult(status -> {
                                service.enqueueVisitor(eventId, buyer);
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

        startGun.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        txTemplate.executeWithoutResult(status -> {
            VirtualQueue queue = repository.findByEvent(eventId).orElseThrow();
            
            int totalInQueue = queue.clearQueue().size();
            assertThat(totalInQueue).isEqualTo(101);
        });
    }
}