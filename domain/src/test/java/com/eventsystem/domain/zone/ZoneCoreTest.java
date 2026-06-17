package com.eventsystem.domain.zone;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZoneCoreTest {

    private final Money PRICE = new Money(BigDecimal.TEN, "USD");
    private final EventId EVENT_ID = new EventId("ev-1");

    @Test
    void jpaConstructor_createsEmptyInstance() throws Exception {
        java.lang.reflect.Constructor<Zone> c = Zone.class.getDeclaredConstructor();
        c.setAccessible(true);
        Zone zone = c.newInstance();
        
        assertThat(zone.zoneId()).isNull();
        assertThat(zone.eventId()).isNull();
        assertThat(zone.zoneName()).isEmpty();
        assertThat(zone.zoneType()).isNull();
        assertThat(zone.pricePerTicket()).isNull();
        assertThat(zone.totalCapacity()).isZero();
    }

    @Test
    void constructorValidations_viaFactoryMethods() {
        ZoneId id = ZoneId.random();
        Row row = new Row("A", List.of(new Seat(SeatId.random(), "A", 1)));

        // Null ZoneId
        assertThatThrownBy(() -> Zone.createSeated(null, EVENT_ID, "Name", PRICE, List.of(row)))
                .isInstanceOf(NullPointerException.class);
        // Null EventId
        assertThatThrownBy(() -> Zone.createSeated(id, null, "Name", PRICE, List.of(row)))
                .isInstanceOf(NullPointerException.class);
        // Blank Name
        assertThatThrownBy(() -> Zone.createSeated(id, EVENT_ID, "  ", PRICE, List.of(row)))
                .isInstanceOf(IllegalArgumentException.class);
        // Null Price
        assertThatThrownBy(() -> Zone.createSeated(id, EVENT_ID, "Name", null, List.of(row)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void updateName_and_updatePrice() {
        Zone zone = Zone.createStanding(ZoneId.random(), EVENT_ID, "OldName", PRICE, 10);
        
        zone.updateName("NewName");
        assertThat(zone.zoneName()).isEqualTo("NewName");
        
        Money newPrice = new Money(BigDecimal.ONE, "USD");
        zone.updatePrice(newPrice);
        assertThat(zone.pricePerTicket()).isEqualTo(newPrice);

        // Validations
        assertThatThrownBy(() -> zone.updateName("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> zone.updateName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> zone.updatePrice(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void version_getter_returnsCorrectly() {
        Zone zone = Zone.createStanding(ZoneId.random(), EVENT_ID, "Name", PRICE, 10);
        assertThat(zone.version()).isEqualTo(0L);
    }

    @Test
    void reconstructRowsAfterLoad_worksForSeatedZone() throws Exception {
        Seat s1 = new Seat(SeatId.random(), "A", 1);
        Seat s2 = new Seat(SeatId.random(), "B", 1);
        Row rowA = new Row("A", List.of(s1));
        Row rowB = new Row("B", List.of(s2));

        Zone zone = Zone.createSeated(ZoneId.random(), EVENT_ID, "Seated", PRICE, List.of(rowA, rowB));
        
        // Invoke the @PostLoad method via reflection to simulate Hibernate loading
        java.lang.reflect.Method method = Zone.class.getDeclaredMethod("reconstructRowsAfterLoad");
        method.setAccessible(true);
        method.invoke(zone);

        assertThat(zone.rows()).hasSize(2);
        assertThat(zone.rows().stream().map(Row::rowLabel)).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void reconstructRowsAfterLoad_clearsRowsForStandingZone() throws Exception {
        Zone zone = Zone.createStanding(ZoneId.random(), EVENT_ID, "Standing", PRICE, 10);
        
        java.lang.reflect.Method method = Zone.class.getDeclaredMethod("reconstructRowsAfterLoad");
        method.setAccessible(true);
        method.invoke(zone);

        assertThat(zone.rows()).isEmpty();
    }
}