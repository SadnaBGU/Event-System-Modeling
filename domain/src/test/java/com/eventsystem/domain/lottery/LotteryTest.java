package com.eventsystem.domain.lottery;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LotteryTest {

    private static final Instant NOW = Instant.parse("2026-05-13T12:00:00Z");
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(15);

    private final EventId eventId = new EventId("event-1");

    private Lottery newLottery() {
        return new Lottery(LotteryId.generate(), eventId);
    }

    // ==========================================
    // Constructors & Persistable 
    // ==========================================

    @Test
    void constructor_rejectsNulls() {
        assertThatThrownBy(() -> new Lottery(null, eventId)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Lottery(LotteryId.generate(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void jpaConstructor_createsEmptyInstance() throws Exception {
        java.lang.reflect.Constructor<Lottery> c = Lottery.class.getDeclaredConstructor();
        c.setAccessible(true);
        Lottery l = c.newInstance();
        
        assertThat(l.getId()).isNull();
        assertThat(l.getEventId()).isNull();
        // Since version defaults to 0 primitive, isNew should be true if it checks == 0L
        assertThat(l.isNew()).isTrue();
    }

    @Test
    void persistableMethods_and_Getters_returnCorrectValues() {
        LotteryId id = LotteryId.generate();
        Lottery l = new Lottery(id, eventId);

        assertThat(l.getId()).isEqualTo(id);
        assertThat(l.getLotteryId()).isEqualTo(id);
        assertThat(l.getEventId()).isEqualTo(eventId);
        assertThat(l.isNew()).isTrue();
        assertThat(l.getDrawTimestamp()).isNull();
    }

    // ==========================================
    // Registration
    // ==========================================

    @Test
    void registerAddsMember() {
        Lottery l = newLottery();
        assertThat(l.register(new MemberId("m1"))).isTrue();
        assertThat(l.getRegistrations()).hasSize(1);
    }

    @Test
    void registerDuplicateReturnsFalse() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        assertThat(l.register(new MemberId("m1"))).isFalse();
        assertThat(l.getRegistrations()).hasSize(1);
    }

    @Test
    void registerRejectsNullMember() {
        Lottery l = newLottery();
        assertThatThrownBy(() -> l.register(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerAfterCloseThrows() {
        Lottery l = newLottery();
        l.close();
        assertThatThrownBy(() -> l.register(new MemberId("m1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerAfterDeadlineThrows() {
        Lottery l = new Lottery(LotteryId.generate(), eventId, NOW.plusSeconds(60));

        assertThat(l.register(new MemberId("m1"), NOW)).isTrue();
        assertThatThrownBy(() -> l.register(new MemberId("m2"), NOW.plusSeconds(61)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadline");
    }

    // ==========================================
    // Close
    // ==========================================

    @Test
    void closeTwiceIsAllowed() {
        Lottery l = newLottery();
        l.close();
        l.close();
        assertThat(l.getStatus()).isEqualTo(LotteryStatus.CLOSED);
    }

    @Test
    void closeFromDrawnThrows() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        l.close();
        l.draw(1, new Random(1L), NOW, CODE_VALIDITY);

        assertThatThrownBy(l::close).isInstanceOf(IllegalStateException.class);
    }

    // ==========================================
    // Draw
    // ==========================================

    @Test
    void drawBeforeCloseThrows() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        assertThatThrownBy(() -> l.draw(1, new Random(1L), NOW, CODE_VALIDITY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void drawRejectsInvalidArguments() {
        Lottery l = newLottery();
        l.close();

        // Null checks
        assertThatThrownBy(() -> l.draw(1, null, NOW, CODE_VALIDITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> l.draw(1, new Random(), null, CODE_VALIDITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> l.draw(1, new Random(), NOW, null)).isInstanceOf(NullPointerException.class);

        // Value checks
        assertThatThrownBy(() -> l.draw(-1, new Random(), NOW, CODE_VALIDITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> l.draw(1, new Random(), NOW, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> l.draw(1, new Random(), NOW, Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void drawPicksAtMostThePoolSize() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        l.register(new MemberId("m2"));
        l.close();
        l.draw(10, new Random(1L), NOW, CODE_VALIDITY);

        assertThat(l.getWinners()).hasSize(2);
        assertThat(l.getStatus()).isEqualTo(LotteryStatus.DRAWN);
        assertThat(l.getDrawTimestamp()).isEqualTo(NOW);
    }

    @Test
    void drawCalledTwiceKeepsFirstResult() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        l.register(new MemberId("m2"));
        l.close();
        l.draw(1, new Random(1L), NOW, CODE_VALIDITY);
        Set<LotteryWinner> firstResult = Set.copyOf(l.getWinners());

        l.draw(2, new Random(99L), NOW.plusSeconds(60), CODE_VALIDITY);

        assertThat(l.getWinners()).isEqualTo(firstResult);
    }

    @Test
    void sameSeedProducesSameWinners() {
        Lottery a = newLottery();
        Lottery b = newLottery();
        for (int i = 0; i < 10; i++) {
            a.register(new MemberId("m" + i));
            b.register(new MemberId("m" + i));
        }
        a.close();
        b.close();

        a.draw(3, new Random(42L), NOW, CODE_VALIDITY);
        b.draw(3, new Random(42L), NOW, CODE_VALIDITY);

        assertThat(a.getWinners()).isEqualTo(b.getWinners());
    }

    @Test
    void draw_handlesCodeCollisionGracefully() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        l.register(new MemberId("m2"));
        l.close();

        // Create a fake RandomGenerator that returns the exact same number twice to force collision
        RandomGenerator mockRng = mock(RandomGenerator.class);
        when(mockRng.nextInt(anyInt())).thenReturn(0);
        when(mockRng.nextLong()).thenReturn(12345L, 12345L, 67890L);

        l.draw(2, mockRng, NOW, CODE_VALIDITY);

        // Both winners should have been selected successfully despite the initial code collision
        assertThat(l.getWinners()).hasSize(2);
    }

    // ==========================================
    // Validation
    // ==========================================

    @Test
    void validateCodeRejectsNulls() {
        Lottery l = newLottery();
        assertThatThrownBy(() -> l.validateCode(null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> l.validateCode("CODE", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validateCodeReturnsWinningMember() {
        Lottery l = newLottery();
        MemberId m = new MemberId("m1");
        l.register(m);
        l.close();
        l.draw(1, new Random(1L), NOW, CODE_VALIDITY);

        String code = l.getWinners().iterator().next().permissionCode();
        assertThat(l.validateCode(code, NOW)).contains(m);
    }

    @Test
    void validateCodeReturnsEmptyForUnknownCode() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        l.close();
        l.draw(1, new Random(1L), NOW, CODE_VALIDITY);

        assertThat(l.validateCode("bogus", NOW)).isEmpty();
    }

    @Test
    void validateCodeReturnsEmptyWhenExpired() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        l.close();
        l.draw(1, new Random(1L), NOW, CODE_VALIDITY);

        String code = l.getWinners().iterator().next().permissionCode();
        Instant afterExpiry = NOW.plus(CODE_VALIDITY).plusSeconds(1);
        assertThat(l.validateCode(code, afterExpiry)).isEmpty();
    }
}
