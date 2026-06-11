package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataEventRepository extends JpaRepository<Event, EventId> {

    @Query("SELECT e FROM Event e WHERE e.companyId.value = :companyId")
    List<Event> findByCompanyIdValue(@Param("companyId") String companyId);

    List<Event> findByStatus(EventStatus status);
}
