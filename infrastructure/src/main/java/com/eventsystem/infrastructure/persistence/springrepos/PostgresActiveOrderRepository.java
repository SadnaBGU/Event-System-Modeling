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

    public List<ActiveOrder> findExpiredReservations() {
        // Keeps the infrastructure detail (Instant.now()) encapsulated here
        return jpaRepo.findByStatusAndReservationExpiryBefore(OrderStatus.CHECKED_OUT, Instant.now());
    }

    @SuppressWarnings("null")
    @Override
    public void save(ActiveOrder activeOrder) {
        jpaRepo.save(activeOrder);
    }

    @Override
    public Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId) {
        return jpaRepo.findByBuyerRefAndEventId(buyer, eventId);
    }

    @Override
    public Optional<List<ActiveOrder>> findExpired() {
        List<ActiveOrder> expiredOrders = jpaRepo.findByReservationExpiryBefore(Instant.now());
        return expiredOrders.isEmpty() ? Optional.empty() : Optional.of(expiredOrders);
    }

    @Override
    public void delete(String orderId) {
        jpaRepo.deleteById(orderId);
    }
    
}
