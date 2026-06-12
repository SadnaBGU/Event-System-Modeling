package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.queue.VirtualQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataVirtualQueueRepository extends JpaRepository<VirtualQueue, String> {
    
    Optional<VirtualQueue> findByEventId(String eventId);
}