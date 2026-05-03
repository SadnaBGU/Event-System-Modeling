package com.eventsystem.application.order;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import java.util.Optional;

public interface ActiveOrderRepository {
    Optional<ActiveOrder> findById(String orderId);
    Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId);
    void save(ActiveOrder order);
    void delete(String orderId);
}