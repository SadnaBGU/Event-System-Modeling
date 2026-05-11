package com.eventsystem.application.venue;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
import com.eventsystem.domain.venue.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueManagementServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private MemberRepository memberRepository;

    private VenueManagementService venueManagementService;
    private CompanyId companyId;
    private VenueId venueId;

    @BeforeEach
    void setUp() {
        venueManagementService = new VenueManagementService(venueRepository, memberRepository);
        companyId = CompanyId.random();
        venueId = VenueId.generate();
    }

    @Test
    void createVenue_successfully_creates_venue() {
        // Given
        String venueName = "Main Venue";

        // When
        Venue venue = venueManagementService.createVenue(companyId, venueName);

        // Then
        assertThat(venue).isNotNull();
        assertThat(venue.getVenueName()).isEqualTo(venueName);
        assertThat(venue.getCompanyId()).isEqualTo(companyId);
        verify(venueRepository).save(venue);
    }

    @Test
    void addSeatedZone_successfully_adds_zone_to_venue() {
        // Given
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        // When
        venueManagementService.addSeatedZone(
                venueId,
                "Zone A",
                BigDecimal.valueOf(50),
                "USD",
                100
        );

        // Then
        assertThat(venue.getZones()).hasSize(1);
        assertThat(venue.getZones().get(0).getZoneName()).isEqualTo("Zone A");
        assertThat(venue.getZones().get(0).getZoneType()).isEqualTo(ZoneType.SEATED);
        verify(venueRepository).save(venue);
    }

    @Test
    void addStandingZone_successfully_adds_standing_zone() {
        // Given
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        // When
        venueManagementService.addStandingZone(
                venueId,
                "Standing",
                BigDecimal.valueOf(30),
                "USD",
                200
        );

        // Then
        assertThat(venue.getZones()).hasSize(1);
        assertThat(venue.getZones().get(0).getZoneType()).isEqualTo(ZoneType.STANDING);
        assertThat(venue.getZones().get(0).getTotalCapacity()).isEqualTo(200);
    }

    @Test
    void addSeatedZone_throws_exception_for_missing_venue() {
        // Given
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
                venueManagementService.addSeatedZone(
                        venueId,
                        "Zone A",
                        BigDecimal.valueOf(50),
                        "USD",
                        100
                )
        ).isInstanceOf(VenueException.class);
    }

    @Test
    void removeZone_successfully_removes_zone() {
        // Given
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.generate(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        // When
        venueManagementService.removeZone(venueId, zone.getZoneId());

        // Then
        assertThat(venue.getZones()).isEmpty();
        verify(venueRepository).save(venue);
    }

    @Test
    void reserveSeat_successfully_reserves_seat() {
        // Given
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        Money price = new Money(BigDecimal.valueOf(50), "USD");
        VenueZone zone = new VenueZone(ZoneId.generate(), "Zone A", ZoneType.SEATED, price, 100);
        venue.addZone(zone);
        Seat seat = zone.getSeats().get(0);
        SeatId seatId = seat.getSeatId();

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        // When
        venueManagementService.reserveSeat(venueId, zone.getZoneId(), seatId);

        // Then
        assertThat(zone.getReservedCount()).isEqualTo(1);
        assertThat(zone.getAvailableCount()).isEqualTo(99);
        verify(venueRepository).save(venue);
    }

    @Test
    void getVenue_returns_venue_when_exists() {
        // Given
        Venue venue = new Venue(venueId, companyId, "Main Venue");
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));

        // When
        Venue retrieved = venueManagementService.getVenue(venueId);

        // Then
        assertThat(retrieved).isEqualTo(venue);
    }

    @Test
    void getVenue_throws_exception_when_not_found() {
        // Given
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> venueManagementService.getVenue(venueId))
                .isInstanceOf(VenueException.class);
    }
}
