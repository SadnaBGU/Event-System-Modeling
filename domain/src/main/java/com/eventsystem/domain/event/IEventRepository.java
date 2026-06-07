package com.eventsystem.domain.event;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.company.CompanyId;


public interface IEventRepository {

    Optional<Event> findById(EventId id);

    List<Event> findByCompany(CompanyId companyId);

    List<Event> findPublishedEvents();

    List<Event> findAll();

    void save(Event event);
}