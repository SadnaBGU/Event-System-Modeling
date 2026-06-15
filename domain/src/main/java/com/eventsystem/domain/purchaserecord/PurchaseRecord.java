package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.eventsystem.domain.shared.Money;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
public class PurchaseRecord {

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
    @AttributeOverrides({
        @AttributeOverride(name = "priceAtPurchase.amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "priceAtPurchase.currency", column = @Column(name = "price_currency"))
    })
    private List<PurchasedItem> items;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
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

    // Protected no-arg constructor required by JPA
    protected PurchaseRecord() {
    }

    // All-args constructor matching the original record components
    public PurchaseRecord(
            String recordId,
            String buyerId,
            BuyerSnapshot buyerSnapshot,
            EventSnapshot eventSnapshot,
            List<PurchasedItem> items,
            Money totalPaid,
            List<DiscountSnapshot> discountsApplied,
            Instant purchaseTimestamp,
            String paymentConfirmationId,
            String ticketIssuanceConfirmationId) {
        this.recordId = recordId;
        this.buyerId = buyerId;
        this.buyerSnapshot = buyerSnapshot;
        this.eventSnapshot = eventSnapshot;
        this.items = items != null ? List.copyOf(items) : List.of();
        this.totalPaid = totalPaid;
        this.discountsApplied = discountsApplied != null ? List.copyOf(discountsApplied) : List.of();
        this.purchaseTimestamp = purchaseTimestamp;
        this.paymentConfirmationId = paymentConfirmationId;
        this.ticketIssuanceConfirmationId = ticketIssuanceConfirmationId;
    }

    // Static factory method updated to instantiate the class
    public static PurchaseRecord create(
            String buyerId,
            BuyerSnapshot buyerSnapshot,
            EventSnapshot eventSnapshot,
            List<PurchasedItem> items,
            Money totalPaid,
            List<DiscountSnapshot> discountsApplied,
            String paymentConfirmationId,
            String ticketIssuanceConfirmationId) {
        
        return new PurchaseRecord(
            UUID.randomUUID().toString(),
            buyerId,
            buyerSnapshot,
            eventSnapshot,
            items,
            totalPaid,
            discountsApplied,
            Instant.now(),
            paymentConfirmationId,
            ticketIssuanceConfirmationId
        );
    }

    // --- Getters (Record-style naming or standard JavaBean prefixes) ---

    public String getRecordId() { return recordId; }
    public String getBuyerId() { return buyerId; }
    public BuyerSnapshot getBuyerSnapshot() { return buyerSnapshot; }
    public EventSnapshot getEventSnapshot() { return eventSnapshot; }
    public List<PurchasedItem> getItems() { return items; }
    public Money getTotalPaid() { return totalPaid; }
    public List<DiscountSnapshot> getDiscountsApplied() { return discountsApplied; }
    public Instant getPurchaseTimestamp() { return purchaseTimestamp; }
    public String getPaymentConfirmationId() { return paymentConfirmationId; }
    public String getTicketIssuanceConfirmationId() { return ticketIssuanceConfirmationId; }

    // --- Equals, HashCode, and ToString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PurchaseRecord that = (PurchaseRecord) o;
        return Objects.equals(recordId, that.recordId) &&
               Objects.equals(buyerId, that.buyerId) &&
               Objects.equals(buyerSnapshot, that.buyerSnapshot) &&
               Objects.equals(eventSnapshot, that.eventSnapshot) &&
               Objects.equals(items, that.items) &&
               Objects.equals(totalPaid, that.totalPaid) &&
               Objects.equals(discountsApplied, that.discountsApplied) &&
               Objects.equals(purchaseTimestamp, that.purchaseTimestamp) &&
               Objects.equals(paymentConfirmationId, that.paymentConfirmationId) &&
               Objects.equals(ticketIssuanceConfirmationId, that.ticketIssuanceConfirmationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId, buyerId, buyerSnapshot, eventSnapshot, items, totalPaid, 
                            discountsApplied, purchaseTimestamp, paymentConfirmationId, ticketIssuanceConfirmationId);
    }

    @Override
    public String toString() {
        return "PurchaseRecord[" +
               "recordId='" + recordId + '\'' +
               ", buyerId='" + buyerId + '\'' +
               ", buyerSnapshot=" + buyerSnapshot +
               ", eventSnapshot=" + eventSnapshot +
               ", items=" + items +
               ", totalPaid=" + totalPaid +
               ", discountsApplied=" + discountsApplied +
               ", purchaseTimestamp=" + purchaseTimestamp +
               ", paymentConfirmationId='" + paymentConfirmationId + '\'' +
               ", ticketIssuanceConfirmationId='" + ticketIssuanceConfirmationId + '\'' +
               ']';
    }
}