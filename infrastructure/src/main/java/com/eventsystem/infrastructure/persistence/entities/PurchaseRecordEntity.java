package com.eventsystem.infrastructure.persistence.entities;

import java.time.Instant;
import java.util.List;

import com.eventsystem.domain.purchaserecord.BuyerSnapshot;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_records")
public class PurchaseRecordEntity {

    @Id
    private String recordId;

    private String buyerId;

    @Embedded
    private BuyerSnapshot buyerSnapshot;

    @Embedded
    private EventSnapshot eventSnapshot;

    @ElementCollection
    @CollectionTable(
        name = "purchase_record_items",
        joinColumns = @JoinColumn(name = "record_id")
    )
    private List<PurchasedItem> items;

    @Embedded
    private Money totalPaid;

    @ElementCollection
    @CollectionTable(
        name = "purchase_record_discounts",
        joinColumns = @JoinColumn(name = "record_id")
    )
    private List<DiscountSnapshot> discountsApplied;

    private Instant purchaseTimestamp;

    private String paymentConfirmationId;

    private String ticketIssuanceConfirmationId;

    public PurchaseRecordEntity() {}

    // ---------------- GETTERS ----------------

    public String getRecordId() {
        return recordId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public BuyerSnapshot getBuyerSnapshot() {
        return buyerSnapshot;
    }

    public EventSnapshot getEventSnapshot() {
        return eventSnapshot;
    }

    public List<PurchasedItem> getItems() {
        return items;
    }

    public Money getTotalPaid() {
        return totalPaid;
    }

    public List<DiscountSnapshot> getDiscountsApplied() {
        return discountsApplied;
    }

    public Instant getPurchaseTimestamp() {
        return purchaseTimestamp;
    }

    public String getPaymentConfirmationId() {
        return paymentConfirmationId;
    }

    public String getTicketIssuanceConfirmationId() {
        return ticketIssuanceConfirmationId;
    }

    // ---------------- SETTERS ----------------

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public void setBuyerSnapshot(BuyerSnapshot buyerSnapshot) {
        this.buyerSnapshot = buyerSnapshot;
    }

    public void setEventSnapshot(EventSnapshot eventSnapshot) {
        this.eventSnapshot = eventSnapshot;
    }

    public void setItems(List<PurchasedItem> items) {
        this.items = items;
    }

    public void setTotalPaid(Money totalPaid) {
        this.totalPaid = totalPaid;
    }

    public void setDiscountsApplied(List<DiscountSnapshot> discountsApplied) {
        this.discountsApplied = discountsApplied;
    }

    public void setPurchaseTimestamp(Instant purchaseTimestamp) {
        this.purchaseTimestamp = purchaseTimestamp;
    }

    public void setPaymentConfirmationId(String paymentConfirmationId) {
        this.paymentConfirmationId = paymentConfirmationId;
    }

    public void setTicketIssuanceConfirmationId(String ticketIssuanceConfirmationId) {
        this.ticketIssuanceConfirmationId = ticketIssuanceConfirmationId;
    }
}