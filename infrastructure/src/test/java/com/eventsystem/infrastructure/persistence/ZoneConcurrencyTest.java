package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.event.ZoneService;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests: multiple threads race to reserve the same resource simultaneously.
 * Uses ExecutorService + CountDownLatch so all threads fire at the same instant.
 */
class ZoneConcurrencyTest {

    private InMemoryZoneRepository repository;
    private ZoneService service;
    private EventId eventId;
    private Money price;

    @BeforeEach
    void setUp() {
        repository = new InMemoryZoneRepository();
        service = new ZoneService(repository);
        eventId = EventId.random();
        price = new Money(new BigDecimal("50.00"), "ILS");
    }

    @Test
    void concurrentSeatReservation_onlyOneThreadSucceeds() throws InterruptedException {
        SeatId seatId = SeatId.random();
        Seat seat = new Seat(seatId, "A", 1);
        Row row = new Row("A", List.of(seat));
        ZoneId zoneId = ZoneId.random();
        Zone zone = Zone.createSeated(zoneId, eventId, "VIP", price, List.of(row));
        repository.save(zone);

        int threads = 10;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGun.await();
                    service.reserveSeat(zoneId, seatId);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);

        Zone finalZone = repository.findById(zoneId).orElseThrow();
        assertThat(finalZone.getAvailableCount()).isEqualTo(0);
    }

    @Test
    void concurrentStandingReservation_noOversell() throws InterruptedException {
        int capacity = 5;
        ZoneId zoneId = ZoneId.random();
        Zone zone = Zone.createStanding(zoneId, eventId, "GA", price, capacity);
        repository.save(zone);

        int threads = 20;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGun.await();
                    service.reserveStanding(zoneId, 1);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    // expected for threads that exceed capacity
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(capacity);

        Zone finalZone = repository.findById(zoneId).orElseThrow();
        assertThat(finalZone.getAvailableCount()).isEqualTo(0);
    }

    @Test
    void concurrentReservationsDifferentZones_dontInterfere() throws InterruptedException {
        ZoneId zone1Id = ZoneId.random();
        ZoneId zone2Id = ZoneId.random();
        repository.save(Zone.createStanding(zone1Id, eventId, "Z1", price, 10));
        repository.save(Zone.createStanding(zone2Id, eventId, "Z2", price, 10));

        int threads = 10;
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads * 2);
        AtomicInteger successes = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGun.await();
                    service.reserveStanding(zone1Id, 1);
                    successes.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startGun.await();
                    service.reserveStanding(zone2Id, 1);
                    successes.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successes.get()).isEqualTo(threads * 2);

        assertThat(repository.findById(zone1Id).orElseThrow().getAvailableCount()).isEqualTo(0);
        assertThat(repository.findById(zone2Id).orElseThrow().getAvailableCount()).isEqualTo(0);
    }
}