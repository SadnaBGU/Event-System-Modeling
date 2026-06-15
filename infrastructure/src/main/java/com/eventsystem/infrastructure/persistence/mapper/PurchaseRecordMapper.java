package com.eventsystem.infrastructure.persistence.mapper;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.infrastructure.persistence.entities.PurchaseRecordEntity;

public class PurchaseRecordMapper {

    public static PurchaseRecordEntity toEntity(PurchaseRecord record) {
        PurchaseRecordEntity entity = new PurchaseRecordEntity();

        entity.setRecordId(record.recordId());
        entity.setBuyerId(record.buyerId());

        entity.setBuyerSnapshot(record.buyerSnapshot());
        entity.setEventSnapshot(record.eventSnapshot());

        entity.setItems(record.items());

        entity.setTotalPaid(record.totalPaid());

        entity.setDiscountsApplied(record.discountsApplied());

        entity.setPurchaseTimestamp(record.purchaseTimestamp());
        entity.setPaymentConfirmationId(record.paymentConfirmationId());
        entity.setTicketIssuanceConfirmationId(record.ticketIssuanceConfirmationId());

        return entity;
    }

    public static PurchaseRecord toDomain(PurchaseRecordEntity entity) {
        return new PurchaseRecord(
            entity.getRecordId(),
            entity.getBuyerId(),
            entity.getBuyerSnapshot(),
            entity.getEventSnapshot(),
            entity.getItems(),
            entity.getTotalPaid(),
            entity.getDiscountsApplied(),
            entity.getPurchaseTimestamp(),
            entity.getPaymentConfirmationId(),
            entity.getTicketIssuanceConfirmationId()
        );
    }
}