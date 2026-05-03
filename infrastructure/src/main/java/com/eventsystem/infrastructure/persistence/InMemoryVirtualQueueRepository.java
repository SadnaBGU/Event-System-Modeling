package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.order.VirtualQueueRepository;
import com.eventsystem.domain.queue.VirtualQueue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVirtualQueueRepository implements VirtualQueueRepository {
    private final Map<String, VirtualQueue> store = new ConcurrentHashMap<>();

    @Override
    public Optional<VirtualQueue> findByEvent(String eventId) {
        return store.values().stream()
                .filter(queue -> queue.getEventId().equals(eventId))
                .findFirst();
    }

    @Override
    public void save(VirtualQueue queue) {
        store.put(queue.getQueueId(), queue);
    }
}