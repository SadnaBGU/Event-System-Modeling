package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.venue.IVenueRepository;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryVenueRepository implements IVenueRepository {
    private final ConcurrentHashMap<VenueId, Venue> venues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CompanyId, List<VenueId>> companyVenues = new ConcurrentHashMap<>();

    @Override
    public Optional<Venue> findById(VenueId venueId) {
        return Optional.ofNullable(venues.get(venueId));
    }

    @Override
    public List<Venue> findByCompanyId(CompanyId companyId) {
        List<VenueId> venueIds = companyVenues.getOrDefault(companyId, List.of());
        return venueIds.stream()
                .map(venues::get)
                .filter(v -> v != null)
                .collect(Collectors.toList());
    }

    @Override
    public void save(Venue venue) {
        if (venue == null) {
            throw new IllegalArgumentException("Venue cannot be null");
        }

        VenueId venueId = venue.getVenueId();
        CompanyId companyId = venue.getCompanyId();

        venues.put(venueId, venue);

        // Update company->venues mapping
        companyVenues.computeIfAbsent(companyId, k -> Collections.synchronizedList(new ArrayList<>()));
        List<VenueId> venueList = companyVenues.get(companyId);
        if (!venueList.contains(venueId)) {
            synchronized (venueList) {
                if (!venueList.contains(venueId)) {
                    venueList.add(venueId);
                }
            }
        }
    }

    @Override
    public void delete(VenueId venueId) {
        Venue venue = venues.remove(venueId);
        if (venue != null) {
            CompanyId companyId = venue.getCompanyId();
            List<VenueId> venueList = companyVenues.get(companyId);
            if (venueList != null) {
                synchronized (venueList) {
                    venueList.remove(venueId);
                }
            }
        }
    }
}
