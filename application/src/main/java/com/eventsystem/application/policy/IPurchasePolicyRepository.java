package com.eventsystem.application.policy;

import java.util.Optional;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.PurchasePolicy;

public interface IPurchasePolicyRepository {

    Optional<PurchasePolicy> findByEventId(EventId eventId);

    void saveForEvent(EventId eventId, PurchasePolicy purchasePolicy);

    void deleteByEventId(EventId eventId);

    boolean existsByEventId(EventId eventId);
    
}