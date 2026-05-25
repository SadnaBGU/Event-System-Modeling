package com.eventsystem.application.event;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
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
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    private ZoneService service;
    private EventId eventId;
    private Money price;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; })
                .when(zoneRepository).withLock(any(), any());
        service = new ZoneService(zoneRepository);
        eventId = EventId.random();
        price = new Money(new BigDecimal("20.00"), "ILS");
    }

    // ── createStandingZone ───────────────────────────────────────────────────

    @Test
    void createStandingZone_savesZoneAndReturnsId() {
        ZoneId id = service.createStandingZone(eventId, "GA", price, 100);

        assertThat(id).isNotNull();
        verify(zoneRepository).save(any(Zone.class));
    }

    @Test
    void createStandingZone_zeroCapacity_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createStandingZone(eventId, "GA", price, 0));
        verify(zoneRepository, never()).save(any());
    }

    @Test
    void createStandingZone_nullEventId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createStandingZone(null, "GA", price, 100));
    }

    // ── createSeatedZone ─────────────────────────────────────────────────────

    @Test
    void createSeatedZone_savesZoneAndReturnsId() {
        Row row = new Row("A", List.of(new Seat(SeatId.random(), "A", 1)));
        ZoneId id = service.createSeatedZone(eventId, "VIP", price, List.of(row));

        assertThat(id).isNotNull();
        verify(zoneRepository).save(any(Zone.class));
    }

    @Test
    void createSeatedZone_emptyRows_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createSeatedZone(eventId, "VIP", price, List.of()));
        verify(zoneRepository, never()).save(any());
    }

    // ── reserveStanding ──────────────────────────────────────────────────────

    @Test
    void reserveStanding_delegatesToZoneAndSaves() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "GA", price, 50);
        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        service.reserveStanding(zone.zoneId(), 10);

        assertThat(zone.getAvailableCount()).isEqualTo(40);
        verify(zoneRepository).save(zone);
    }

    @Test
    void reserveStanding_zoneNotFound_throws() {
        ZoneId unknown = ZoneId.random();
        when(zoneRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.reserveStanding(unknown, 1));
    }

    @Test
    void reserveStanding_zeroQuantity_throws_beforeLoadingZone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.reserveStanding(ZoneId.random(), 0));
        verify(zoneRepository, never()).findById(any());
    }

    // ── releaseSeat ──────────────────────────────────────────────────────────

    @Test
    void releaseSeat_delegatesToZoneAndSaves() {
        SeatId seatId = SeatId.random();
        Seat seat = new Seat(seatId, "A", 1);
        Row row = new Row("A", List.of(seat));
        Zone zone = Zone.createSeated(ZoneId.random(), eventId, "VIP", price, List.of(row));
        zone.reserveSeat(seatId);
        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        service.releaseSeat(zone.zoneId(), seatId);

        assertThat(seat.status()).isEqualTo(SeatStatus.AVAILABLE);
        verify(zoneRepository).save(zone);
    }

    // ── markSeatSold ─────────────────────────────────────────────────────────

    @Test
    void markSeatSold_delegatesToZoneAndSaves() {
        SeatId seatId = SeatId.random();
        Seat seat = new Seat(seatId, "A", 1);
        Row row = new Row("A", List.of(seat));
        Zone zone = Zone.createSeated(ZoneId.random(), eventId, "VIP", price, List.of(row));
        zone.reserveSeat(seatId);
        when(zoneRepository.findById(zone.zoneId())).thenReturn(Optional.of(zone));

        service.markSeatSold(zone.zoneId(), seatId);

        assertThat(seat.status()).isEqualTo(SeatStatus.SOLD);
        verify(zoneRepository).save(zone);
    }

    // ── updateZoneName / updateZonePrice ─────────────────────────────────────

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

    // ── findByEvent ──────────────────────────────────────────────────────────

    @Test
    void findByEvent_delegatesToRepository() {
        Zone z1 = Zone.createStanding(ZoneId.random(), eventId, "GA", price, 50);
        when(zoneRepository.findByEventId(eventId)).thenReturn(List.of(z1));

        List<Zone> result = service.findByEvent(eventId);

        assertThat(result).hasSize(1).first().extracting(Zone::zoneName).isEqualTo("GA");
    }
}
