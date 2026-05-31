package com.eventsystem.application.event;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;


public interface IEventRepository {

    Optional<Event> findById(EventId id);

    List<Event> findByCompany(CompanyId companyId);

    List<Event> findPublishedEvents();

    void save(Event event);
}