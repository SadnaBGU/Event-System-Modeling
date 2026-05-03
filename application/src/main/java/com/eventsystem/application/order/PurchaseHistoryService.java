package com.eventsystem.application.order;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PurchaseHistoryService {

    private final PurchaseRecordRepository purchaseRecordRepository;

    public PurchaseHistoryService(PurchaseRecordRepository purchaseRecordRepository) {
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    /**
     * Extract the purchase history for a specific buyer. 
     * this method will be called from the web layer when a user accesses their purchase history page.
     */
    public List<PurchaseRecord> getHistoryForBuyer(String buyerId) {
        return purchaseRecordRepository.findByBuyer(buyerId).stream()
                .sorted(Comparator.comparing(PurchaseRecord::purchaseTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Extract details for a specific purchase receipt.
     * this method will be called when a user clicks on a purchase record to view its details.
     */
    public Optional<PurchaseRecord> getReceiptDetails(String recordId) {
        return purchaseRecordRepository.findById(recordId);
    }
}