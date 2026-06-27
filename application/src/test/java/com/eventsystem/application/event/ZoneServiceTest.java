package com.eventsystem.application.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Transactional
class ZoneServiceTest {

    @Mock
    private IZoneRepository zoneRepository;

    @Mock
    private ICompanyPermissionServicePort permissionChecker;

    @Mock
    private IEventManagementPort eventOwnershipChecker;

    private ZoneService service;

    private MemberId actorId;
    private CompanyId companyId;
    private EventId eventId;
    private Money price;

    @BeforeEach
    void setUp() {
        service = new ZoneService(zoneRepository, permissionChecker, eventOwnershipChecker);

        actorId = MemberId.random();
        companyId = CompanyId.random();
        eventId = new EventId("EV-1");
        price = new Money(new BigDecimal("100.00"), "USD");

        lenient().when(eventOwnershipChecker.companyOfEvent(eventId)).thenReturn(companyId);
        lenient().when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(true);
        lenient().when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(false);

        // Makes withLock immediately run the Runnable body.
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(zoneRepository).withLock(any(ZoneId.class), any(Runnable.class));
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new ZoneService(null, permissionChecker, eventOwnershipChecker))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ZoneService(zoneRepository, null, eventOwnershipChecker))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new ZoneService(zoneRepository, permissionChecker, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ==========================================
    // Creation
    // ==========================================

    @Test
    void createSeatedZone_savesAndReturnsId_whenActorHasVenuePermission() {
        Row row = new Row("A", List.of(new Seat(SeatId.random(), "A", 1)));

        ZoneId id = service.createSeatedZone(actorId, eventId, "VIP", price, List.of(row));

        assertThat(id).isNotNull();

        ArgumentCaptor<Zone> captor = ArgumentCaptor.forClass(Zone.class);
        verify(zoneRepository).save(captor.capture());
        assertThat(captor.getValue().zoneType()).isEqualTo(ZoneType.SEATED);
        verify(permissionChecker).canConfigureVenue(actorId, companyId);
    }

    @Test
    void createStandingZone_savesAndReturnsId_whenActorHasVenuePermission() {
        ZoneId id = service.createStandingZone(actorId, eventId, "GA", price, 500);

        assertThat(id).isNotNull();

        ArgumentCaptor<Zone> captor = ArgumentCaptor.forClass(Zone.class);
        verify(zoneRepository).save(captor.capture());
        assertThat(captor.getValue().zoneType()).isEqualTo(ZoneType.STANDING);
        verify(permissionChecker).canConfigureVenue(actorId, companyId);
    }

    @Test
    void createStandingZone_allowsActorWithEventManagementPermission() {
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);
        when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(true);

        ZoneId id = service.createStandingZone(actorId, eventId, "GA", price, 500);

        assertThat(id).isNotNull();
        verify(zoneRepository).save(any(Zone.class));
    }

    @Test
    void createStandingZone_deniesActorWithoutVenueOrEventPermission() {
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);
        when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.createStandingZone(actorId, eventId, "GA", price, 500))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed");

        verify(zoneRepository, never()).save(any(Zone.class));
    }

    @Test
    void createZones_validateNullsAndNegatives() {
        assertThatThrownBy(() -> service.createSeatedZone(null, eventId, "VIP", price, List.of()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.createSeatedZone(actorId, null, "VIP", price, List.of()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.createSeatedZone(actorId, eventId, null, price, List.of()))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.createStandingZone(null, eventId, "GA", price, 100))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.createStandingZone(actorId, null, "GA", price, 100))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.createStandingZone(actorId, eventId, null, price, 100))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.createStandingZone(actorId, eventId, "GA", price, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==========================================
    // Seated Operations (withLock)
    // ==========================================

    @Test
    void seatedOperations_delegateToZoneAndSave() {
        Zone mockZone = mock(Zone.class);
        ZoneId zoneId = ZoneId.random();
        SeatId seatId = SeatId.random();

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(mockZone));
        when(mockZone.pricePerTicket()).thenReturn(price);

        service.reserveSeat(zoneId, seatId);
        verify(mockZone).reserveSeat(seatId);

        service.releaseSeat(zoneId, seatId);
        verify(mockZone).releaseSeat(seatId);

        service.markSeatSold(zoneId, seatId);
        verify(mockZone).markSold(seatId);

        verify(zoneRepository, times(3)).save(mockZone);
    }

    // ==========================================
    // Standing Operations (withLock)
    // ==========================================

    @Test
    void standingOperations_delegateToZoneAndSave() {
        Zone mockZone = mock(Zone.class);
        ZoneId zoneId = ZoneId.random();

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(mockZone));

        service.reserveStanding(zoneId, 5);
        verify(mockZone).reserveStanding(5);

        service.releaseStanding(zoneId, 5);
        verify(mockZone).releaseStanding(5);

        service.markStandingSold(zoneId, 5);
        verify(mockZone).markSoldStanding(5);

        verify(zoneRepository, times(3)).save(mockZone);
    }

    @Test
    void standingOperations_validateNegatives() {
        ZoneId zoneId = ZoneId.random();

        assertThatThrownBy(() -> service.reserveStanding(zoneId, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.releaseStanding(zoneId, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.markStandingSold(zoneId, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.reserveStanding(null, 5))
                .isInstanceOf(NullPointerException.class);
    }

    // ==========================================
    // Updates & Queries
    // ==========================================

    @Test
    void updateZoneName_delegatesToZoneAndSaves_whenActorHasPermission() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "Old", price, 10);

        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        service.updateZoneName(actorId, zone.zoneId(), "New");

        assertThat(zone.zoneName()).isEqualTo("New");
        verify(zoneRepository).save(zone);
    }

    @Test
    void updateZonePrice_delegatesToZoneAndSaves_whenActorHasPermission() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "GA", price, 10);
        Money newPrice = new Money(new BigDecimal("99.00"), "ILS");

        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        service.updateZonePrice(actorId, zone.zoneId(), newPrice);

        assertThat(zone.pricePerTicket()).isEqualTo(newPrice);
        verify(zoneRepository).save(zone);
    }

    @Test
    void updateZoneName_deniesActorWithoutPermission() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "Old", price, 10);

        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);
        when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateZoneName(actorId, zone.zoneId(), "New"))
                .isInstanceOf(SecurityException.class);

        verify(zoneRepository, never()).save(any(Zone.class));
    }

    @Test
    void findById_delegatesToRepository() {
        Zone zone = mock(Zone.class);
        ZoneId zoneId = ZoneId.random();

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));
        assertThat(service.findById(zoneId)).isEqualTo(zone);

        when(zoneRepository.findById(zoneId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(zoneId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findByEvent_delegatesToRepository() {
        Zone zone = mock(Zone.class);

        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(zone));

        assertThat(service.findByEvent(eventId)).containsExactly(zone);

        assertThatThrownBy(() -> service.findByEvent(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void createSeatedZone_deniesActorWithoutVenueOrEventPermission() {
        Row row = new Row("A", List.of(new Seat(SeatId.random(), "A", 1)));

        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);
        when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.createSeatedZone(actorId, eventId, "VIP", price, List.of(row)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed");

        verify(zoneRepository, never()).save(any(Zone.class));
    }

    @Test
    void createSeatedZone_allowsActorWithEventManagementPermission() {
        Row row = new Row("A", List.of(new Seat(SeatId.random(), "A", 1)));

        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);
        when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(true);

        ZoneId id = service.createSeatedZone(actorId, eventId, "VIP", price, List.of(row));

        assertThat(id).isNotNull();
        verify(zoneRepository).save(any(Zone.class));
    }

    @Test
    void updateZonePrice_deniesActorWithoutPermission() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "GA", price, 10);
        Money newPrice = new Money(new BigDecimal("120.00"), "USD");

        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));
        when(permissionChecker.canConfigureVenue(actorId, companyId)).thenReturn(false);
        when(permissionChecker.canManageEvents(actorId, companyId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateZonePrice(actorId, zone.zoneId(), newPrice))
                .isInstanceOf(SecurityException.class);

        verify(zoneRepository, never()).save(any(Zone.class));
    }

    @Test
    void updateZoneName_rejectsNullInputs() {
        ZoneId zoneId = ZoneId.random();

        assertThatThrownBy(() -> service.updateZoneName(null, zoneId, "New"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.updateZoneName(actorId, null, "New"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.updateZoneName(actorId, zoneId, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updateZonePrice_rejectsNullInputs() {
        ZoneId zoneId = ZoneId.random();

        assertThatThrownBy(() -> service.updateZonePrice(null, zoneId, price))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.updateZonePrice(actorId, null, price))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.updateZonePrice(actorId, zoneId, null))
                .isInstanceOf(NullPointerException.class);
    }
}