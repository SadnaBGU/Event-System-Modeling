package com.eventsystem.infrastructure.persistence.springrepos;


import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;

public class PostgresPurchaseRecordRepository implements IPurchaseRecordRepository {

    private final SpringDataPurchaseRecordRepository jpaRepo;

    public PostgresPurchaseRecordRepository(SpringDataPurchaseRecordRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @SuppressWarnings("null")
    @Override
    public Optional<PurchaseRecord> findById(String recordId) {
        return jpaRepo.findById(recordId);
    }

    public List<PurchaseRecord> findHistoryByBuyer(String buyerId) {
        return jpaRepo.findByBuyerIdOrderByPurchaseTimestampDesc(buyerId);
    }

    public Optional<PurchaseRecord> findByPaymentConfirmation(String paymentConfirmationId) {
        return jpaRepo.findByPaymentConfirmationId(paymentConfirmationId);
    }

    @SuppressWarnings("null")
    public void save(PurchaseRecord purchaseRecord) {
        jpaRepo.save(purchaseRecord);
    }

        @Override
    public void append(PurchaseRecord record) {
        jpaRepo.save(record);
    }

    @Override
    public List<PurchaseRecord> findByBuyer(String buyerId) {
        return jpaRepo.findByBuyerIdOrderByPurchaseTimestampDesc(buyerId);
    }

    @Override
    public List<PurchaseRecord> findByEvent(String eventId) {
        return jpaRepo.findByEventSnapshot_EventId(eventId);
    }

    @Override
    public List<PurchaseRecord> findAll() {
        return jpaRepo.findAll();
    }
    
}
