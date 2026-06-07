package com.eventsystem.application.order;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.purchaserecord.IPurchaseRecordRepository;
import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;
import com.eventsystem.domain.shared.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final IPurchaseRecordRepository purchaseRecordRepository;

    public ReportService(IPurchaseRecordRepository purchaseRecordRepository) {
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    public record SalesSummaryDTO(String eventId, int totalTicketsSold, Money totalRevenue) {}

        public record EventSalesSummaryDTO(String eventId, String eventName, int ticketsSold, BigDecimal revenue) {}

        public record CompanySalesReportDTO(
            String companyId,
            Instant reportGeneratedAt,
            BigDecimal grandTotalRevenue,
            List<EventSalesSummaryDTO> events) {}

    /**
     * Returns a sales summary for a given event, including total tickets sold and total revenue generated.
     */
    public SalesSummaryDTO generateEventSalesReport(String eventId) {
        logger.info("Generating sales report for event: {}", eventId);

        List<PurchaseRecord> eventRecords = purchaseRecordRepository.findByEvent(eventId);

        if (eventRecords.isEmpty()) {
            logger.warn("No purchase records found for event: {}. Report will reflect zero sales.", eventId);
        }

        int totalTickets = eventRecords.stream()
                .flatMap(record -> record.items().stream())
                .mapToInt(PurchasedItem::quantity)
                .sum();
        
        String currency = eventRecords.stream()
                .flatMap(record -> record.items().stream())
                .map(item -> item.priceAtPurchase().currency())
                .findFirst()
                .orElse("USD"); // Default to USD if no records found

        Money totalRevenue = eventRecords.stream()
                .map(PurchaseRecord::totalPaid)
                .reduce(Money.of(BigDecimal.ZERO, currency), Money::add);

        SalesSummaryDTO summary = new SalesSummaryDTO(eventId, totalTickets, totalRevenue);

        logger.info("Successfully generated sales report for event: {}. Total Tickets: {}, Total Revenue: {}", 
                eventId, totalTickets, totalRevenue);

        return summary;
    }

    public CompanySalesReportDTO generateCompanySalesReport(CompanyId companyId, List<Event> events) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(events, "events must not be null");

        List<EventSalesSummaryDTO> eventSummaries = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Event event : events) {
            List<PurchaseRecord> eventRecords = purchaseRecordRepository.findByEvent(event.id().value());
            BigDecimal eventRevenue = eventRecords.stream()
                    .map(record -> record.totalPaid().amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int ticketsSold = eventRecords.stream()
                    .flatMap(record -> record.items().stream())
                    .mapToInt(PurchasedItem::quantity)
                    .sum();

            grandTotal = grandTotal.add(eventRevenue);
            eventSummaries.add(new EventSalesSummaryDTO(
                    event.id().value(),
                    event.details().name(),
                    ticketsSold,
                    eventRevenue
            ));
        }

        return new CompanySalesReportDTO(
                companyId.value(),
                Instant.now(),
                grandTotal,
                eventSummaries
        );
    }
}