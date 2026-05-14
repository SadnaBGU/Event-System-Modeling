package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventIdTest {

    @Test
    void randomProducesUniqueIds() {
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
        assertThatThrownBy(() -> new EventId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventId(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
