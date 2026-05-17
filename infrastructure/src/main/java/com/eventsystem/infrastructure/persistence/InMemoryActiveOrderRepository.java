package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.order.IActiveOrderRepository;
import com.eventsystem.domain.order.ActiveOrder;
import com.eventsystem.domain.order.BuyerReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryActiveOrderRepository implements IActiveOrderRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryActiveOrderRepository.class);
    
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
    public Optional<List<ActiveOrder>> findExpired() {
        List<ActiveOrder> expiredOrders = store.values().stream()
                .filter(ActiveOrder::isExpired)
                .toList();
        return Optional.of(expiredOrders);
    }

    @Override
    public void save(ActiveOrder order) {
        store.put(order.getOrderId(), order);
        logger.info("Persisted ActiveOrder to memory store: {}", order.getOrderId());
    }

    @Override
    public void delete(String orderId) {
        ActiveOrder removed = store.remove(orderId);
        if (removed != null) {
            logger.info("Deleted ActiveOrder from memory store: {}", orderId);
        } else {
            logger.warn("Attempted to delete non-existent ActiveOrder: {}", orderId);
        }
    }
}