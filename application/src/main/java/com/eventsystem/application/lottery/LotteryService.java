package com.eventsystem.application.lottery;

import com.eventsystem.application.appexceptions.InvalidLotteryCodeException;
import com.eventsystem.application.appexceptions.LotteryNotFoundException;
import com.eventsystem.application.member.INotificationPort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.lottery.LotteryWinner;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
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

    private final ILotteryRepository lotteries;
    private final RandomGenerator rng;
    private final Clock clock;
    private final Duration codeValidity;
    private final INotificationPort notificationPort;

    public LotteryService(ILotteryRepository lotteries,
                          RandomGenerator rng,
                          Clock clock,
                          Duration codeValidity) {
        this(lotteries, rng, clock, codeValidity, null);
    }

    public LotteryService(ILotteryRepository lotteries,
                          RandomGenerator rng,
                          Clock clock,
                          Duration codeValidity,
                          INotificationPort notificationPort) {
        this.lotteries = Objects.requireNonNull(lotteries, "lotteries must not be null");
        this.rng = Objects.requireNonNull(rng, "rng must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(codeValidity, "codeValidity must not be null");
        if (codeValidity.isZero() || codeValidity.isNegative()) {
            throw new IllegalArgumentException("codeValidity must be positive");
        }
        this.codeValidity = codeValidity;
        this.notificationPort = notificationPort;
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

    public void register(MemberId memberId, EventId eventId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        Lottery lottery = lotteries.findByEventId(eventId)
            .orElseThrow(() -> new LotteryNotFoundException(eventId));
        boolean added = lottery.register(memberId);
        lotteries.save(lottery);
        if (added) {
            log.info("Lottery registration lotteryId={} eventId={} memberId={}",
                    lottery.getLotteryId().value(), eventId.value(), memberId.value());
        }
    }

    public void draw(LotteryId lotteryId, int winnerCount) {
        Lottery lottery = load(lotteryId);
        lottery.close();
        lottery.draw(winnerCount, rng, clock.instant(), codeValidity);
        lotteries.save(lottery);
        log.info("Lottery drawn lotteryId={} winners={}", lotteryId.value(), lottery.getWinners().size());
        notifyWinners(lottery);
    }

    /**
     * Push a LOTTERY_WON notification (with the time-limited purchase code) to each winner.
     * Best-effort: a notification failure must not roll back a completed draw, so failures
     * are logged and swallowed.
     */
    private void notifyWinners(Lottery lottery) {
        if (notificationPort == null) {
            return;
        }
        String eventId = lottery.getEventId().value();
        for (LotteryWinner winner : lottery.getWinners()) {
            try {
                BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, winner.memberId().value());
                notificationPort.sendLotteryWon(buyer, eventId, winner.permissionCode());
            } catch (RuntimeException e) {
                log.warn("Failed to notify lottery winner {}: {}", winner.memberId().value(), e.getMessage());
            }
        }
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
