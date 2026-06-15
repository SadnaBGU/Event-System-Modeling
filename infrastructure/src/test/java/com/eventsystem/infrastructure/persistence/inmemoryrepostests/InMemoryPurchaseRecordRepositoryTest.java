package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPurchaseRecordRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryPurchaseRecordRepositoryTest {

    private InMemoryPurchaseRecordRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchaseRecordRepository();
    }

    @Test
    void append_PreventsOverwritingExistingRecord() {
        // Arrange
        String sharedRecordId = "REC-111";
        
        PurchaseRecord originalRecord = mock(PurchaseRecord.class);
        when(originalRecord.getRecordId()).thenReturn(sharedRecordId);
        
        PurchaseRecord fakeDuplicateRecord = mock(PurchaseRecord.class);
        when(fakeDuplicateRecord.getRecordId()).thenReturn(sharedRecordId);

        // Act
        repository.append(originalRecord);
        repository.append(fakeDuplicateRecord);

        // Assert
        Optional<PurchaseRecord> result = repository.findById(sharedRecordId);
        assertTrue(result.isPresent());
        assertEquals(originalRecord, result.get(), "The original record should not be overwritten!");
    }

    @Test
    void findByBuyer_ReturnsOnlyBuyersRecords() {
        // Arrange
        PurchaseRecord myRecord = mock(PurchaseRecord.class);
        when(myRecord.getRecordId()).thenReturn("REC-1");
        when(myRecord.getBuyerId()).thenReturn("DAVID");

        PurchaseRecord otherRecord = mock(PurchaseRecord.class);
        when(otherRecord.getRecordId()).thenReturn("REC-2");
        when(otherRecord.getBuyerId()).thenReturn("MOSHE");

        repository.append(myRecord);
        repository.append(otherRecord);

        // Act
        List<PurchaseRecord> results = repository.findByBuyer("DAVID");

        // Assert
        assertEquals(1, results.size());
        assertEquals("REC-1", results.get(0).getRecordId());
    }

    @Test
    void findByEvent_ReturnsOnlyEventRecords() {
        // Arrange
        EventSnapshot eventA = new EventSnapshot("EVENT-A", "Rock Fest", "LiveNation", null, "Park");
        EventSnapshot eventB = new EventSnapshot("EVENT-B", "Jazz Fest", "LiveNation", null, "Club");

        PurchaseRecord recordEventA = mock(PurchaseRecord.class);
        when(recordEventA.getRecordId()).thenReturn("REC-A");
        when(recordEventA.getEventSnapshot()).thenReturn(eventA);

        PurchaseRecord recordEventB = mock(PurchaseRecord.class);
        when(recordEventB.getRecordId()).thenReturn("REC-B");
        when(recordEventB.getEventSnapshot()).thenReturn(eventB);

        repository.append(recordEventA);
        repository.append(recordEventB);

        // Act
        List<PurchaseRecord> results = repository.findByEvent("EVENT-A");

        // Assert
        assertEquals(1, results.size());
        assertEquals("REC-A", results.get(0).getRecordId());
    }
}