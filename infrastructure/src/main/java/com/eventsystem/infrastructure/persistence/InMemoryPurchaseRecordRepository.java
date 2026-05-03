package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.order.PurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryPurchaseRecordRepository implements PurchaseRecordRepository {
    private final Map<String, PurchaseRecord> store = new ConcurrentHashMap<>();

    @Override
    public void append(PurchaseRecord record) {
        store.putIfAbsent(record.recordId(), record);
    }

    @Override
    public Optional<PurchaseRecord> findById(String recordId) {
        return Optional.ofNullable(store.get(recordId));
    }

    @Override
    public List<PurchaseRecord> findByBuyer(String buyerId) {
        return store.values().stream()
                .filter(record -> record.buyerId().equals(buyerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<PurchaseRecord> findByEvent(String eventId) {
        return store.values().stream()
                .filter(record -> record.eventSnapshot().eventId().equals(eventId))
                .collect(Collectors.toList());
    }
}