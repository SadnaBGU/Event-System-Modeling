package com.eventsystem.domain.venue;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.VenueException;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import org.springframework.data.domain.Persistable;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "venues")
public class Venue implements Persistable<VenueId> {

    @EmbeddedId
    private VenueId venueId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "company_id", nullable = false))
    })
    private CompanyId companyId;

    @Column(name = "venue_name", nullable = false)
    private String venueName;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private List<VenueZone> zones;

    @Version
    @Column(name = "version")
    private Long version;

    protected Venue() {}

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
        this.zones = new ArrayList<>();
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

    public long getVersion() {
        return version;
    }

    public List<VenueZone> getZones() {
        return Collections.unmodifiableList(new ArrayList<>(zones));
    }

    public VenueZone getZone(ZoneId zoneId) {
        return findZone(zoneId);
    }

    public synchronized void addZone(VenueZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("Zone cannot be null");
        }

        // Check if zone with same name exists
        if (zones.stream().anyMatch(z -> 
                z.getZoneId().equals(zone.getZoneId()) || 
                z.getZoneName().equalsIgnoreCase(zone.getZoneName()))) {
            throw new VenueException("Zone already exists or name is duplicated: " + zone.getZoneName());
        }

        zones.add(zone);
    }

    public synchronized void removeZone(ZoneId zoneId) {
        boolean removed = zones.removeIf(z -> z.getZoneId().equals(zoneId));
        if (!removed) {
            throw new VenueException("Zone not found: " + zoneId);
        }
    }

    public synchronized void reserveSeat(ZoneId zoneId, SeatId seatId) {
        VenueZone zone = findZone(zoneId);
        zone.reserveSeat(seatId);
    }

    public synchronized void releaseSeat(ZoneId zoneId, SeatId seatId) {
        VenueZone zone = findZone(zoneId);
        zone.releaseSeat(seatId);
    }

    public synchronized void markSeatSold(ZoneId zoneId, SeatId seatId) {
        VenueZone zone = findZone(zoneId);
        zone.markSeatSold(seatId);
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

    @Transient
    @Override
    public boolean isNew() {
        return this.version == null;
    }

    @Transient
    @Override
    public VenueId getId() {
        return this.venueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Venue venue = (Venue) o;
        return Objects.equals(venueId, venue.venueId);
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
                '}';
    }
}
