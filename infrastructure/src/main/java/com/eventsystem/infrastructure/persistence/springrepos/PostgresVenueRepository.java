package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.venue.IVenueRepository;
import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PostgresVenueRepository implements IVenueRepository {

    private final SpringDataVenueRepository jpaRepository;

    public PostgresVenueRepository(SpringDataVenueRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    @Override
    public void save(Venue venue) {
        Objects.requireNonNull(venue, "venue must not be null");
        jpaRepository.save(venue);
    }

    @Override
    public Optional<Venue> findById(VenueId id) {
        Objects.requireNonNull(id, "id must not be null");
        return jpaRepository.findById(id);
    }

    @Override
    public List<Venue> findAll() {
        return jpaRepository.findAll();
    }
    
    @Override
    public void delete(VenueId id) {
        Objects.requireNonNull(id, "id must not be null");
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Venue> findByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jpaRepository.findAll().stream()
                .filter(venue -> venue.getCompanyId().equals(companyId))
                .toList();
                
    }
}