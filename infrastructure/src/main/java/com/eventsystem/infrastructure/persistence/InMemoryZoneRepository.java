package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryZoneRepository implements ZoneRepository {

    private final Map<String, Zone> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> zoneLocks = new ConcurrentHashMap<>();

    @Override
    public Optional<Zone> findById(ZoneId zoneId) {
        return Optional.ofNullable(store.get(zoneId.value()));
    }

    @Override
    public List<Zone> findByEventId(EventId eventId) {
        return store.values().stream()
                .filter(z -> z.eventId().equals(eventId))
                .toList();
    }

    @Override
    public void save(Zone zone) {
        store.put(zone.zoneId().value(), zone);
    }

    @Override
    public void withLock(ZoneId zoneId, Runnable action) {
        ReentrantLock lock = zoneLocks.computeIfAbsent(zoneId.value(), k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
