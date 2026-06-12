package com.eventsystem.domain.queue;

import java.util.List;
import java.util.Optional;

public interface IVirtualQueueRepository {
    Optional<VirtualQueue> findById(String queueId);
    Optional<VirtualQueue> findByEvent(String eventId);
    void save(VirtualQueue queue);
    List<VirtualQueue> findAll();
}