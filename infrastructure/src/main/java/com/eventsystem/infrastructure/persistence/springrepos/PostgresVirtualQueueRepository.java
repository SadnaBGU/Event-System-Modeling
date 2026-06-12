package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.queue.IVirtualQueueRepository;
import com.eventsystem.domain.queue.VirtualQueue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PostgresVirtualQueueRepository implements IVirtualQueueRepository {

    private final SpringDataVirtualQueueRepository jpaRepository;

    public PostgresVirtualQueueRepository(SpringDataVirtualQueueRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    @Override
    public void save(VirtualQueue queue) {
        Objects.requireNonNull(queue, "queue must not be null");
        jpaRepository.save(queue);
    }

    @Override
    public Optional<VirtualQueue> findById(String queueId) {
        Objects.requireNonNull(queueId, "queueId must not be null");
        return jpaRepository.findById(queueId);
    }

    @Override
    public Optional<VirtualQueue> findByEvent(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return jpaRepository.findByEventId(eventId);
    }

    @Override
    public List<VirtualQueue> findAll() {
        return jpaRepository.findAll();
    }
}