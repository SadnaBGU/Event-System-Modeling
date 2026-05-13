package com.eventsystem.application.order;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private PurchaseRecordRepository purchaseRecordRepository;

    @InjectMocks
    private ReportService reportService;

    private final String EVENT_ID = "EVENT-2026";

    @Test
    void generateEventSalesReport_NoSales_ReturnsZeroedSummary() {
        // Arrange
        when(purchaseRecordRepository.findByEvent(EVENT_ID)).thenReturn(List.of());

        // Act
        ReportService.SalesSummary summary = reportService.generateEventSalesReport(EVENT_ID);

        // Assert
        assertEquals(EVENT_ID, summary.eventId());
        assertEquals(0, summary.totalTicketsSold());
        assertEquals(Money.of(BigDecimal.ZERO, "USD"), summary.totalRevenue());
    }

    @Test
    void generateEventSalesReport_WithMultipleSales_CalculatesTotalsCorrectly() {
        // Arrange
        PurchasedItem item1 = mock(PurchasedItem.class);
        when(item1.quantity()).thenReturn(2);
        when(item1.priceAtPurchase()).thenReturn(Money.of(new BigDecimal("50.25"), "USD"));
        
        PurchaseRecord record1 = mock(PurchaseRecord.class);
        when(record1.items()).thenReturn(List.of(item1));
        when(record1.totalPaid()).thenReturn(Money.of(new BigDecimal("100.50"), "USD"));

        PurchasedItem item2a = mock(PurchasedItem.class);
        when(item2a.quantity()).thenReturn(3);
        PurchasedItem item2b = mock(PurchasedItem.class);
        when(item2b.quantity()).thenReturn(1);
        
        PurchaseRecord record2 = mock(PurchaseRecord.class);
        when(record2.items()).thenReturn(List.of(item2a, item2b));
        when(record2.totalPaid()).thenReturn(Money.of(new BigDecimal("200.25"), "USD"));

        when(purchaseRecordRepository.findByEvent(EVENT_ID)).thenReturn(List.of(record1, record2));

        // Act
        ReportService.SalesSummary summary = reportService.generateEventSalesReport(EVENT_ID);

        // Assert
        assertEquals(EVENT_ID, summary.eventId());
        
        // total tickets sold: 2 (from record1) + 3 + 1 (from record2) = 6
        assertEquals(6, summary.totalTicketsSold());
        
        // total revenue: 100.50 + 200.25 = 300.75
        assertEquals(Money.of(new BigDecimal("300.75"), "USD"), summary.totalRevenue());
    }
}