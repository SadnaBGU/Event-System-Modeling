package com.eventsystem.application.purchaserecorddto;

import java.time.Instant;
import java.util.List;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.shared.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;


public record PurchaseRecordDTO(
    String recordId,
    String buyerId,
    String buyerDisplayName,
    EventSnapshotDTO eventSnapshot,
    List<PurchasedItemDTO> items,
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    Money totalPaid,
    List<DiscountSnapshotDTO> discountsApplied,
    Instant purchaseTimestamp,
    String paymentConfirmationId,
    String ticketIssuanceConfirmationId
) {
    public static PurchaseRecordDTO fromDomain(
            PurchaseRecord purchaseRecord) {
        
        return new PurchaseRecordDTO(
            purchaseRecord.getRecordId(),
            purchaseRecord.getBuyerId(),
            purchaseRecord.getBuyerSnapshot().displayName(),
            EventSnapshotDTO.fromDomain(purchaseRecord.getEventSnapshot()),
            purchaseRecord.getItems().stream().map(PurchasedItemDTO::fromDomain).toList(),
            purchaseRecord.getTotalPaid(),
            purchaseRecord.getDiscountsApplied().stream().map(DiscountSnapshotDTO::fromDomain).toList(),
            Instant.now(),
            purchaseRecord.getPaymentConfirmationId(),
            purchaseRecord.getTicketIssuanceConfirmationId()
        );
    }
}