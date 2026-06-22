package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.order.IActiveOrderRepository;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PostgresActiveOrderRepository implements IActiveOrderRepository {

    private final SpringDataActiveOrderRepository jpaRepo;

    public PostgresActiveOrderRepository(SpringDataActiveOrderRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @SuppressWarnings("null")
    @Override
    public Optional<ActiveOrder> findById(String orderId) {
        return jpaRepo.findById(orderId);
    }

    public List<ActiveOrder> findByEvent(String eventId) {
        return jpaRepo.findByEventId(eventId);
    }

    @SuppressWarnings("null")
    @Override
    public void save(ActiveOrder activeOrder) {
        jpaRepo.save(activeOrder);
    }

    @Override
    public Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId) {
        return jpaRepo.findByBuyerAndEvent(buyer.type(), buyer.sessionId(), buyer.memberId(), eventId);
    }

    @Override
    public Optional<List<ActiveOrder>> findExpired() {
        // Only ACTIVE reservations still hold inventory. CHECKED_OUT / EXPIRED / CANCELLED
        // orders have already released or sold their seats, so they must not be swept again.
        // Always returns a present Optional (possibly empty list) so callers can treat
        // "nothing to sweep" as a normal outcome rather than an error.
        List<ActiveOrder> expiredOrders =
                jpaRepo.findByStatusAndReservationExpiryBefore(OrderStatus.ACTIVE, Instant.now());
        return Optional.of(expiredOrders);
    }

    @SuppressWarnings("null")
    @Override
    public void delete(String orderId) {
        jpaRepo.deleteById(orderId);
    }
    
}
