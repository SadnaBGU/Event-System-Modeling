package com.eventsystem.domain.venue;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.zone.ZoneId;
import java.util.*;

public class Venue {
    private final VenueId venueId;
    private final CompanyId companyId;
    private final String venueName;
    private final List<VenueZone> zones;

    public Venue(VenueId venueId, CompanyId companyId, String venueName) {
        if (venueId == null || companyId == null || venueName == null) {
            throw new IllegalArgumentException("VenueId, CompanyId, and venueName cannot be null");
        }
        if (venueName.isBlank()) {
            throw new IllegalArgumentException("Venue name cannot be blank");
        }

        this.venueId = venueId;
        this.companyId = companyId;
        this.venueName = venueName;
        this.zones = Collections.synchronizedList(new ArrayList<>());
    }

    public VenueId getVenueId() {
        return venueId;
    }

    public CompanyId getCompanyId() {
        return companyId;
    }

    public String getVenueName() {
        return venueName;
    }

    public List<VenueZone> getZones() {
        synchronized (zones) {
            return Collections.unmodifiableList(new ArrayList<>(zones));
        }
    }

    public synchronized void addZone(VenueZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("Zone cannot be null");
        }

        // Check if zone with same name exists
        if (zones.stream().anyMatch(z -> z.getZoneName().equalsIgnoreCase(zone.getZoneName()))) {
            throw new VenueException("Zone with name '" + zone.getZoneName() + "' already exists in venue");
        }

        zones.add(zone);
    }

    public synchronized void removeZone(ZoneId zoneId) {
        if (zoneId == null) {
            throw new IllegalArgumentException("ZoneId cannot be null");
        }

        VenueZone zone = findZone(zoneId);
        zones.remove(zone);
    }

    public synchronized VenueZone getZone(ZoneId zoneId) {
        return findZone(zoneId);
    }

    public synchronized int getTotalCapacity() {
        return zones.stream()
                .mapToInt(VenueZone::getTotalCapacity)
                .sum();
    }

    public synchronized int getTotalAvailableSeats() {
        return zones.stream()
                .mapToInt(VenueZone::getAvailableCount)
                .sum();
    }

    public synchronized int getTotalReservedSeats() {
        return zones.stream()
                .mapToInt(VenueZone::getReservedCount)
                .sum();
    }

    public synchronized int getTotalSoldSeats() {
        return zones.stream()
                .mapToInt(VenueZone::getSoldCount)
                .sum();
    }

    private VenueZone findZone(ZoneId zoneId) {
        return zones.stream()
                .filter(z -> z.getZoneId().equals(zoneId))
                .findFirst()
                .orElseThrow(() -> new VenueException("Zone not found: " + zoneId));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Venue venue = (Venue) o;
        return venueId.equals(venue.venueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(venueId);
    }

    @Override
    public String toString() {
        return "Venue{" +
                "venueId=" + venueId +
                ", companyId=" + companyId +
                ", venueName='" + venueName + '\'' +
                ", zones=" + zones.size() +
                ", totalCapacity=" + getTotalCapacity() +
                '}';
    }
}
