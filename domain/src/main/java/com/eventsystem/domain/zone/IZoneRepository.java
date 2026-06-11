package com.eventsystem.domain.zone;

import com.eventsystem.domain.event.EventId;

import java.util.List;
import java.util.Optional;


public interface IZoneRepository {
    Optional<Zone> findById(ZoneId zoneId);
    List<Zone> findByEventId(EventId eventId);
    void save(Zone zone);

    /**
     * Acquires an exclusive per-zone lock, runs {@code action}, then releases the lock.
     * Allows any service that holds a ZoneRepository to perform atomic read-modify-write
     * operations without cross-service coupling.
     */
    void withLock(ZoneId zoneId, Runnable action);
}
