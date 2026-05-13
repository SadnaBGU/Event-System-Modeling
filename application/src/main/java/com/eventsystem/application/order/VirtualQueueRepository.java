package com.eventsystem.application.order;

import com.eventsystem.domain.queue.VirtualQueue;
import java.util.Optional;

public interface VirtualQueueRepository {
    Optional<VirtualQueue> findByEvent(String eventId);
    void save(VirtualQueue queue);
}