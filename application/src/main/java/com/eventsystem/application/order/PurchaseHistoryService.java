package com.eventsystem.application.order;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;

public class PurchaseHistoryService {

    private final IPurchaseRecordRepository purchaseRecordRepository;
    private final Logger logger = LoggerFactory.getLogger(PurchaseHistoryService.class);

    public PurchaseHistoryService(IPurchaseRecordRepository purchaseRecordRepository) {
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    /**
     * Extract the purchase history for a specific buyer. 
     * this method will be called from the web layer when a user accesses their purchase history page.
     */
    public List<PurchaseRecordDTO> getHistoryForBuyer(String buyerId) {
        logger.info("Fetching purchase history for buyer");
        
        List<PurchaseRecord> history = purchaseRecordRepository.findByBuyer(buyerId).stream()
                .sorted(Comparator.comparing(PurchaseRecord::getPurchaseTimestamp).reversed())
                .collect(Collectors.toList());

        logger.info("Found {} purchase records for buyer", history.size());        
        return history.stream().map(PurchaseRecordDTO::fromDomain).collect(Collectors.toList());
    }

    /**
     * Extract details for a specific purchase receipt.
     * this method will be called when a user clicks on a purchase record to view its details.
     */
    public Optional<PurchaseRecordDTO> getReceiptDetails(String recordId) {
        logger.info("Fetching receipt details for recordId: {}", recordId);
        
        Optional<PurchaseRecord> receipt = purchaseRecordRepository.findById(recordId);
        
        if (receipt.isEmpty()) {
            logger.warn("Receipt not found for recordId: {}", recordId);
        }
        
        logger.info("Found receipt for recordId: {}", recordId);
        return receipt.map(PurchaseRecordDTO::fromDomain);
    }

    public List<PurchaseRecordDTO> getGlobalHistory() {
        return purchaseRecordRepository.findAll().stream().map(PurchaseRecordDTO::fromDomain).collect(Collectors.toList());
    }
}