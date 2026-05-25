package com.eventsystem.application.order;

import com.eventsystem.application.event.ZoneRepository;
import com.eventsystem.application.event.ZoneService;
import com.eventsystem.application.event.ZoneServicePort;
import com.eventsystem.application.lottery.LotteryRepository;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.*;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Row;
import com.eventsystem.domain.zone.Seat;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneType;

import com.eventsystem.application.event.ZoneRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// track a: concurrency test for orderservice. 100 threads attempt to reserve the same seat. expected: 1 succeeds, 99 fail.
@ExtendWith(MockitoExtension.class)
class OrderConcurrencyTest {

    @Mock
    private IActiveOrderRepository orderRepository;

    @Mock
    private LotteryRepository lotteryRepository;

    private ZoneRepository zoneRepository;
    private OrderService orderService;
    private OrderFactory orderFactory;

    private final String ORDER_ID = "ORDER-CONCURRENT-TEST";
    private final String ZONE_ID = "VIP-ZONE";
    private final String SEAT_ID = "SEAT-A1";
    private final String EVENT_ID = "EVENT-2026";

    @BeforeEach
    void setUp() {
        // use thread-safe fake instead of mockito
        zoneRepository = new ThreadSafeZoneRepository();
        orderFactory = new OrderFactory();
        orderService = new OrderService(orderRepository, zoneRepository, orderFactory, lotteryRepository);
    }

    @Test
    void testConcurrentSeatReservation_100Threads_OnlyOneSucceeds() throws InterruptedException {
        ActiveOrder testOrder = orderFactory.createOrder(
                new BuyerReference(BuyerType.MEMBER, "session-master", "master-buyer"),
                EVENT_ID,
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));

        Money price = mock(Money.class);
        lenient().when(price.amount()).thenReturn(new BigDecimal("150.00"));

        ZoneId zoneIdObj = new ZoneId(ZONE_ID);
        EventId eventIdObj = new EventId(EVENT_ID);
        SeatId seatIdObj = new SeatId(SEAT_ID);

        Seat seat = new Seat(seatIdObj, "Row A", 1);
        Row row = new Row("Row A", List.of(seat));
        Zone realZone = Zone.createSeated(zoneIdObj, eventIdObj, "VIP", price, List.of(row));
        zoneRepository.save(realZone);

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch endSignal = new CountDownLatch(100);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    orderService.reserveSeat(ORDER_ID, ZONE_ID, SEAT_ID);
                    successCount.incrementAndGet();
                } catch (RuntimeException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    endSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        boolean completed = endSignal.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(99);
        verify(orderRepository, times(1)).save(testOrder);
        assertThat(realZone.getAvailableCount()).isEqualTo(0);
    }

    @Test
    void testConcurrentDifferentSeats_AllSucceed() throws InterruptedException {
        ActiveOrder testOrder = orderFactory.createOrder(
                new BuyerReference(BuyerType.MEMBER, "session-master", "master-buyer"),
                EVENT_ID,
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(testOrder));

        Money price = mock(Money.class);
        lenient().when(price.amount()).thenReturn(new BigDecimal("150.00"));

        ZoneId zoneIdObj = new ZoneId(ZONE_ID);
        EventId eventIdObj = new EventId(EVENT_ID);

        List<Seat> seats = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seats.add(new Seat(new SeatId("SEAT-" + i), "Row A", i + 1));
        }
        Row row = new Row("Row A", seats);
        Zone realZone = Zone.createSeated(zoneIdObj, eventIdObj, "VIP", price, List.of(row));
        zoneRepository.save(realZone);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch endSignal = new CountDownLatch(10);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            final int seatNum = i;
            executor.submit(() -> {
                try {
                    startSignal.await();
                    orderService.reserveSeat(ORDER_ID, ZONE_ID, "SEAT-" + seatNum);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    endSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        endSignal.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(10);
        verify(orderRepository, times(10)).save(testOrder);
        assertThat(realZone.getAvailableCount()).isEqualTo(realZone.totalCapacity() - 10);
    }

    // thread-safe zone repository stub for concurrent testing
    private static class ThreadSafeZoneRepository implements ZoneRepository {
        private final Map<String, Zone> store = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        @Override
        public Optional<Zone> findById(ZoneId zoneId) {
            return Optional.ofNullable(store.get(zoneId.value()));
        }

        @Override
        public List<Zone> findByEventId(EventId eventId) {
            return store.values().stream()
                    .filter(z -> z.eventId().equals(eventId))
                    .toList();
        }

        @Override
        public void save(Zone zone) {
            store.put(zone.zoneId().value(), zone);
        }

        @Override
        public void withLock(ZoneId zoneId, Runnable action) {
            ReentrantLock lock = locks.computeIfAbsent(zoneId.value(), k -> new ReentrantLock());
            lock.lock();
            try {
                // execute action safely so zoneHolder gets populated
                action.run(); 
            } finally {
                lock.unlock();
            }
        }
    }
}