package com.eventsystem.application.order;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.VenueMap;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private IPurchaseRecordRepository purchaseRecordRepository;

    @InjectMocks
    private ReportService reportService;

    private final String EVENT_ID = "EVENT-2026";

    @Test
    void generateEventSalesReport_NoSales_ReturnsZeroedSummary() {
        // Arrange
        when(purchaseRecordRepository.findByEvent(EVENT_ID)).thenReturn(List.of());

        // Act
        ReportService.SalesSummaryDTO summary = reportService.generateEventSalesReport(EVENT_ID);

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
        ReportService.SalesSummaryDTO summary = reportService.generateEventSalesReport(EVENT_ID);

        // Assert
        assertEquals(EVENT_ID, summary.eventId());
        
        // total tickets sold: 2 (from record1) + 3 + 1 (from record2) = 6
        assertEquals(6, summary.totalTicketsSold());
        
        // total revenue: 100.50 + 200.25 = 300.75
        assertEquals(Money.of(new BigDecimal("300.75"), "USD"), summary.totalRevenue());
    }

        @Test
        void generateCompanySalesReport_WithMultipleEvents_CalculatesGrandTotalsAndPerEventSummaries() {
        CompanyId companyId = new CompanyId("COMP-1");
        Event firstEvent = new Event(
            "EVENT-1",
            companyId.value(),
            new EventDetails(
                "Concert A",
                List.of(LocalDateTime.of(2026, 6, 1, 18, 0)),
                "Music",
                "Hall A",
                "Description A"
            ),
            VenueMap.empty()
        );
        Event secondEvent = new Event(
            "EVENT-2",
            companyId.value(),
            new EventDetails(
                "Concert B",
                List.of(LocalDateTime.of(2026, 6, 2, 20, 0)),
                "Music",
                "Hall B",
                "Description B"
            ),
            VenueMap.empty()
        );

        PurchasedItem firstItem = mock(PurchasedItem.class);
        when(firstItem.quantity()).thenReturn(2);
        PurchaseRecord firstRecord = mock(PurchaseRecord.class);
        when(firstRecord.items()).thenReturn(List.of(firstItem));
        when(firstRecord.totalPaid()).thenReturn(Money.of(new BigDecimal("120.00"), "USD"));

        PurchasedItem secondItem = mock(PurchasedItem.class);
        when(secondItem.quantity()).thenReturn(3);
        PurchaseRecord secondRecord = mock(PurchaseRecord.class);
        when(secondRecord.items()).thenReturn(List.of(secondItem));
        when(secondRecord.totalPaid()).thenReturn(Money.of(new BigDecimal("180.00"), "USD"));

        when(purchaseRecordRepository.findByEvent("EVENT-1")).thenReturn(List.of(firstRecord));
        when(purchaseRecordRepository.findByEvent("EVENT-2")).thenReturn(List.of(secondRecord));

        ReportService.CompanySalesReportDTO report = reportService.generateCompanySalesReport(
            companyId,
            List.of(firstEvent, secondEvent)
        );

        assertEquals(companyId.value(), report.companyId());
        assertEquals(new BigDecimal("300.00"), report.grandTotalRevenue());
        assertEquals(2, report.events().size());
        assertEquals("EVENT-1", report.events().get(0).eventId());
        assertEquals("Concert A", report.events().get(0).eventName());
        assertEquals(2, report.events().get(0).ticketsSold());
        assertEquals(new BigDecimal("120.00"), report.events().get(0).revenue());
        assertEquals("EVENT-2", report.events().get(1).eventId());
        assertEquals("Concert B", report.events().get(1).eventName());
        assertEquals(3, report.events().get(1).ticketsSold());
        assertEquals(new BigDecimal("180.00"), report.events().get(1).revenue());
        }

        @Test
        void generateCompanySalesReport_whenArgumentsAreNull_throwsNullPointerException() {
        assertThatThrownBy(() -> reportService.generateCompanySalesReport(null, List.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("companyId");

        assertThatThrownBy(() -> reportService.generateCompanySalesReport(new CompanyId("COMP-1"), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("events");
        }
}