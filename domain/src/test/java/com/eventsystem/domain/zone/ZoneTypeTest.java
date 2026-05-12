package com.eventsystem.domain.zone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ZoneTypeTest {

    @Test
    void hasSeatedAndStandingValues() {
        assertThat(ZoneType.values())
                .containsExactlyInAnyOrder(ZoneType.SEATED, ZoneType.STANDING);
    }

    @Test
    void valueOf_worksForBothTypes() {
        assertThat(ZoneType.valueOf("SEATED")).isEqualTo(ZoneType.SEATED);
        assertThat(ZoneType.valueOf("STANDING")).isEqualTo(ZoneType.STANDING);
    }
}
