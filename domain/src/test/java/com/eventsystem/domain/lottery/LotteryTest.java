package com.eventsystem.domain.lottery;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotteryTest {

    private static final Instant NOW = Instant.parse("2026-05-13T12:00:00Z");
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(15);

    private final EventId eventId = new EventId("event-1");

    private Lottery newLottery() {
        return new Lottery(LotteryId.generate(), eventId);
    }

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
    void registerAfterCloseThrows() {
        Lottery l = newLottery();
        l.close();
        assertThatThrownBy(() -> l.register(new MemberId("m1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeTwiceIsAllowed() {
        Lottery l = newLottery();
        l.close();
        l.close();
        assertThat(l.getStatus()).isEqualTo(LotteryStatus.CLOSED);
    }

    @Test
    void drawBeforeCloseThrows() {
        Lottery l = newLottery();
        l.register(new MemberId("m1"));
        assertThatThrownBy(() -> l.draw(1, new Random(1L), NOW, CODE_VALIDITY))
                .isInstanceOf(IllegalStateException.class);
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
