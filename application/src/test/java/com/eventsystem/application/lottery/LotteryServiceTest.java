package com.eventsystem.application.lottery;

import com.eventsystem.application.appexceptions.InvalidLotteryCodeException;
import com.eventsystem.application.appexceptions.LotteryNotFoundException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.*;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LotteryServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-13T12:00:00Z");
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(15);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

    @Mock private ILotteryRepository repo;
    @Mock private RandomGenerator rng;

    private LotteryService service;

    @BeforeEach
    void setUp() {
        service = new LotteryService(repo, rng, clock, CODE_VALIDITY);
    }

    @Test
    void constructor_RejectsNulls() {
        assertThatThrownBy(() -> new LotteryService(null, rng, clock, CODE_VALIDITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LotteryService(repo, null, clock, CODE_VALIDITY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LotteryService(repo, rng, null, CODE_VALIDITY)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void register_DelegatesToLotteryAndSaves() {
        EventId eventId = new EventId("EV-1");
        MemberId memberId = new MemberId("M-1");
        Lottery lottery = new Lottery(LotteryId.generate(), eventId, NOW.plusSeconds(60));
        
        when(repo.findByEventId(eventId)).thenReturn(Optional.of(lottery));

        service.register(memberId, eventId);

        verify(repo).save(lottery);
        assertThat(lottery.getRegistrations()).contains(memberId);
    }

    @Test
    void openLottery_SavesFutureDeadline() {
        EventId eventId = new EventId("EV-1");
        Instant deadline = NOW.plusSeconds(60);

        LotteryId lotteryId = service.openLottery(eventId, deadline);

        verify(repo).save(argThat(lottery ->
                lottery.getLotteryId().equals(lotteryId)
                        && lottery.getEventId().equals(eventId)
                        && lottery.getRegistrationDeadline().equals(deadline)));
    }

    @Test
    void openLottery_RejectsPastDeadline() {
        assertThatThrownBy(() -> service.openLottery(new EventId("EV-1"), NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void register_ThrowsAfterDeadline() {
        EventId eventId = new EventId("EV-1");
        Lottery lottery = new Lottery(LotteryId.generate(), eventId, NOW.minusSeconds(1));

        when(repo.findByEventId(eventId)).thenReturn(Optional.of(lottery));

        assertThatThrownBy(() -> service.register(new MemberId("M-1"), eventId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadline");
        verify(repo, never()).save(lottery);
    }

    @Test
    void register_ThrowsWhenLotteryNotFound() {
        assertThatThrownBy(() -> service.register(new MemberId("M-1"), new EventId("NOPE")))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void draw_ClosesAndPicksWinners() {
        Lottery lottery = new Lottery(LotteryId.generate(), new EventId("EV-1"));
        lottery.register(new MemberId("m1"));
        lottery.register(new MemberId("m2"));
        when(repo.findById(lottery.getLotteryId())).thenReturn(Optional.of(lottery));

        service.draw(lottery.getLotteryId(), 1);

        assertThat(lottery.getStatus()).isEqualTo(LotteryStatus.DRAWN);
        assertThat(lottery.getWinners()).hasSize(1);
        verify(repo).save(lottery);
    }

    @Test
    void draw_ThrowsWhenLotteryNotFound() {
        assertThatThrownBy(() -> service.draw(LotteryId.generate(), 1))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void validateCode_ReturnsWinner() {
        Lottery lottery = new Lottery(LotteryId.generate(), new EventId("EV-1"));
        MemberId winner = new MemberId("m1");
        lottery.register(winner);
        lottery.close();
        lottery.draw(1, new Random(1L), NOW, CODE_VALIDITY);
        
        String code = lottery.getWinners().iterator().next().permissionCode();
        when(repo.findById(lottery.getLotteryId())).thenReturn(Optional.of(lottery));

        MemberId resolved = service.validateCode(lottery.getLotteryId(), code);

        assertThat(resolved).isEqualTo(winner);
    }

    @Test
    void validateCode_ThrowsOnInvalidCode() {
        Lottery lottery = new Lottery(LotteryId.generate(), new EventId("EV-1"));
        lottery.close();
        when(repo.findById(lottery.getLotteryId())).thenReturn(Optional.of(lottery));

        assertThatThrownBy(() -> service.validateCode(lottery.getLotteryId(), "BAD-CODE"))
                .isInstanceOf(InvalidLotteryCodeException.class);
    }

    @Test
    void validateCode_RejectsNulls() {
        LotteryId id = LotteryId.generate();
        assertThatThrownBy(() -> service.validateCode(id, null)).isInstanceOf(NullPointerException.class);
    }
}
