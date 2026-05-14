package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC15: Create and Configure Event
 *
 * Supporting value-object tests.
 * No direct UAT.
 */
class EventIdTest {

    @Test
    void randomProducesUniqueAndNonBlankIds() {
        EventId a = EventId.random();
        EventId b = EventId.random();
        
        assertThat(a).isNotEqualTo(b);
        assertThat(a.value()).isNotBlank();
    }

    @Test
    void equalityIsByValue() {
        assertThat(new EventId("abc")).isEqualTo(new EventId("abc"));
    }

    @Test
    void rejectsNullAndBlank() {
        assertThatThrownBy(() -> new EventId(null))
                .isInstanceOf(NullPointerException.class);
                
        assertThatThrownBy(() -> new EventId(""))
                .isInstanceOf(IllegalArgumentException.class);
                
        assertThatThrownBy(() -> new EventId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringReturnsValue() {
        EventId eventId = new EventId("event-1");
        // We verify that the string representation contains the ID value
        assertThat(eventId.toString()).contains("event-1");
    }
}