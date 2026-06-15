package com.eventsystem.application.purchaserecorddto;

import java.time.Instant;
import java.util.List;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.application.purchaserecorddto.EventSnapshotDTO;
import com.eventsystem.domain.shared.Money;

import jakarta.persistence.Embedded;


public record PurchaseRecordDTO(
    String recordId,
    String buyerId,
    String buyerDisplayName,
    EventSnapshotDTO eventSnapshot,
    List<PurchasedItemDTO> items,
    Money totalPaid,
    List<DiscountSnapshotDTO> discountsApplied,
    Instant purchaseTimestamp,
    String paymentConfirmationId,
    String ticketIssuanceConfirmationId
) {
    public static PurchaseRecordDTO fromDomain(
            PurchaseRecord purchaseRecord) {
        
        return new PurchaseRecordDTO(
            purchaseRecord.recordId(),
            purchaseRecord.buyerId(),
            purchaseRecord.buyerSnapshot().displayName(),
            EventSnapshotDTO.fromDomain(purchaseRecord.eventSnapshot()),
            purchaseRecord.items().stream().map(PurchasedItemDTO::fromDomain).toList(),
            purchaseRecord.totalPaid(),
            purchaseRecord.discountsApplied().stream().map(DiscountSnapshotDTO::fromDomain).toList(),
            Instant.now(),
            purchaseRecord.paymentConfirmationId(),
            purchaseRecord.ticketIssuanceConfirmationId()
        );
    }
}