package com.eventsystem.domain.zone;

import com.eventsystem.domain.domainexceptions.ZoneDomainException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class ZoneStandingTest {

    private Zone zone;

    @BeforeEach
    void setUp() {
        zone = Zone.createStanding(
                ZoneId.random(),
                EventId.random(),
                "General Admission",
                new Money(new BigDecimal("25.00"), "ILS"),
                100
        );
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    void initialAvailableCount_equalsCapacity() {
        assertThat(zone.getAvailableCount()).isEqualTo(100);
        assertThat(zone.totalCapacity()).isEqualTo(100);
    }

    @Test
    void standingZone_hasNoRows() {
        assertThat(zone.rows()).isEmpty();
    }

    // ── reserveStanding ──────────────────────────────────────────────────────

    @Test
    void reserveStanding_decrementsAvailableCount() {
        zone.reserveStanding(10);
        assertThat(zone.getAvailableCount()).isEqualTo(90);
    }

    @Test
    void reserveStanding_exactlyAvailable_succeeds() {
        zone.reserveStanding(100);
        assertThat(zone.getAvailableCount()).isZero();
    }

    @Test
    void reserveStanding_exceedsAvailable_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveStanding(101));
    }

    @Test
    void reserveStanding_partialThenExceed_throws() {
        zone.reserveStanding(90);
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveStanding(11));
    }

    @Test
    void reserveStanding_zeroQuantity_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveStanding(0));
    }

    @Test
    void reserveStanding_negativeQuantity_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveStanding(-5));
    }

    // ── releaseStanding ──────────────────────────────────────────────────────

    @Test
    void releaseStanding_incrementsAvailableCount() {
        zone.reserveStanding(20);
        zone.releaseStanding(20);
        assertThat(zone.getAvailableCount()).isEqualTo(100);
    }

    @Test
    void releaseStanding_partial_increasesCount() {
        zone.reserveStanding(30);
        zone.releaseStanding(10);
        assertThat(zone.getAvailableCount()).isEqualTo(80);
    }

    @Test
    void releaseStanding_withoutPriorReserve_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.releaseStanding(1));
    }

    @Test
    void releaseStanding_exceedsTotalCapacity_throws() {
        zone.reserveStanding(5);
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.releaseStanding(6));
    }

    // ── markSoldStanding ─────────────────────────────────────────────────────

    @Test
    void markSoldStanding_doesNotChangeAvailableCount() {
        zone.reserveStanding(5);
        int countAfterReserve = zone.getAvailableCount();
        zone.markSoldStanding(5);
        assertThat(zone.getAvailableCount()).isEqualTo(countAfterReserve);
    }

    @Test
    void markSoldStanding_zeroQuantity_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.markSoldStanding(0));
    }

    // ── Wrong zone type ──────────────────────────────────────────────────────

    @Test
    void standingZone_seatedOperations_throw() {
        SeatId seatId = SeatId.random();
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.reserveSeat(seatId));
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.releaseSeat(seatId));
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> zone.markSold(seatId));
    }

    // ── Factory guards ───────────────────────────────────────────────────────

    @Test
    void createStanding_zeroCapacity_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> Zone.createStanding(
                        ZoneId.random(), EventId.random(), "Zone",
                        new Money(BigDecimal.TEN, "ILS"), 0));
    }

    @Test
    void createStanding_negativeCapacity_throws() {
        assertThatExceptionOfType(ZoneDomainException.class)
                .isThrownBy(() -> Zone.createStanding(
                        ZoneId.random(), EventId.random(), "Zone",
                        new Money(BigDecimal.TEN, "ILS"), -1));
    }

    // ── Version increments ───────────────────────────────────────────────────

    @Test
    void version_incrementsOnEachOperation() {
        assertThat(zone.version()).isZero();
        zone.reserveStanding(10);
        assertThat(zone.version()).isEqualTo(1);
        zone.releaseStanding(10);
        assertThat(zone.version()).isEqualTo(2);
        zone.reserveStanding(5);
        zone.markSoldStanding(5);
        assertThat(zone.version()).isEqualTo(4);
    }
}
