package com.eventsystem.application.venue;

import com.eventsystem.application.member.MemberRepository;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.VenueException;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.venue.*;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class VenueManagementService {
    private final VenueRepository venueRepository;
    private final MemberRepository memberRepository;

    public VenueManagementService(VenueRepository venueRepository, MemberRepository memberRepository) {
        this.venueRepository = Objects.requireNonNull(venueRepository, "VenueRepository cannot be null");
        this.memberRepository = Objects.requireNonNull(memberRepository, "MemberRepository cannot be null");
    }

    public Venue createVenue(CompanyId companyId, String venueName) {
        if (companyId == null || venueName == null) {
            throw new IllegalArgumentException("CompanyId and venueName cannot be null");
        }

        Venue venue = new Venue(VenueId.generate(), companyId, venueName);
        venueRepository.save(venue);
        return venue;
    }

    public void addSeatedZone(VenueId venueId, String zoneName, BigDecimal pricePerTicket, String currency, int capacity) {
        if (venueId == null || zoneName == null || pricePerTicket == null || currency == null) {
            throw new IllegalArgumentException("VenueId, zoneName, pricePerTicket, and currency cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        Money price = new Money(pricePerTicket, currency);
        VenueZone zone = new VenueZone(ZoneId.random(), zoneName, ZoneType.SEATED, price, capacity);
        venue.addZone(zone);
        venueRepository.save(venue);
    }

    public void addStandingZone(VenueId venueId, String zoneName, BigDecimal pricePerTicket, String currency, int capacity) {
        if (venueId == null || zoneName == null || pricePerTicket == null || currency == null) {
            throw new IllegalArgumentException("VenueId, zoneName, pricePerTicket, and currency cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        Money price = new Money(pricePerTicket, currency);
        VenueZone zone = new VenueZone(ZoneId.random(), zoneName, ZoneType.STANDING, price, capacity);
        venue.addZone(zone);
        venueRepository.save(venue);
    }

    public void removeZone(VenueId venueId, ZoneId zoneId) {
        if (venueId == null || zoneId == null) {
            throw new IllegalArgumentException("VenueId and ZoneId cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        venue.removeZone(zoneId);
        venueRepository.save(venue);
    }

    public VenueZone getZone(VenueId venueId, ZoneId zoneId) {
        if (venueId == null || zoneId == null) {
            throw new IllegalArgumentException("VenueId and ZoneId cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        return venue.getZone(zoneId);
    }

    public Venue getVenue(VenueId venueId) {
        if (venueId == null) {
            throw new IllegalArgumentException("VenueId cannot be null");
        }

        return venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));
    }

    public List<Venue> getCompanyVenues(CompanyId companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("CompanyId cannot be null");
        }

        return venueRepository.findByCompanyId(companyId);
    }

    public void reserveSeat(VenueId venueId, ZoneId zoneId, SeatId seatId) {
        if (venueId == null || zoneId == null || seatId == null) {
            throw new IllegalArgumentException("VenueId, ZoneId, and SeatId cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        VenueZone zone = venue.getZone(zoneId);
        zone.reserveSeat(seatId);
        venueRepository.save(venue);
    }

    public void releaseSeat(VenueId venueId, ZoneId zoneId, SeatId seatId) {
        if (venueId == null || zoneId == null || seatId == null) {
            throw new IllegalArgumentException("VenueId, ZoneId, and SeatId cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        VenueZone zone = venue.getZone(zoneId);
        zone.releaseSeat(seatId);
        venueRepository.save(venue);
    }

    public void markSeatSold(VenueId venueId, ZoneId zoneId, SeatId seatId) {
        if (venueId == null || zoneId == null || seatId == null) {
            throw new IllegalArgumentException("VenueId, ZoneId, and SeatId cannot be null");
        }

        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueException("Venue not found: " + venueId));

        VenueZone zone = venue.getZone(zoneId);
        zone.markSeatSold(seatId);
        venueRepository.save(venue);
    }
}
