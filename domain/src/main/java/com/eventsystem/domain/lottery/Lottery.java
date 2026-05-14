package com.eventsystem.domain.lottery;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Per-event lottery for purchase rights.
 * Status flow: REGISTRATION_OPEN -> CLOSED -> DRAWN.
 */
public class Lottery {

    private final LotteryId lotteryId;
    private final EventId eventId;
    private final Set<MemberId> registrations;
    private final Set<LotteryWinner> winners;
    private LotteryStatus status;
    private Instant drawTimestamp;

    public Lottery(LotteryId lotteryId, EventId eventId) {
        this.lotteryId = Objects.requireNonNull(lotteryId, "lotteryId must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.registrations = ConcurrentHashMap.newKeySet();
        this.winners = ConcurrentHashMap.newKeySet();
        this.status = LotteryStatus.REGISTRATION_OPEN;
    }

    /** Returns true if added, false if the member was already registered. */
    public boolean register(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        if (status != LotteryStatus.REGISTRATION_OPEN) {
            throw new IllegalStateException("Lottery registration is " + status);
        }
        return registrations.add(memberId);
    }

    public synchronized void close() {
        if (status == LotteryStatus.CLOSED) {
            return;
        }
        if (status != LotteryStatus.REGISTRATION_OPEN) {
            throw new IllegalStateException("Lottery cannot close from " + status);
        }
        this.status = LotteryStatus.CLOSED;
    }

    public synchronized void draw(int winnerCount, RandomGenerator rng, Instant now, Duration codeValidity) {
        Objects.requireNonNull(rng, "rng must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(codeValidity, "codeValidity must not be null");
        if (winnerCount < 0) {
            throw new IllegalArgumentException("winnerCount must be non-negative");
        }
        if (codeValidity.isZero() || codeValidity.isNegative()) {
            throw new IllegalArgumentException("codeValidity must be positive");
        }
        if (status == LotteryStatus.DRAWN) {
            return;
        }
        if (status != LotteryStatus.CLOSED) {
            throw new IllegalStateException("Lottery must be CLOSED before drawing (was " + status + ")");
        }

        // Sort first so the same rng + same registrations always give the same winners.
        List<MemberId> pool = new ArrayList<>(registrations);
        pool.sort((a, b) -> a.value().compareTo(b.value()));

        // Fisher-Yates shuffle.
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Collections.swap(pool, i, j);
        }

        int picks = Math.min(winnerCount, pool.size());
        Instant expiry = now.plus(codeValidity);
        Set<String> issuedCodes = new HashSet<>();
        for (int i = 0; i < picks; i++) {
            String code = nextUniqueCode(rng, issuedCodes);
            winners.add(new LotteryWinner(pool.get(i), code, expiry));
        }
        this.drawTimestamp = now;
        this.status = LotteryStatus.DRAWN;
    }

    public Optional<MemberId> validateCode(String code, Instant now) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(now, "now must not be null");
        for (LotteryWinner w : winners) {
            if (w.permissionCode().equals(code) && !w.isExpired(now)) {
                return Optional.of(w.memberId());
            }
        }
        return Optional.empty();
    }

    public LotteryId getLotteryId() { return lotteryId; }
    public EventId getEventId() { return eventId; }
    public LotteryStatus getStatus() { return status; }
    public Instant getDrawTimestamp() { return drawTimestamp; }

    public Set<MemberId> getRegistrations() {
        return Collections.unmodifiableSet(registrations);
    }

    public Set<LotteryWinner> getWinners() {
        return Collections.unmodifiableSet(winners);
    }

    private String nextUniqueCode(RandomGenerator rng, Set<String> already) {
        while (true) {
            String hex = String.format("%016x", rng.nextLong());
            if (already.add(hex)) {
                return hex.toUpperCase();
            }
        }
    }
}
