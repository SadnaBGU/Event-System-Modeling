package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.venue.Venue;
import com.eventsystem.domain.venue.VenueId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataVenueRepository extends JpaRepository<Venue, VenueId> {}