package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresZoneRepository;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest 
@EntityScan(basePackages = "com.eventsystem.domain") 
@Import(PostgresZoneRepository.class) 
class PostgresZoneRepositoryTest {

    @Autowired
    private PostgresZoneRepository zoneRepository;

    @Autowired
    private EntityManager em;

    private Zone seatedZone;

    @BeforeEach
    void setUp() {
        // Arrange
        Seat seat1 = new Seat(new SeatId("S1"), "A", 1);
        Seat seat2 = new Seat(new SeatId("S2"), "A", 2);
        Row rowA = new Row("A", List.of(seat1, seat2));

        seatedZone = Zone.createSeated(
                new ZoneId("Z-VIP"),
                new EventId("E-123"),
                "VIP Area",
                new Money(new BigDecimal("150.00"), "ILS"),
                List.of(rowA)
        );
    }

    @Test
    void saveAndFindById_savesZoneAndReconstructsRows() {
        // Arrange
        zoneRepository.save(seatedZone);

        em.flush();
        em.clear();

        // Act
        Optional<Zone> foundZoneOpt = zoneRepository.findById(new ZoneId("Z-VIP"));

        // Assert
        assertThat(foundZoneOpt).isPresent();
        Zone foundZone = foundZoneOpt.get();
        
        assertThat(foundZone.zoneName()).isEqualTo("VIP Area");
        assertThat(foundZone.eventId().value()).isEqualTo("E-123");
        
        assertThat(foundZone.rows()).hasSize(1);
        assertThat(foundZone.rows().get(0).rowLabel()).isEqualTo("A");
        assertThat(foundZone.rows().get(0).seats()).hasSize(2);
    }

    @Test
    void findByEventId_returnsAllZonesForGivenEvent() {
        // Arrange
        zoneRepository.save(seatedZone);
        
        Zone standingZone = Zone.createStanding(
                new ZoneId("Z-GRASS"),
                new EventId("E-123"),
                "Grass Area",
                new Money(new BigDecimal("80.00"), "ILS"),
                500
        );
        zoneRepository.save(standingZone);

        // Act
        List<Zone> zones = zoneRepository.findByEventId(new EventId("E-123"));

        // Assert
        assertThat(zones).hasSize(2);
        assertThat(zones)
                .extracting(z -> z.zoneId().value())
                .containsExactlyInAnyOrder("Z-VIP", "Z-GRASS");
    }

    @Test
    void withLock_executesActionSuccessfully() {
        // Arrange
        zoneRepository.save(seatedZone);
        ZoneId id = new ZoneId("Z-VIP");
        final boolean[] actionExecuted = {false};

        // Act
        // Checking that the locking query executes successfully without SQL errors
        zoneRepository.withLock(id, () -> {
            actionExecuted[0] = true;
            Zone zone = zoneRepository.findById(id).orElseThrow();
            zone.reserveSeat(new SeatId("S1"));
            zoneRepository.save(zone);
        });

        // Assert
        assertThat(actionExecuted[0]).isTrue();
        Zone updatedZone = zoneRepository.findById(id).orElseThrow();
        assertThat(updatedZone.getAvailableCount()).isEqualTo(1); // 2 seats total - 1 reserved
    }
}