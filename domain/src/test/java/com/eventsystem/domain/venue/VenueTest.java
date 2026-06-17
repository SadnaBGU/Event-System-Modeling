package com.eventsystem.domain.venue;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.VenueException;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.SeatId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.zone.ZoneType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class VenueTest {

    private Venue venue;
    private CompanyId companyId;

    @BeforeEach
    void setUp() {
        companyId = CompanyId.random();
        venue = new Venue(VenueId.generate(), companyId, "Main Venue");
    }

    @Test
    void constructor_rejects_invalid_arguments() {
        VenueId validId = VenueId.generate();
        CompanyId validCompanyId = CompanyId.random();

        assertThatThrownBy(() -> new Venue(null, validCompanyId, "Name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Venue(validId, null, "Name"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Venue(validId, validCompanyId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Venue(validId, validCompanyId, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }

    @Test
    void addZone_successfully_adds_zone_to_venue() {
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);

        venue.addZone(zone);

        assertThat(venue.getZones()).hasSize(1);
        assertThat(venue.getZones().get(0).getZoneName()).isEqualTo("Zone A");
    }

    @Test
    void addZone_throws_exception_for_null_zone() {
        assertThatThrownBy(() -> venue.addZone(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addZone_throws_exception_for_duplicate_zone_name() {
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone1 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        VenueZone zone2 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 50);

        venue.addZone(zone1);

        assertThatThrownBy(() -> venue.addZone(zone2))
                .isInstanceOf(VenueException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void removeZone_successfully_removes_zone_from_venue() {
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        venue.removeZone(zone.getZoneId());

        assertThat(venue.getZones()).isEmpty();
    }

    @Test
    void removeZone_throws_exception_if_not_found() {
        assertThatThrownBy(() -> venue.removeZone(ZoneId.random()))
                .isInstanceOf(VenueException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getZone_returns_correct_zone() {
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        VenueZone retrieved = venue.getZone(zone.getZoneId());

        assertThat(retrieved.getZoneName()).isEqualTo("Zone A");
        assertThat(retrieved.getTotalCapacity()).isEqualTo(100);
    }

    @Test
    void getZone_throws_exception_if_not_found() {
        assertThatThrownBy(() -> venue.getZone(ZoneId.random()))
                .isInstanceOf(VenueException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void total_aggregations_calculate_correctly() {
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone1 = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 10);
        VenueZone zone2 = new VenueZone(ZoneId.random(), "Zone B", ZoneType.STANDING, price, 20);
        venue.addZone(zone1);
        venue.addZone(zone2);

        // All seats are available initially
        assertThat(venue.getTotalCapacity()).isEqualTo(30);
        assertThat(venue.getTotalAvailableSeats()).isEqualTo(30);
        assertThat(venue.getTotalReservedSeats()).isZero();
        assertThat(venue.getTotalSoldSeats()).isZero();

        // Reserve and sell some
        SeatId seatId = zone1.getSeats().get(0).seatId();
        venue.reserveSeat(zone1.getZoneId(), seatId);
        
        assertThat(venue.getTotalReservedSeats()).isEqualTo(1);
        assertThat(venue.getTotalAvailableSeats()).isEqualTo(29);

        venue.markSeatSold(zone1.getZoneId(), seatId);
        
        assertThat(venue.getTotalSoldSeats()).isEqualTo(1);
        assertThat(venue.getTotalReservedSeats()).isZero(); // Sold isn't reserved anymore in this flow
    }

    @Test
    void seatLifecycle_delegates_to_zone_or_throws() {
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 5);
        venue.addZone(zone);
        SeatId seatId = zone.getSeats().get(0).seatId();

        // Release
        venue.reserveSeat(zone.getZoneId(), seatId);
        venue.releaseSeat(zone.getZoneId(), seatId);
        assertThat(zone.getAvailableCount()).isEqualTo(5);

        // Unknown zone operations throw
        assertThatThrownBy(() -> venue.reserveSeat(ZoneId.random(), seatId)).isInstanceOf(VenueException.class);
        assertThatThrownBy(() -> venue.releaseSeat(ZoneId.random(), seatId)).isInstanceOf(VenueException.class);
        assertThatThrownBy(() -> venue.markSeatSold(ZoneId.random(), seatId)).isInstanceOf(VenueException.class);
    }

    @Test
    void entity_overrides_and_getters_work_correctly() {
        VenueId id1 = VenueId.generate();
        Venue venue1 = new Venue(id1, companyId, "V1");
        Venue venue2 = new Venue(id1, companyId, "V2");
        Venue venue3 = new Venue(VenueId.generate(), companyId, "V3");

        assertThat(venue1.getVenueId()).isEqualTo(id1);
        assertThat(venue1.getId()).isEqualTo(id1);
        assertThat(venue1.getCompanyId()).isEqualTo(companyId);
        assertThat(venue1.getVenueName()).isEqualTo("V1");
        assertThat(venue1.getVersion()).isNull(); // Not persisted yet
        assertThat(venue1.isNew()).isTrue();
        
        assertThat(venue1).isEqualTo(venue2);
        assertThat(venue1.hashCode()).isEqualTo(venue2.hashCode());
        assertThat(venue1).isNotEqualTo(venue3);
        assertThat(venue1).isNotEqualTo(null);
        assertThat(venue1).isNotEqualTo(new Object());
        assertThat(venue1.toString()).contains("V1").contains(id1.toString());
    }
}