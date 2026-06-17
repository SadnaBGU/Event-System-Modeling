package com.eventsystem.domain.lottery;

import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotteryValueObjectsTest {

    // ==========================================
    // LotteryId Tests
    // ==========================================

    @Test
    void generateProducesUniqueIds() {
        LotteryId a = LotteryId.generate();
        LotteryId b = LotteryId.generate();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void lotteryIdRejectsNullAndBlank() {
        assertThatThrownBy(() -> new LotteryId(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new LotteryId(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lotteryIdJpaConstructorCreatesEmpty() throws Exception {
        java.lang.reflect.Constructor<LotteryId> c = LotteryId.class.getDeclaredConstructor();
        c.setAccessible(true);
        LotteryId id = c.newInstance();
        assertThat(id.value()).isNull();
    }

    @Test
    void lotteryIdEqualsAndHashCode() {
        LotteryId id1 = new LotteryId("123");
        LotteryId id2 = new LotteryId("123");
        LotteryId id3 = new LotteryId("456");

        assertThat(id1).isEqualTo(id1); // Reflexive
        assertThat(id1).isEqualTo(id2); // Symmetric
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode()); // HashCode

        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1).isNotEqualTo(null);
        assertThat(id1).isNotEqualTo(new Object());
    }

    // ==========================================
    // LotteryWinner Tests
    // ==========================================

    @Test
    void lotteryWinnerRejectsNullsAndBlanks() {
        MemberId m = new MemberId("m1");
        Instant t = Instant.parse("2026-05-13T12:00:00Z");

        assertThatThrownBy(() -> new LotteryWinner(null, "CODE", t)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LotteryWinner(m, null, t)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LotteryWinner(m, "CODE", null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new LotteryWinner(m, "", t))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lotteryWinnerJpaConstructorCreatesEmpty() throws Exception {
        java.lang.reflect.Constructor<LotteryWinner> c = LotteryWinner.class.getDeclaredConstructor();
        c.setAccessible(true);
        LotteryWinner w = c.newInstance();
        assertThat(w.memberId()).isNull();
    }

    @Test
    void isExpiredAtBoundary() {
        MemberId m = new MemberId("m1");
        Instant expiry = Instant.parse("2026-05-13T12:00:00Z");
        LotteryWinner w = new LotteryWinner(m, "CODE", expiry);

        assertThat(w.isExpired(expiry.minusSeconds(1))).isFalse();
        assertThat(w.isExpired(expiry)).isTrue();
    }

    @Test
    void isExpiredRejectsNull() {
        MemberId m = new MemberId("m1");
        Instant expiry = Instant.parse("2026-05-13T12:00:00Z");
        LotteryWinner w = new LotteryWinner(m, "CODE", expiry);

        assertThatThrownBy(() -> w.isExpired(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void lotteryWinnerEqualsAndHashCode() {
        MemberId m1 = new MemberId("m1");
        MemberId m2 = new MemberId("m2");
        Instant t1 = Instant.parse("2026-05-13T12:00:00Z");
        Instant t2 = Instant.parse("2026-06-13T12:00:00Z");

        LotteryWinner w1 = new LotteryWinner(m1, "CODE1", t1);
        LotteryWinner w2 = new LotteryWinner(m1, "CODE1", t1);
        LotteryWinner w3 = new LotteryWinner(m2, "CODE1", t1); // Diff Member
        LotteryWinner w4 = new LotteryWinner(m1, "CODE2", t1); // Diff Code
        LotteryWinner w5 = new LotteryWinner(m1, "CODE1", t2); // Diff Expiry

        assertThat(w1).isEqualTo(w1);
        assertThat(w1).isEqualTo(w2);
        assertThat(w1.hashCode()).isEqualTo(w2.hashCode());

        assertThat(w1).isNotEqualTo(w3);
        assertThat(w1).isNotEqualTo(w4);
        assertThat(w1).isNotEqualTo(w5);
        assertThat(w1).isNotEqualTo(null);
        assertThat(w1).isNotEqualTo(new Object());
    }
}