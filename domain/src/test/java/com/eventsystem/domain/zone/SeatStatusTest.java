package com.eventsystem.domain.zone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SeatStatusTest {

    @Test
    void hasAvailableReservedAndSoldValues() {
        assertThat(SeatStatus.values())
                .containsExactlyInAnyOrder(SeatStatus.AVAILABLE, SeatStatus.RESERVED, SeatStatus.SOLD);
    }

    @Test
    void valueOf_worksForAllStatuses() {
        assertThat(SeatStatus.valueOf("AVAILABLE")).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(SeatStatus.valueOf("RESERVED")).isEqualTo(SeatStatus.RESERVED);
        assertThat(SeatStatus.valueOf("SOLD")).isEqualTo(SeatStatus.SOLD);
    }
}
