package com.eventsystem.application.order;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseHistoryServiceTest {

    @Mock
    private PurchaseRecordRepository purchaseRecordRepository;

    @InjectMocks
    private PurchaseHistoryService purchaseHistoryService;

    private final String BUYER_ID = "buyer-123";

    @Test
    void getHistoryForBuyer_ReturnsSortedRecordsNewestFirst() {
        // Arrange
        PurchaseRecord oldRecord = mock(PurchaseRecord.class);
        when(oldRecord.purchaseTimestamp()).thenReturn(Instant.now().minus(5, ChronoUnit.DAYS));
        
        PurchaseRecord newRecord = mock(PurchaseRecord.class);
        when(newRecord.purchaseTimestamp()).thenReturn(Instant.now());

        when(purchaseRecordRepository.findByBuyer(BUYER_ID)).thenReturn(List.of(oldRecord, newRecord));

        // Act
        List<PurchaseRecord> history = purchaseHistoryService.getHistoryForBuyer(BUYER_ID);

        // Assert
        assertEquals(2, history.size());
        assertEquals(newRecord, history.get(0), "Newest record should be first");
        assertEquals(oldRecord, history.get(1), "Oldest record should be last");
    }

    @Test
    void getReceiptDetails_DelegatesToRepository() {
        // Arrange
        String recordId = "REC-999";
        PurchaseRecord mockRecord = mock(PurchaseRecord.class);
        when(purchaseRecordRepository.findById(recordId)).thenReturn(Optional.of(mockRecord));

        // Act
        Optional<PurchaseRecord> result = purchaseHistoryService.getReceiptDetails(recordId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(mockRecord, result.get());
    }
}