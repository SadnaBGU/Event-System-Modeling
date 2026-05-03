package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.order.ActiveOrderRepository;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActiveOrderRepository implements ActiveOrderRepository {
    private final Map<String, ActiveOrder> store = new ConcurrentHashMap<>();

    @Override
    public Optional<ActiveOrder> findById(String orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public Optional<ActiveOrder> findByBuyerAndEvent(BuyerReference buyer, String eventId) {
        return store.values().stream()
                .filter(order -> order.getEventId().equals(eventId) && order.getBuyerRef().equals(buyer))
                .findFirst();
    }

    @Override
    public void save(ActiveOrder order) {
        store.put(order.getOrderId(), order);
    }

    @Override
    public void delete(String orderId) {
        store.remove(orderId);
    }
}