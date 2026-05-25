package com.eventsystem.application.order;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;

public interface IPurchaseRecordRepository {
    void append(PurchaseRecord record);
    Optional<PurchaseRecord> findById(String recordId);
    List<PurchaseRecord> findByBuyer(String buyerId);
    List<PurchaseRecord> findByEvent(String eventId);
    List<PurchaseRecord> findAll();
}
