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

    // מתודת עזר לייצור Mock אחיד של סנאפשוטים כדי למנוע NullPointer ב-DTO mapping
    private void setupSnapshotsForRecord(PurchaseRecord mockRecord) {
        BuyerSnapshot bs = mock(BuyerSnapshot.class);
        EventSnapshot es = mock(EventSnapshot.class);
        lenient().when(mockRecord.getBuyerSnapshot()).thenReturn(bs);
        lenient().when(mockRecord.getEventSnapshot()).thenReturn(es);
        lenient().when(mockRecord.getItems()).thenReturn(List.of());
        lenient().when(mockRecord.getDiscountsApplied()).thenReturn(List.of());
    }

    @Test
    void getHistoryForBuyer_ReturnsSortedHistory() {
        // Arrange
        PurchaseRecord rec1 = mock(PurchaseRecord.class);
        PurchaseRecord rec2 = mock(PurchaseRecord.class);
        
        setupSnapshotsForRecord(rec1);
        setupSnapshotsForRecord(rec2);
        
        lenient().when(rec1.getRecordId()).thenReturn("REC-1");
        lenient().when(rec2.getRecordId()).thenReturn("REC-2");
        
        Instant now = Instant.now();
        // rec1 is older, rec2 is newer
        lenient().when(rec1.getPurchaseTimestamp()).thenReturn(now.minus(2, ChronoUnit.DAYS));
        lenient().when(rec2.getPurchaseTimestamp()).thenReturn(now.minus(1, ChronoUnit.DAYS));

        // Repository returns them in unsorted order
        when(purchaseRecordRepository.findByBuyer(BUYER_ID)).thenReturn(List.of(rec1, rec2));

        // Act
        List<PurchaseRecordDTO> history = purchaseHistoryService.getHistoryForBuyer(BUYER_ID);

        // Assert
        assertEquals(2, history.size());
        assertEquals("REC-2", history.get(0).recordId(), "Newest record should be first");
        assertEquals("REC-1", history.get(1).recordId(), "Oldest record should be last");
    }

    @Test
    void getReceiptDetails_DelegatesToRepository() {
        // Arrange
        String recordId = "REC-999";
        PurchaseRecord mockRecord = mock(PurchaseRecord.class);
        lenient().when(mockRecord.getRecordId()).thenReturn(recordId);
        setupSnapshotsForRecord(mockRecord);
        
        when(purchaseRecordRepository.findById(recordId)).thenReturn(Optional.of(mockRecord));

        // Act
        Optional<PurchaseRecordDTO> result = purchaseHistoryService.getReceiptDetails(recordId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(recordId, result.get().recordId());
    }

    @Test
    void getReceiptDetails_Empty_LogsWarningAndReturnsEmpty() {
        // Arrange
        String recordId = "REC-NOT-FOUND";
        when(purchaseRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        // Act
        Optional<PurchaseRecordDTO> result = purchaseHistoryService.getReceiptDetails(recordId);

        // Assert
        assertTrue(result.isEmpty());
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