package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryZoneRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class InMemoryZoneRepositoryTest {

    private InMemoryZoneRepository repository;
    private EventId eventId;
    private Money price;

    @BeforeEach
    void setUp() {
        repository = new InMemoryZoneRepository();
        eventId = EventId.random();
        price = new Money(new BigDecimal("30.00"), "ILS");
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertThat(repository.findById(ZoneId.random())).isEmpty();
    }

    @Test
    void save_thenFindById_returnsZone() {
        Zone zone = Zone.createStanding(ZoneId.random(), eventId, "GA", price, 50);
        repository.save(zone);

        Optional<Zone> found = repository.findById(zone.zoneId());
        assertThat(found).isPresent();
        assertThat(found.get().zoneName()).isEqualTo("GA");
    }

    @Test
    void findByEventId_returnsOnlyZonesForThatEvent() {
        Zone z1 = Zone.createStanding(ZoneId.random(), eventId, "GA",  price, 50);
        Zone z2 = Zone.createStanding(ZoneId.random(), eventId, "VIP", price, 20);
        Zone z3 = Zone.createStanding(ZoneId.random(), EventId.random(), "Other", price, 10);
        repository.save(z1);
        repository.save(z2);
        repository.save(z3);

        List<Zone> zones = repository.findByEventId(eventId);
        assertThat(zones).hasSize(2)
                .extracting(Zone::zoneName)
                .containsExactlyInAnyOrder("GA", "VIP");
    }

    @Test
    void findByEventId_noZones_returnsEmptyList() {
        assertThat(repository.findByEventId(eventId)).isEmpty();
    }

    @Test
    void save_overwrites_existingZone() {
        ZoneId id = ZoneId.random();
        Zone zone = Zone.createStanding(id, eventId, "GA", price, 50);
        repository.save(zone);

        zone.reserveStanding(10);
        repository.save(zone);

        Zone reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getAvailableCount()).isEqualTo(40);
    }

    @Test
    void differentZones_storedIndependently() {
        Zone z1 = Zone.createStanding(ZoneId.random(), eventId, "Z1", price, 100);
        Zone z2 = Zone.createStanding(ZoneId.random(), eventId, "Z2", price, 200);
        repository.save(z1);
        repository.save(z2);

        assertThat(repository.findById(z1.zoneId()).orElseThrow().zoneName()).isEqualTo("Z1");
        assertThat(repository.findById(z2.zoneId()).orElseThrow().zoneName()).isEqualTo("Z2");
    }
}
