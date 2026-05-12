package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapElementTest {

    @Test
    void typeMustNotBeBlank() {
        assertThatThrownBy(() -> new MapElement(
                "",
                "Main stage",
                10,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelMustNotBeBlank() {
        assertThatThrownBy(() -> new MapElement(
                "STAGE",
                "",
                10,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionXMustNotBeNegative() {
        assertThatThrownBy(() -> new MapElement(
                "STAGE",
                "Main stage",
                -1,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionYMustNotBeNegative() {
        assertThatThrownBy(() -> new MapElement(
                "STAGE",
                "Main stage",
                10,
                -1,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}