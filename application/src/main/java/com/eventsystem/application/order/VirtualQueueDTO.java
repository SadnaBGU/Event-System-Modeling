package com.eventsystem.application.order;

import com.eventsystem.domain.queue.VirtualQueue;
import com.eventsystem.domain.queue.QueueStatus;

public record VirtualQueueDTO(String queueId, String eventId, QueueStatus status, int loadThreshold, int maxConcurrentAdmissions, long version) {
    public static VirtualQueueDTO fromDomain(VirtualQueue virtualQueue) {
        return new VirtualQueueDTO(
                virtualQueue.getQueueId(),
                virtualQueue.getEventId(),
                virtualQueue.getStatus(),
                virtualQueue.getLoadThreshold(),
                virtualQueue.getMaxConcurrentAdmissions(),
                virtualQueue.getVersion()
        );
    }

    public static VirtualQueue toDomain(VirtualQueueDTO dto) {
        return new VirtualQueue(
                dto.queueId(),
                dto.eventId(),
                dto.loadThreshold(),
                dto.maxConcurrentAdmissions()
        );
    }
}
