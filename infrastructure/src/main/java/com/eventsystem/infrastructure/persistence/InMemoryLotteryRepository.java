package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory adapter for {@link ILotteryRepository}.
 *
 * Indexed by lottery id and by event id. An event can only have one lottery.
 */
public class InMemoryLotteryRepository implements ILotteryRepository {

    private final ConcurrentMap<LotteryId, Lottery> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<EventId, LotteryId> idByEvent = new ConcurrentHashMap<>();

    @Override
    public Optional<Lottery> findById(LotteryId lotteryId) {
        Objects.requireNonNull(lotteryId, "lotteryId must not be null");
        return Optional.ofNullable(byId.get(lotteryId));
    }

    @Override
    public Optional<Lottery> findByEventId(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        LotteryId id = idByEvent.get(eventId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public void save(Lottery lottery) {
        Objects.requireNonNull(lottery, "lottery must not be null");

        LotteryId existingForEvent = idByEvent.putIfAbsent(lottery.getEventId(), lottery.getLotteryId());
        if (existingForEvent != null && !existingForEvent.equals(lottery.getLotteryId())) {
            throw new IllegalStateException(
                    "Event already has a lottery: " + lottery.getEventId().value());
        }
        byId.put(lottery.getLotteryId(), lottery);
    }

    public void clear() {
        byId.clear();
        idByEvent.clear();
    }
}
