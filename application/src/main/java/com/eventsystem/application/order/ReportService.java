package com.eventsystem.application.order;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    private final PurchaseRecordRepository purchaseRecordRepository;

    public ReportService(PurchaseRecordRepository purchaseRecordRepository) {
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    public record SalesSummary(String eventId, int totalTicketsSold, BigDecimal totalRevenue) {}

    /**
     * Returns a sales summary for a given event, including total tickets sold and total revenue generated.
     */
    public SalesSummary generateEventSalesReport(String eventId) {
        logger.info("Generating sales report for event: {}", eventId);

        List<PurchaseRecord> eventRecords = purchaseRecordRepository.findByEvent(eventId);

        if (eventRecords.isEmpty()) {
            logger.warn("No purchase records found for event: {}. Report will reflect zero sales.", eventId);
        }

        int totalTickets = eventRecords.stream()
                .flatMap(record -> record.items().stream())
                .mapToInt(PurchasedItem::quantity)
                .sum();

        BigDecimal totalRevenue = eventRecords.stream()
                .map(PurchaseRecord::totalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SalesSummary summary = new SalesSummary(eventId, totalTickets, totalRevenue);

        logger.info("Successfully generated sales report for event: {}. Total Tickets: {}, Total Revenue: {}", 
                eventId, totalTickets, totalRevenue);

        return summary;
    }
}