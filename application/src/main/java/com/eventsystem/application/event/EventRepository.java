package com.eventsystem.application.event;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;

public interface EventRepository {

    Optional<Event> findById(EventId id);

    List<Event> findByCompany(String companyId);

    void save(Event event);
}