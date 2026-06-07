package com.eventsystem.application.order;

import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.domain.purchaserecord.BuyerSnapshot;
import com.eventsystem.domain.purchaserecord.EventSnapshot;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseHistoryServiceTest {

    @Mock
    private IPurchaseRecordRepository purchaseRecordRepository;

    @InjectMocks
    private PurchaseHistoryService purchaseHistoryService;

    private final String BUYER_ID = "buyer-123";

    // מתודת עזר לייצור Mock אחיד של סנאפשוטים כדי למנוע NPE
    private void setupSnapshotsForRecord(PurchaseRecord record) {
        BuyerSnapshot dummyBuyerSnapshot = mock(BuyerSnapshot.class);
        lenient().when(dummyBuyerSnapshot.displayName()).thenReturn("John Doe");

        EventSnapshot dummyEventSnapshot = mock(EventSnapshot.class);
        lenient().when(dummyEventSnapshot.eventId()).thenReturn("EVENT-999");

        lenient().when(record.buyerSnapshot()).thenReturn(dummyBuyerSnapshot);
        lenient().when(record.eventSnapshot()).thenReturn(dummyEventSnapshot);
    }

    @Test
    void getHistoryForBuyer_ReturnsSortedRecordsNewestFirst() {
        // Arrange
        PurchaseRecord oldRecord = mock(PurchaseRecord.class);
        lenient().when(oldRecord.recordId()).thenReturn("REC-OLD");
        setupSnapshotsForRecord(oldRecord);
        when(oldRecord.purchaseTimestamp()).thenReturn(Instant.now().minus(5, ChronoUnit.DAYS));
        
        PurchaseRecord newRecord = mock(PurchaseRecord.class);
        lenient().when(newRecord.recordId()).thenReturn("REC-NEW");
        setupSnapshotsForRecord(newRecord);
        when(newRecord.purchaseTimestamp()).thenReturn(Instant.now());

        when(purchaseRecordRepository.findByBuyer(BUYER_ID)).thenReturn(List.of(oldRecord, newRecord));

        // Act
        List<PurchaseRecordDTO> history = purchaseHistoryService.getHistoryForBuyer(BUYER_ID);

        // Assert
        assertEquals(2, history.size());
        // משווים את ה-ID של ה-DTO מול ה-ID של הרשומה המקורית
        assertEquals("REC-NEW", history.get(0).recordId(), "Newest record should be first");
        assertEquals("REC-OLD", history.get(1).recordId(), "Oldest record should be last");
    }

    @Test
    void getReceiptDetails_DelegatesToRepository() {
        // Arrange
        String recordId = "REC-999";
        PurchaseRecord mockRecord = mock(PurchaseRecord.class);
        lenient().when(mockRecord.recordId()).thenReturn(recordId);
        setupSnapshotsForRecord(mockRecord);
        
        when(purchaseRecordRepository.findById(recordId)).thenReturn(Optional.of(mockRecord));

        // Act
        Optional<PurchaseRecordDTO> result = purchaseHistoryService.getReceiptDetails(recordId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(recordId, result.get().recordId());
    }

    @Test
    void getGlobalHistory_ReturnsAllRecords() {
        // Arrange - UAT 68
        PurchaseRecord record = mock(PurchaseRecord.class);
        setupSnapshotsForRecord(record);
        when(purchaseRecordRepository.findAll()).thenReturn(List.of(record));

        // Act
        List<PurchaseRecordDTO> history = purchaseHistoryService.getGlobalHistory();

        // Assert
        assertEquals(1, history.size());
    }

    @Test
    void getGlobalHistory_Empty_ReturnsEmptyList() {
        // Arrange - UAT 69
        when(purchaseRecordRepository.findAll()).thenReturn(List.of());

        // Act
        List<PurchaseRecordDTO> history = purchaseHistoryService.getGlobalHistory();

        // Assert
        assertTrue(history.isEmpty());
    }
}