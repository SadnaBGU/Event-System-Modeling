package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import com.eventsystem.domain.event.IEventRepository;

import java.util.List;
import java.util.Optional;

public class PostgresEventRepository implements IEventRepository {

    private final SpringDataEventRepository jpaRepo;

    public PostgresEventRepository(SpringDataEventRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @SuppressWarnings("null")
    @Override
    public Optional<Event> findById(EventId id) {
        return jpaRepo.findById(id);
    }

    @Override
    public List<Event> findByCompany(CompanyId companyId) {
        return jpaRepo.findByCompanyIdValue(companyId.value());
    }

    @Override
    public List<Event> findPublishedEvents() {
        return jpaRepo.findByStatus(EventStatus.PUBLISHED);
    }

    @Override
    public List<Event> findAll() {
        return jpaRepo.findAll();
    }

    @SuppressWarnings("null")
    @Override
    public void save(Event event) {
        jpaRepo.save(event);
    }
}