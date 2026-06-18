package com.eventsystem.infrastructure.persistence.springrepos;


import org.springframework.data.jpa.repository.JpaRepository;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;

import java.util.List;
import java.util.Optional;

public interface SpringDataPurchaseRecordRepository extends JpaRepository<PurchaseRecord, String> {

    // Derived query method to get a buyer's history ordered by most recent
    List<PurchaseRecord> findByBuyerIdOrderByPurchaseTimestampDesc(String buyerId);

    // Custom query example to find records by a specific payment confirmation ID
    Optional<PurchaseRecord> findByPaymentConfirmationId(String paymentConfirmationId);

    List<PurchaseRecord> findByEventSnapshot_EventId(String eventId);

}
