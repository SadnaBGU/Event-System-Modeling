package com.eventsystem.application.venue;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.VenueException;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.venue.*;
import com.eventsystem.domain.zone.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueManagementServiceTest {

    @Mock
    private IVenueRepository venueRepository;

    @Mock
    private IMemberRepository memberRepository;

    @Mock
    private ICompanyPermissionServicePort permissionChecker;

    private VenueManagementService venueManagementService;

    private MemberId actorId;
    private CompanyId companyId;
    private VenueId venueId;

    @BeforeEach
    void setUp() {
        venueManagementService = new VenueManagementService(
                venueRepository,
                memberRepository,
                permissionChecker);

        actorId = MemberId.random();
        companyId = CompanyId.random();
        venueId = VenueId.generate();

        lenient().when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(true);
    }

    @Test
    void constructor_rejects_null_dependencies() {
        assertThatThrownBy(() -> new VenueManagementService(null, memberRepository, permissionChecker))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new VenueManagementService(venueRepository, null, permissionChecker))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new VenueManagementService(venueRepository, memberRepository, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createVenue_successfully_creates_venue_whenActorHasPermission() {
        String venueName = "Main Venue";

        Venue venue = venueManagementService.createVenue(actorId, companyId, venueName);

        assertThat(venue).isNotNull();
        assertThat(venue.getVenueName()).isEqualTo(venueName);
        assertThat(venue.getCompanyId()).isEqualTo(companyId);
        verify(venueRepository).save(venue);
        verify(permissionChecker).canConfigureVenue(actorId, companyId);
    }

    @Test
    void createVenue_denies_actor_without_permission() {
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.createVenue(actorId, companyId, "Main Venue"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed");

        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    void createVenue_rejects_null_inputs() {
        assertThatThrownBy(() -> venueManagementService.createVenue(null, companyId, "Main Venue"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> venueManagementService.createVenue(actorId, null, "Main Venue"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");

        assertThatThrownBy(() -> venueManagementService.createVenue(actorId, companyId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void addSeatedZone_successfully_adds_zone_to_venue_whenActorHasPermission() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        venueManagementService.addSeatedZone(
                actorId,
                venueId,
                "Zone A",
                BigDecimal.valueOf(50),
                "USD",
                100);

        assertThat(venue.getZones()).hasSize(1);
        assertThat(venue.getZones().get(0).getZoneName()).isEqualTo("Zone A");
        assertThat(venue.getZones().get(0).getZoneType()).isEqualTo(ZoneType.SEATED);
        verify(venueRepository).save(venue);
    }

    @Test
    void addStandingZone_successfully_adds_standing_zone_whenActorHasPermission() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        venueManagementService.addStandingZone(
                actorId,
                venueId,
                "Standing",
                BigDecimal.valueOf(30),
                "USD",
                200);

        assertThat(venue.getZones()).hasSize(1);
        assertThat(venue.getZones().get(0).getZoneType()).isEqualTo(ZoneType.STANDING);
        assertThat(venue.getZones().get(0).getTotalCapacity()).isEqualTo(200);
        verify(venueRepository).save(venue);
    }

    @Test
    void addStandingZone_denies_actor_without_permission() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.addStandingZone(
                actorId,
                venueId,
                "Standing",
                BigDecimal.valueOf(30),
                "USD",
                200)).isInstanceOf(SecurityException.class);

        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    void addStandingZone_throws_exception_for_missing_venue() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.addStandingZone(
                actorId,
                venueId,
                "Standing",
                BigDecimal.valueOf(30),
                "USD",
                200)).isInstanceOf(VenueException.class);
    }

    @Test
    void addZone_rejects_null_inputs() {
        assertThatThrownBy(
                () -> venueManagementService.addSeatedZone(null, venueId, "Zone A", BigDecimal.TEN, "USD", 10))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(
                () -> venueManagementService.addSeatedZone(actorId, null, "Zone A", BigDecimal.TEN, "USD", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> venueManagementService.addSeatedZone(actorId, venueId, null, BigDecimal.TEN, "USD", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.addSeatedZone(actorId, venueId, "Zone A", null, "USD", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> venueManagementService.addSeatedZone(actorId, venueId, "Zone A", BigDecimal.TEN, null, 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> venueManagementService.addStandingZone(actorId, null, "Zone B", BigDecimal.TEN, "USD", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> venueManagementService.addStandingZone(actorId, venueId, null, BigDecimal.TEN, "USD", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.addStandingZone(actorId, venueId, "Zone B", null, "USD", 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> venueManagementService.addStandingZone(actorId, venueId, "Zone B", BigDecimal.TEN, null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addSeatedZone_throws_exception_for_missing_venue() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.addSeatedZone(
                actorId,
                venueId,
                "Zone A",
                BigDecimal.valueOf(50),
                "USD",
                100)).isInstanceOf(VenueException.class);
    }

    @Test
    void removeZone_successfully_removes_zone_whenActorHasPermission() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        venueManagementService.removeZone(actorId, venueId, zone.getZoneId());

        assertThat(venue.getZones()).isEmpty();
        verify(venueRepository).save(venue);
    }

    @Test
    void removeZone_denies_actor_without_permission() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.removeZone(actorId, venueId, zone.getZoneId()))
                .isInstanceOf(SecurityException.class);

        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    void removeZone_rejects_null_inputs() {
        assertThatThrownBy(() -> venueManagementService.removeZone(null, venueId, ZoneId.random()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> venueManagementService.removeZone(actorId, null, ZoneId.random()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.removeZone(actorId, venueId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeZone_missingVenue_throwsVenueException() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.removeZone(actorId, venueId, ZoneId.random()))
                .isInstanceOf(VenueException.class);
    }

    @Test
    void reserveSeat_successfully_reserves_seat() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);
        Seat seat = zone.getSeats().get(0);
        SeatId seatId = seat.seatId();

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        venueManagementService.reserveSeat(venueId, zone.getZoneId(), seatId);

        assertThat(zone.getReservedCount()).isEqualTo(1);
        assertThat(zone.getAvailableCount()).isEqualTo(99);
        verify(venueRepository).save(venue);
    }

    @Test
    void releaseSeat_and_markSeatSold_update_zone_state() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 10);
        venue.addZone(zone);
        SeatId seatId = zone.getSeats().get(0).seatId();

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        venueManagementService.reserveSeat(venueId, zone.getZoneId(), seatId);
        venueManagementService.releaseSeat(venueId, zone.getZoneId(), seatId);

        assertThat(zone.getReservedCount()).isZero();
        assertThat(zone.getAvailableCount()).isEqualTo(10);

        venueManagementService.reserveSeat(venueId, zone.getZoneId(), seatId);
        venueManagementService.markSeatSold(venueId, zone.getZoneId(), seatId);

        assertThat(zone.getSoldCount()).isEqualTo(1);
    }

    @Test
    void seatOperations_reject_null_inputs() {
        ZoneId zoneId = ZoneId.random();
        SeatId seatId = SeatId.random();

        assertThatThrownBy(() -> venueManagementService.reserveSeat(null, zoneId, seatId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.reserveSeat(venueId, null, seatId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.reserveSeat(venueId, zoneId, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.releaseSeat(null, zoneId, seatId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.markSeatSold(null, zoneId, seatId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getZone_success_and_validation_paths() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.random(), "Zone A", ZoneType.SEATED, price, 20);
        venue.addZone(zone);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        VenueZone found = venueManagementService.getZone(venueId, zone.getZoneId());

        assertThat(found).isEqualTo(zone);

        assertThatThrownBy(() -> venueManagementService.getZone(null, zone.getZoneId()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> venueManagementService.getZone(venueId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getVenue_returns_venue_when_exists() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        Venue retrieved = venueManagementService.getVenue(venueId);

        assertThat(retrieved).isEqualTo(venue);
    }

    @Test
    void getVenue_throws_exception_when_not_found() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> venueManagementService.getVenue(venueId))
                .isInstanceOf(VenueException.class);
    }

    @Test
    void getVenue_rejects_null_venueId() {
        assertThatThrownBy(() -> venueManagementService.getVenue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCompanyVenues_delegates_and_validates_input() {
        Venue v1 = new Venue(VenueId.generate(), companyId, "A");
        Venue v2 = new Venue(VenueId.generate(), companyId, "B");

        when(venueRepository.findByCompanyId(companyId)).thenReturn(List.of(v1, v2));

        List<Venue> venues = venueManagementService.getCompanyVenues(companyId);

        assertThat(venues).containsExactly(v1, v2);

        assertThatThrownBy(() -> venueManagementService.getCompanyVenues(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createVenue_deniesActorWithOnlyEventManagementPermission_ifVenueRequiresVenueConfiguration() {
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.createVenue(actorId, companyId, "Main Venue"))
                .isInstanceOf(SecurityException.class);

        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    void addSeatedZone_denies_actor_without_permission() {
        Venue venue = new Venue(venueId, companyId, "Main Venue");

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> venueManagementService.addSeatedZone(
                actorId,
                venueId,
                "Zone A",
                BigDecimal.valueOf(50),
                "USD",
                100)).isInstanceOf(SecurityException.class);

        verify(venueRepository, never()).save(any(Venue.class));
    }
}