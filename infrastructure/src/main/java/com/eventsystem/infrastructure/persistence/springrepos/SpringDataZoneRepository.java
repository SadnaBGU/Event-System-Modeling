package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface SpringDataZoneRepository extends JpaRepository<Zone, ZoneId> {

    @Query("SELECT z FROM Zone z WHERE z.eventId.value = :eventId")
    List<Zone> findByEventIdValue(@Param("eventId") String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT z FROM Zone z WHERE z.zoneId = :zoneId")
    Optional<Zone> findByIdWithPessimisticLock(@Param("zoneId") ZoneId zoneId);
}