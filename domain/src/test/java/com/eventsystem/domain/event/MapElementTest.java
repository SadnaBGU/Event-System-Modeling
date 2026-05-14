package com.eventsystem.domain.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
/** 
 * UC15: Create and Configure Event
 *
 * Tests for UATs:
 * - UAT-41: Successful Event Creation
 * - UAT-42: Missing Required Fields
 */
class MapElementTest {

    @Test // UAT-42: Missing Required Fields
    void typeMustNotBeBlank() {
        assertThatThrownBy(() -> new MapElement(
                "",
                "Main stage",
                10,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void labelMustNotBeBlank() {
        assertThatThrownBy(() -> new MapElement(
                "STAGE",
                "",
                10,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
    void positionXMustNotBeNegative() {
        assertThatThrownBy(() -> new MapElement(
                "STAGE",
                "Main stage",
                -1,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test // UAT-42: Missing Required Fields
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