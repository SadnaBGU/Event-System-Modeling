package com.eventsystem.domain.queue;

import java.util.Optional;

public interface IVirtualQueueRepository {
    Optional<VirtualQueue> findByEvent(String eventId);
    void save(VirtualQueue queue);
}