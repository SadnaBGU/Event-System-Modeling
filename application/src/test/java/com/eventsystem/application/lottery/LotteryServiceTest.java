package com.eventsystem.application.lottery;

import com.eventsystem.application.appexceptions.InvalidLotteryCodeException;
import com.eventsystem.application.appexceptions.LotteryNotFoundException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.application.lottery.ILotteryRepository;
import com.eventsystem.domain.member.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotteryServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(15);

    @Mock
    private ILotteryRepository repo;

    private LotteryService service;

    @BeforeEach
    void setUp() {
        service = new LotteryService(repo, new Random(1L), CLOCK, CODE_VALIDITY);
    }

    @Test
    void openLotterySavesAndReturnsId() {
        LotteryId id = service.openLottery(EventId.random());

        assertThat(id).isNotNull();
        verify(repo).save(any(Lottery.class));
    }

    @Test
    void registerLoadsAndSaves() {
        Lottery lottery = new Lottery(LotteryId.generate(), EventId.random());
        when(repo.findById(lottery.getLotteryId())).thenReturn(Optional.of(lottery));
        MemberId m = new MemberId("m1");

        service.register(m, lottery.getLotteryId());

        assertThat(lottery.getRegistrations()).contains(m);
        verify(repo).save(lottery);
    }

    @Test
    void registerWhenLotteryMissingThrows() {
        LotteryId id = LotteryId.generate();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(new MemberId("m1"), id))
                .isInstanceOf(LotteryNotFoundException.class);
    }

    @Test
    void drawClosesAndPicksWinners() {
        Lottery lottery = new Lottery(LotteryId.generate(), EventId.random());
        lottery.register(new MemberId("m1"));
        lottery.register(new MemberId("m2"));
        when(repo.findById(lottery.getLotteryId())).thenReturn(Optional.of(lottery));

        service.draw(lottery.getLotteryId(), 1);

        assertThat(lottery.getWinners()).hasSize(1);
        verify(repo).save(lottery);
    }

    @Test
    void validateCodeReturnsWinningMember() {
        Lottery lottery = new Lottery(LotteryId.generate(), EventId.random());
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
    void validateCodeRejectsBadCode() {
        Lottery lottery = new Lottery(LotteryId.generate(), EventId.random());
        lottery.close();
        when(repo.findById(lottery.getLotteryId())).thenReturn(Optional.of(lottery));

        assertThatThrownBy(() -> service.validateCode(lottery.getLotteryId(), "nope"))
                .isInstanceOf(InvalidLotteryCodeException.class);
    }
}
