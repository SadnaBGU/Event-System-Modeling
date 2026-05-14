package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * UC15: Create and Configure Event
 *
 * Tests for UATs:
 * - UAT-42: Missing Required Fields
 */
class EventDetailsTest {

    @Test // UAT-42: Missing Required Fields
    void nameMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void descriptionMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "Tel Aviv",
                ""
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void datesMustNotContainNull() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of((LocalDateTime) null),
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void datesMustNotBeNull() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                null,
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void datesMustNotBeEmpty() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(),
                "Music",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void categoryMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "",
                "Tel Aviv",
                "Description"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
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