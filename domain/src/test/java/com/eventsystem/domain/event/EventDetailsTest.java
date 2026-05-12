package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDetailsTest {

    @Test
    void nameMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptionMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "Tel Aviv",
                ""
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datesMustNotContainNull() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of((LocalDateTime) null),
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void datesMustNotBeNull() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                null,
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void datesMustNotBeEmpty() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(),
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void locationMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}