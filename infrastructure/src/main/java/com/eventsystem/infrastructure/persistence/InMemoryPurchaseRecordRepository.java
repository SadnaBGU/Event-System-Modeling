package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryPurchaseRecordRepository implements IPurchaseRecordRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryPurchaseRecordRepository.class);
    
    private final Map<String, PurchaseRecord> store = new ConcurrentHashMap<>();

    @Override
    public void append(PurchaseRecord record) {
        PurchaseRecord existing = store.putIfAbsent(record.recordId(), record);
        if (existing != null) {
            logger.warn("Data anomaly: Attempted to append duplicate PurchaseRecord with ID: {}", record.recordId());
        } else {
            logger.info("Appended new PurchaseRecord to memory store: {}", record.recordId());
        }
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

    @Override
    public List<PurchaseRecord> findAll() {
        return new ArrayList<>(store.values());
    }   
}