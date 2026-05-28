package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.policy.IPurchasePolicyRepository;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.PurchasePolicy;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPurchasePolicyRepository implements IPurchasePolicyRepository {

    private final Map<EventId, PurchasePolicy> policiesByEventId = new ConcurrentHashMap<>();

    @Override
    public Optional<PurchasePolicy> findByEventId(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return Optional.ofNullable(policiesByEventId.get(eventId));
    }

    @Override
    public void saveForEvent(EventId eventId, PurchasePolicy purchasePolicy) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(purchasePolicy, "purchasePolicy must not be null");

        policiesByEventId.put(eventId, purchasePolicy);
    }

    @Override
    public void deleteByEventId(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        policiesByEventId.remove(eventId);
    }

    @Override
    public boolean existsByEventId(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return policiesByEventId.containsKey(eventId);
    }
}
