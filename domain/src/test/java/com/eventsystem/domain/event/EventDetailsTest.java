package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

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
                "Description")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void descriptionMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "Tel Aviv",
                "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void datesMustNotContainNull() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of((LocalDateTime) null),
                "Music",
                "Tel Aviv",
                "Description")).isInstanceOf(NullPointerException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void datesMustNotBeNull() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                null,
                "Music",
                "Tel Aviv",
                "Description")).isInstanceOf(NullPointerException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void datesMustNotBeEmpty() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(),
                "Music",
                "Tel Aviv",
                "Description")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void categoryMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "",
                "Tel Aviv",
                "Description")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void locationMustNotBeBlank() {
        assertThatThrownBy(() -> new EventDetails(
                "Concert",
                List.of(LocalDateTime.now().plusDays(1)),
                "Music",
                "",
                "Description")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenSameInstance_shouldReturnTrue() {
        EventDetails details = validDetails();

        assertThat(details).isEqualTo(details);
        assertThat(details.hashCode()).isEqualTo(details.hashCode());
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenAllFieldsAreSame_shouldReturnTrueAndSameHashCode() {
        LocalDateTime date = LocalDateTime.of(2026, 7, 1, 20, 30);

        EventDetails first = new EventDetails(
                "Concert",
                List.of(date),
                "Music",
                "Tel Aviv",
                "Description");

        EventDetails second = new EventDetails(
                "Concert",
                List.of(date),
                "Music",
                "Tel Aviv",
                "Description");

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(first);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenComparedToNull_shouldReturnFalse() {
        EventDetails details = validDetails();

        assertThat(details).isNotEqualTo(null);
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenComparedToDifferentType_shouldReturnFalse() {
        EventDetails details = validDetails();

        assertThat(details).isNotEqualTo("not event details");
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenNameIsDifferent_shouldReturnFalse() {
        EventDetails first = validDetails();

        EventDetails second = new EventDetails(
                "Different Concert",
                first.dates(),
                first.category(),
                first.location(),
                first.description());

        assertThat(first).isNotEqualTo(second);
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenDatesAreDifferent_shouldReturnFalse() {
        EventDetails first = validDetails();

        EventDetails second = new EventDetails(
                first.name(),
                List.of(LocalDateTime.of(2026, 7, 2, 20, 30)),
                first.category(),
                first.location(),
                first.description());

        assertThat(first).isNotEqualTo(second);
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenCategoryIsDifferent_shouldReturnFalse() {
        EventDetails first = validDetails();

        EventDetails second = new EventDetails(
                first.name(),
                first.dates(),
                "Theater",
                first.location(),
                first.description());

        assertThat(first).isNotEqualTo(second);
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenLocationIsDifferent_shouldReturnFalse() {
        EventDetails first = validDetails();

        EventDetails second = new EventDetails(
                first.name(),
                first.dates(),
                first.category(),
                "Jerusalem",
                first.description());

        assertThat(first).isNotEqualTo(second);
    }

    @Test // REQ: PRD-01, UC 15 - EventDetails value object equality
    void equals_whenDescriptionIsDifferent_shouldReturnFalse() {
        EventDetails first = validDetails();

        EventDetails second = new EventDetails(
                first.name(),
                first.dates(),
                first.category(),
                first.location(),
                "Different description");

        assertThat(first).isNotEqualTo(second);
    }

    private EventDetails validDetails() {
        return new EventDetails(
                "Concert",
                List.of(LocalDateTime.of(2026, 7, 1, 20, 30)),
                "Music",
                "Tel Aviv",
                "Description");
    }
}