package com.eventsystem.domain.purchaserecord;

import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class PurchaseRecordTest {

    @Test
    @DisplayName("Factory method create() initializes all fields correctly and sets UUID/Timestamp")
    void create_StaticFactory_InitializesCorrectly() {
        // Mocks for embedded/complex objects to keep the test isolated
        BuyerSnapshot buyerSnapshot = mock(BuyerSnapshot.class);
        EventSnapshot eventSnapshot = mock(EventSnapshot.class);
        PurchasedItem item = mock(PurchasedItem.class);
        DiscountSnapshot discount = mock(DiscountSnapshot.class);
        Money totalPaid = new Money(new BigDecimal("150.00"), "USD");

        PurchaseRecord record = PurchaseRecord.create(
                "buyer-123",
                buyerSnapshot,
                eventSnapshot,
                List.of(item),
                totalPaid,
                List.of(discount),
                "PAY-999",
                "TICK-888"
        );

        assertThat(record.getRecordId()).isNotNull().isNotEqualTo("");
        assertThat(record.getBuyerId()).isEqualTo("buyer-123");
        assertThat(record.getBuyerSnapshot()).isEqualTo(buyerSnapshot);
        assertThat(record.getEventSnapshot()).isEqualTo(eventSnapshot);
        assertThat(record.getItems()).containsExactly(item);
        assertThat(record.getTotalPaid()).isEqualTo(totalPaid);
        assertThat(record.getDiscountsApplied()).containsExactly(discount);
        assertThat(record.getPaymentConfirmationId()).isEqualTo("PAY-999");
        assertThat(record.getTicketIssuanceConfirmationId()).isEqualTo("TICK-888");
        
        // Assert that the timestamp was generated just now
        assertThat(record.getPurchaseTimestamp()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Constructor handles null lists by converting them to empty lists")
    void constructor_WithNullLists_InitializesToEmptyLists() {
        PurchaseRecord record = new PurchaseRecord(
                "rec-1", "buyer-1", null, null, null, null, null, Instant.now(), null, null
        );

        assertThat(record.getItems()).isNotNull().isEmpty();
        assertThat(record.getDiscountsApplied()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Protected no-arg constructor exists for JPA")
    void protectedNoArgConstructor_ExistsForJPA() {
        // Since the test is in the same package, we can invoke the protected constructor
        PurchaseRecord record = new PurchaseRecord();
        assertThat(record.getRecordId()).isNull(); 
    }

    @Test
    @DisplayName("equals() and hashCode() evaluate identity by fields")
    void equalsAndHashCode_WorkCorrectly() {
        Instant now = Instant.now();
        Money total = new Money(BigDecimal.TEN, "USD");

        PurchaseRecord record1 = new PurchaseRecord(
                "rec-1", "buyer-1", null, null, List.of(), total, List.of(), now, "pay-1", "tick-1"
        );
        PurchaseRecord record2 = new PurchaseRecord(
                "rec-1", "buyer-1", null, null, List.of(), total, List.of(), now, "pay-1", "tick-1"
        );
        PurchaseRecord record3 = new PurchaseRecord(
                "rec-3", "buyer-3", null, null, List.of(), total, List.of(), now, "pay-3", "tick-3"
        );

        // Reflexive
        assertThat(record1).isEqualTo(record1);
        
        // Symmetric
        assertThat(record1).isEqualTo(record2);
        assertThat(record2).isEqualTo(record1);
        
        // HashCode
        assertThat(record1.hashCode()).isEqualTo(record2.hashCode());

        // Negative cases
        assertThat(record1).isNotEqualTo(record3);
        assertThat(record1).isNotEqualTo(null);
        assertThat(record1).isNotEqualTo(new Object());
    }

    @Test
    @DisplayName("toString() contains expected key fields")
    void toString_ContainsExpectedFields() {
        PurchaseRecord record = new PurchaseRecord(
                "REC-ID-TEST", "buyer-1", null, null, List.of(), null, List.of(), null, "PAY-ID", "TICK-ID"
        );

        String str = record.toString();
        assertThat(str).contains("REC-ID-TEST")
                       .contains("buyer-1")
                       .contains("PAY-ID")
                       .contains("TICK-ID")
                       .contains("PurchaseRecord");
    }
}