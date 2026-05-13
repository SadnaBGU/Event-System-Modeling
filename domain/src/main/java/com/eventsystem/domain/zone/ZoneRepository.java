package com.eventsystem.domain.zone;

import com.eventsystem.domain.event.EventId;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository {
    Optional<Zone> findById(ZoneId zoneId);
    List<Zone> findByEventId(EventId eventId);
    void save(Zone zone);
}
