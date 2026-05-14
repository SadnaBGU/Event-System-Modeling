package com.eventsystem.domain.lottery;

import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotteryValueObjectsTest {

    @Test
    void generateProducesUniqueIds() {
        LotteryId a = LotteryId.generate();
        LotteryId b = LotteryId.generate();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void lotteryIdRejectsBlank() {
        assertThatThrownBy(() -> new LotteryId(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lotteryWinnerRejectsBlankCode() {
        MemberId m = new MemberId("m1");
        Instant t = Instant.parse("2026-05-13T12:00:00Z");
        assertThatThrownBy(() -> new LotteryWinner(m, "", t))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isExpiredAtBoundary() {
        MemberId m = new MemberId("m1");
        Instant expiry = Instant.parse("2026-05-13T12:00:00Z");
        LotteryWinner w = new LotteryWinner(m, "CODE", expiry);

        assertThat(w.isExpired(expiry.minusSeconds(1))).isFalse();
        assertThat(w.isExpired(expiry)).isTrue();
    }
}
