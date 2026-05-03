package com.eventsystem.application.order;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import java.util.List;
import java.util.Optional;

public interface PurchaseRecordRepository {
    void append(PurchaseRecord record);
    Optional<PurchaseRecord> findById(String recordId);
    List<PurchaseRecord> findByBuyer(String buyerId);
}
