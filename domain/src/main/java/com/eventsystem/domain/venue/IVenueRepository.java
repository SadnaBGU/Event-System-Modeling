package com.eventsystem.domain.venue;

import com.eventsystem.domain.company.CompanyId;

import java.util.List;
import java.util.Optional;

public interface IVenueRepository {
    Optional<Venue> findById(VenueId venueId);

    List<Venue> findByCompanyId(CompanyId companyId);

    void save(Venue venue);

    void delete(VenueId venueId);
}
