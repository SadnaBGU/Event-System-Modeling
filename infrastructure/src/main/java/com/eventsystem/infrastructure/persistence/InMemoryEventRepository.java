package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.event.IEventRepository;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public class InMemoryEventRepository implements IEventRepository {

    private final ConcurrentMap<EventId, Event> events = new ConcurrentHashMap<>();

    @Override
    public Optional<Event> findById(EventId id) {
        Objects.requireNonNull(id, "event id must not be null");

        return Optional.ofNullable(events.get(id));
    }

    @Override
    public List<Event> findByCompany(String companyId) {
        Objects.requireNonNull(companyId, "company id must not be null");

        return events.values()
                .stream()
            .filter(event -> event.companyId().value().equals(companyId))
                .toList();
    }

    @Override
    public void save(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        events.put(event.id(), event);
    }
}