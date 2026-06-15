package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.queue.VirtualQueue;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataVirtualQueueRepository extends JpaRepository<VirtualQueue, String> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vq FROM VirtualQueue vq WHERE vq.eventId = :eventId")
    Optional<VirtualQueue> findByEventIdForUpdate(@Param("eventId") String eventId);
    
    Optional<VirtualQueue> findByEventId(String eventId);
}