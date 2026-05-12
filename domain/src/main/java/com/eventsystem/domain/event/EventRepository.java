package com.eventsystem.domain.event;

import java.util.List;
import java.util.Optional;

public interface EventRepository {

    Optional<Event> findById(EventId id);

    List<Event> findByCompany(String companyId);

    void save(Event event);
}