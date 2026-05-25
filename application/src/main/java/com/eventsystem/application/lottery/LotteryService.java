package com.eventsystem.application.lottery;

import com.eventsystem.application.appexceptions.InvalidLotteryCodeException;
import com.eventsystem.application.appexceptions.LotteryNotFoundException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.application.lottery.LotteryRepository;
import com.eventsystem.domain.member.MemberId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Use cases: open a lottery, register members, draw winners, validate codes.
 */
public class LotteryService {

    private static final Logger log = LoggerFactory.getLogger(LotteryService.class);

    private final LotteryRepository lotteries;
    private final RandomGenerator rng;
    private final Clock clock;
    private final Duration codeValidity;

    public LotteryService(LotteryRepository lotteries,
                          RandomGenerator rng,
                          Clock clock,
                          Duration codeValidity) {
        this.lotteries = Objects.requireNonNull(lotteries, "lotteries must not be null");
        this.rng = Objects.requireNonNull(rng, "rng must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(codeValidity, "codeValidity must not be null");
        if (codeValidity.isZero() || codeValidity.isNegative()) {
            throw new IllegalArgumentException("codeValidity must be positive");
        }
        this.codeValidity = codeValidity;
    }

    public LotteryId openLottery(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Lottery lottery = new Lottery(LotteryId.generate(), eventId);
        lotteries.save(lottery);
        log.info("Lottery opened lotteryId={} eventId={}", lottery.getLotteryId().value(), eventId.value());
        return lottery.getLotteryId();
    }

    public void register(MemberId memberId, LotteryId lotteryId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Lottery lottery = load(lotteryId);
        boolean added = lottery.register(memberId);
        lotteries.save(lottery);
        if (added) {
            log.info("Lottery registration lotteryId={} memberId={}", lotteryId.value(), memberId.value());
        }
    }

    public void draw(LotteryId lotteryId, int winnerCount) {
        Lottery lottery = load(lotteryId);
        lottery.close();
        lottery.draw(winnerCount, rng, clock.instant(), codeValidity);
        lotteries.save(lottery);
        log.info("Lottery drawn lotteryId={} winners={}", lotteryId.value(), lottery.getWinners().size());
    }

    public MemberId validateCode(LotteryId lotteryId, String code) {
        Objects.requireNonNull(code, "code must not be null");
        Lottery lottery = load(lotteryId);
        return lottery.validateCode(code, clock.instant())
                .orElseThrow(InvalidLotteryCodeException::new);
    }

    private Lottery load(LotteryId lotteryId) {
        Objects.requireNonNull(lotteryId, "lotteryId must not be null");
        return lotteries.findById(lotteryId).orElseThrow(() -> new LotteryNotFoundException(lotteryId));
    }
}
