package com.eventsystem.domain.order;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface IActiveOrderRepository {
    Optional<ActiveOrder> findById(String orderId);
    Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId);
    Optional<List<ActiveOrder>> findExpired();
    long countActiveNonExpiredByEvent(String eventId, Instant now);
    void save(ActiveOrder order);
    void delete(String orderId);
}