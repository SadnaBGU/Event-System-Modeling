package com.eventsystem.application.event;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @InjectMocks
    private ZoneService service;

    private EventId eventId;
    private Money price;

    @BeforeEach
    void setUp() {
        eventId = new EventId("EV-1");
        price = new Money(new BigDecimal("100.00"), "USD");
        
        // גורם לפונקציית withLock להריץ מיד את הלולאה הפנימית שבתוכה (ה-Runnable)
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(zoneRepository).withLock(any(ZoneId.class), any(Runnable.class));
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new ZoneService(null)).isInstanceOf(NullPointerException.class);
    }

    // ==========================================
    // Creation
    // ==========================================

    @Test
    void createSeatedZone_savesAndReturnsId() {
        Row row = new Row("A", List.of(new Seat(SeatId.random(), "A", 1)));
        
        ZoneId id = service.createSeatedZone(eventId, "VIP", price, List.of(row));
        
        assertThat(id).isNotNull();
        ArgumentCaptor<Zone> captor = ArgumentCaptor.forClass(Zone.class);
        verify(zoneRepository).save(captor.capture());
        assertThat(captor.getValue().zoneType()).isEqualTo(ZoneType.SEATED);
    }

    @Test
    void createStandingZone_savesAndReturnsId() {
        ZoneId id = service.createStandingZone(eventId, "GA", price, 500);
        
        assertThat(id).isNotNull();
        ArgumentCaptor<Zone> captor = ArgumentCaptor.forClass(Zone.class);
        verify(zoneRepository).save(captor.capture());
        assertThat(captor.getValue().zoneType()).isEqualTo(ZoneType.STANDING);
    }

    @Test
    void createZones_validateNullsAndNegatives() {
        assertThatThrownBy(() -> service.createSeatedZone(null, "VIP", price, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createSeatedZone(eventId, null, price, List.of()))
                .isInstanceOf(NullPointerException.class);
                
        assertThatThrownBy(() -> service.createStandingZone(null, "GA", price, 100))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createStandingZone(eventId, null, price, 100))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createStandingZone(eventId, "GA", price, 0))
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
        assertThatThrownBy(() -> service.reserveStanding(zoneId, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.releaseStanding(zoneId, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.markStandingSold(zoneId, 0)).isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> service.reserveStanding(null, 5)).isInstanceOf(NullPointerException.class);
    }

    // ==========================================
    // Updates & Queries
    // ==========================================

    @Test
    void updateZoneName_delegatesToZoneAndSaves() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "Old", price, 10);
        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        service.updateZoneName(zone.zoneId(), "New");

        assertThat(zone.zoneName()).isEqualTo("New");
        verify(zoneRepository).save(zone);
    }

    @Test
    void updateZonePrice_delegatesToZoneAndSaves() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "GA", price, 10);
        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        Money newPrice = new Money(new BigDecimal("99.00"), "ILS");
        service.updateZonePrice(zone.zoneId(), newPrice);

        assertThat(zone.pricePerTicket()).isEqualTo(newPrice);
        verify(zoneRepository).save(zone);
    }

    @Test
    void findById_delegatesToRepository() {
        Zone zone = mock(Zone.class);
        ZoneId zoneId = ZoneId.random();
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.of(zone));

        assertThat(service.findById(zoneId)).isEqualTo(zone);
        
        when(zoneRepository.findById(zoneId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(zoneId)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void findByEvent_delegatesToRepository() {
        Zone zone = mock(Zone.class);
        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(zone));

        assertThat(service.findByEvent(eventId)).containsExactly(zone);
        assertThatThrownBy(() -> service.findByEvent(null)).isInstanceOf(NullPointerException.class);
    }
}