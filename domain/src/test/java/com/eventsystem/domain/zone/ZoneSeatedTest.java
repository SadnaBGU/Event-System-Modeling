package com.eventsystem.domain.zone;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ZoneSeatedTest {

    private SeatId seatA;
    private SeatId seatB;
    private Zone zone;

    @BeforeEach
    void setUp() {
        seatA = SeatId.random();
        seatB = SeatId.random();
        Seat s1 = new Seat(seatA, "A", 1);
        Seat s2 = new Seat(seatB, "A", 2);
        Row rowA = new Row("A", List.of(s1, s2));
        zone = Zone.createSeated(
                ZoneId.random(),
                EventId.random(),
                "VIP",
                new Money(new BigDecimal("50.00"), "ILS"),
                List.of(rowA)
        );
    }

    // ── Capacity ─────────────────────────────────────────────────────────────

    @Test
    void initialAvailableCount_equalsTotalCapacity() {
        assertThat(zone.getAvailableCount()).isEqualTo(2);
        assertThat(zone.totalCapacity()).isEqualTo(2);
    }

    // ── reserveSeat ──────────────────────────────────────────────────────────

    @Test
    void reserveSeat_changesSeatStatusToReserved() {
        zone.reserveSeat(seatA);
        Seat seat = zone.rows().get(0).seats().get(0);
        assertThat(seat.status()).isEqualTo(SeatStatus.RESERVED);
    }

    @Test
    void reserveSeat_decrementsAvailableCount() {
        zone.reserveSeat(seatA);
        assertThat(zone.getAvailableCount()).isEqualTo(1);
    }


    @Test
    void reserveSeat_alreadyReserved_throws() {
        zone.reserveSeat(seatA);
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveSeat(seatA));
    }

    @Test
    void reserveSeat_alreadySold_throws() {
        zone.reserveSeat(seatA);
        zone.markSold(seatA);
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveSeat(seatA));
    }

    @Test
    void reserveSeat_seatNotFound_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveSeat(SeatId.random()));
    }

    // ── releaseSeat ──────────────────────────────────────────────────────────

    @Test
    void releaseSeat_restoresAvailableCount() {
        zone.reserveSeat(seatA);
        zone.releaseSeat(seatA);
        assertThat(zone.getAvailableCount()).isEqualTo(2);
    }

    @Test
    void releaseSeat_changesSeatStatusToAvailable() {
        zone.reserveSeat(seatA);
        zone.releaseSeat(seatA);
        assertThat(zone.rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void releaseSeat_notReserved_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.releaseSeat(seatA));
    }

    // ── markSold ─────────────────────────────────────────────────────────────

    @Test
    void markSold_changesSeatStatusToSold() {
        zone.reserveSeat(seatA);
        zone.markSold(seatA);
        assertThat(zone.rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    void markSold_doesNotChangeAvailableCount() {
        zone.reserveSeat(seatA);
        int countAfterReserve = zone.getAvailableCount();
        zone.markSold(seatA);
        assertThat(zone.getAvailableCount()).isEqualTo(countAfterReserve);
    }

    @Test
    void markSold_notReserved_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.markSold(seatA));
    }

    // ── Wrong zone type ──────────────────────────────────────────────────────

    @Test
    void seatedZone_standingOperations_throw() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveStanding(1));
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.releaseStanding(1));
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.markSoldStanding(1));
    }

    // ── Factory guards ───────────────────────────────────────────────────────

    @Test
    void createSeated_emptyRows_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> Zone.createSeated(
                        ZoneId.random(), EventId.random(), "Zone",
                        new Money(BigDecimal.TEN, "ILS"), List.of()));
    }
}
