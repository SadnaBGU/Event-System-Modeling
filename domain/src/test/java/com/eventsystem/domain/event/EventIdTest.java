package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventIdTest {

    @Test
    void randomCreatesNonBlankId() {
        EventId eventId = EventId.random();

        assertThat(eventId.value()).isNotBlank();
    }

    @Test
    void valueMustNotBeBlank() {
        assertThatThrownBy(() -> new EventId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueMustNotBeNull() {
        assertThatThrownBy(() -> new EventId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringReturnsValue() {
        EventId eventId = new EventId("event-1");

        assertThat(eventId.toString()).isEqualTo("event-1");
    }
}