package com.eventsystem.application.order;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import java.util.Optional;
import java.util.List;

public interface ActiveOrderRepository {
    Optional<ActiveOrder> findById(String orderId);
    Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId);
    Optional<List<ActiveOrder>> findExpired();
    List<ActiveOrder> findActiveOrdersByEvent(EventId eventId);
    void save(ActiveOrder order);
    void delete(String orderId);
}