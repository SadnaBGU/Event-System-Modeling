package com.eventsystem.application.order;

import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;
import java.util.Optional;
import java.util.List;

public interface IActiveOrderRepository {
    Optional<ActiveOrder> findById(String orderId);
    Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId);
    Optional<List<ActiveOrder>> findExpired();
    void save(ActiveOrder order);
    void delete(String orderId);
}