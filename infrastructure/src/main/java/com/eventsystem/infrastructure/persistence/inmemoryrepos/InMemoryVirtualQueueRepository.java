package com.eventsystem.infrastructure.persistence.inmemoryrepos;

import com.eventsystem.domain.queue.IVirtualQueueRepository;
import com.eventsystem.domain.queue.VirtualQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVirtualQueueRepository implements IVirtualQueueRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryVirtualQueueRepository.class);
    
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
        logger.info("Persisted VirtualQueue state for event {} to memory store", queue.getEventId());
    }
}