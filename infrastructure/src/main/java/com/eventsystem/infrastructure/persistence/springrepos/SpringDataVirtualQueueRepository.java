package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.queue.VirtualQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface SpringDataVirtualQueueRepository extends JpaRepository<VirtualQueue, String> {

    Optional<VirtualQueue> findByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from VirtualQueue q where q.eventId = :eventId")
    Optional<VirtualQueue> findByEventIdForUpdate(@Param("eventId") String eventId);
}