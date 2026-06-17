package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class PostgresZoneRepository implements IZoneRepository {

    private final SpringDataZoneRepository jpaRepo;

    public PostgresZoneRepository(SpringDataZoneRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @SuppressWarnings("null")
    @Override
    public Optional<Zone> findById(ZoneId zoneId) {
        return jpaRepo.findById(zoneId);
    }

    @Override
    public List<Zone> findByEventId(EventId eventId) {
        return jpaRepo.findByEventIdValue(eventId.value());
    }

    @SuppressWarnings("null")
    @Override
    public void save(Zone zone) {
        jpaRepo.save(zone);
    }

    @Override
    @Transactional
    public void withLock(ZoneId zoneId, Runnable action) {
        jpaRepo.findByIdWithPessimisticLock(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found for lock: " + zoneId.value()));
        
        action.run();
    }
}