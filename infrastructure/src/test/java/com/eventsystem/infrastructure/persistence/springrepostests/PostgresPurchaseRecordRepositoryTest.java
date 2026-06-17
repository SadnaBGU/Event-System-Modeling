package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.purchaserecord.BuyerSnapshot;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchaseRecordRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresPurchaseRecordRepository.class)
class PostgresPurchaseRecordRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresPurchaseRecordRepository purchaseRecordRepository;

    @Autowired
    private EntityManager em;

    @Test
    void saveAndFindById_persistsPurchaseRecord() {
        PurchaseRecord record = record("record-1");

        purchaseRecordRepository.save(record);
        em.flush();
        em.clear();

        PurchaseRecord found = purchaseRecordRepository.findById("record-1").orElseThrow();

        assertThat(found.getRecordId()).isEqualTo("record-1");
        assertThat(found.getBuyerId()).isEqualTo("buyer-1");
        assertThat(found.getTotalPaid()).isEqualTo(Money.of(new BigDecimal("85.00"), "ILS"));
        assertThat(found.getPaymentConfirmationId()).isEqualTo("pay-record-1");
    }

    @Test
    void findById_returnsEmptyForMissingRecord() {
        assertThat(purchaseRecordRepository.findById("missing-record")).isEmpty();
    }

    @Test
    void loadSnapshots_restoresBuyerEventAndDiscountSnapshots() {
        PurchaseRecord record = record("record-2");
        purchaseRecordRepository.save(record);
        em.flush();
        em.clear();

        PurchaseRecord found = purchaseRecordRepository.findById("record-2").orElseThrow();

        assertThat(found.getBuyerSnapshot()).isEqualTo(new BuyerSnapshot("Dana Buyer"));
        assertThat(found.getEventSnapshot()).isEqualTo(new EventSnapshot(
                "event-1",
                "Jazz Night",
                "Acme Productions",
                LocalDate.of(2026, 7, 20),
                "Main Hall"
        ));
        assertThat(found.getDiscountsApplied())
                .containsExactly(new DiscountSnapshot("student", Money.of(new BigDecimal("10.00"), "ILS")));
    }

    @Test
    void loadPurchasedItems_restoresAllPurchasedItemRows() {
        PurchaseRecord record = record("record-3");
        purchaseRecordRepository.save(record);
        em.flush();
        em.clear();

        PurchaseRecord found = purchaseRecordRepository.findById("record-3").orElseThrow();

        assertThat(found.getItems()).containsExactly(
                new PurchasedItem("Balcony", "B-12", 1, Money.of(new BigDecimal("40.00"), "ILS")),
                new PurchasedItem("Floor", null, 2, Money.of(new BigDecimal("45.00"), "ILS"))
        );
    }

    @Test
    void findByBuyer_returnsHistoryNewestFirstForThatBuyerOnly() {
        purchaseRecordRepository.save(record("old-record", "buyer-history", "event-1", Instant.parse("2026-06-16T10:00:00Z")));
        purchaseRecordRepository.save(record("new-record", "buyer-history", "event-2", Instant.parse("2026-06-16T12:00:00Z")));
        purchaseRecordRepository.save(record("other-buyer-record", "other-buyer", "event-1", Instant.parse("2026-06-16T13:00:00Z")));
        em.flush();
        em.clear();

        List<PurchaseRecord> history = purchaseRecordRepository.findByBuyer("buyer-history");

        assertThat(history)
                .extracting(PurchaseRecord::getRecordId)
                .containsExactly("new-record", "old-record");
    }

    @Test
    void findByEvent_returnsRecordsForThatEventOnly() {
        purchaseRecordRepository.save(record("event-record-1", "buyer-1", "event-shared", Instant.parse("2026-06-16T10:00:00Z")));
        purchaseRecordRepository.save(record("event-record-2", "buyer-2", "event-shared", Instant.parse("2026-06-16T11:00:00Z")));
        purchaseRecordRepository.save(record("event-record-other", "buyer-3", "event-other", Instant.parse("2026-06-16T12:00:00Z")));
        em.flush();
        em.clear();

        List<PurchaseRecord> eventRecords = purchaseRecordRepository.findByEvent("event-shared");

        assertThat(eventRecords)
                .extracting(PurchaseRecord::getRecordId)
                .containsExactlyInAnyOrder("event-record-1", "event-record-2");
    }

    @Test
    void findByPaymentConfirmation_returnsMatchingRecord() {
        purchaseRecordRepository.save(record("payment-record"));
        em.flush();
        em.clear();

        PurchaseRecord found = purchaseRecordRepository.findByPaymentConfirmation("pay-payment-record").orElseThrow();

        assertThat(found.getRecordId()).isEqualTo("payment-record");
    }

    @Test
    void appendAndFindAll_persistRecordsThroughPortMethod() {
        purchaseRecordRepository.append(record("append-record-1"));
        purchaseRecordRepository.append(record("append-record-2"));
        em.flush();
        em.clear();

        assertThat(purchaseRecordRepository.findAll())
                .extracting(PurchaseRecord::getRecordId)
                .contains("append-record-1", "append-record-2");
    }

    private static PurchaseRecord record(String recordId) {
        return record(recordId, "buyer-1", "event-1", Instant.parse("2026-06-16T10:00:00Z"));
    }

    private static PurchaseRecord record(String recordId, String buyerId, String eventId, Instant purchaseTimestamp) {
        return new PurchaseRecord(
                recordId,
                buyerId,
                new BuyerSnapshot("Dana Buyer"),
                new EventSnapshot(eventId, "Jazz Night", "Acme Productions", LocalDate.of(2026, 7, 20), "Main Hall"),
                List.of(
                        new PurchasedItem("Balcony", "B-12", 1, Money.of(new BigDecimal("40.00"), "ILS")),
                        new PurchasedItem("Floor", null, 2, Money.of(new BigDecimal("45.00"), "ILS"))
                ),
                Money.of(new BigDecimal("85.00"), "ILS"),
                List.of(new DiscountSnapshot("student", Money.of(new BigDecimal("10.00"), "ILS"))),
                purchaseTimestamp,
                "pay-" + recordId,
                "ticket-" + recordId
        );
    }
}
