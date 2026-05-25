package com.eventsystem.application.event;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository {
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
