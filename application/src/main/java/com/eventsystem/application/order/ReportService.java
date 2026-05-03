package com.eventsystem.application.order;

import com.eventsystem.domain.purchaserecord.PurchaseRecord;
import com.eventsystem.domain.purchaserecord.PurchasedItem;

import java.math.BigDecimal;
import java.util.List;

public class ReportService {

    private final PurchaseRecordRepository purchaseRecordRepository;

    public ReportService(PurchaseRecordRepository purchaseRecordRepository) {
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    public record SalesSummary(String eventId, int totalTicketsSold, BigDecimal totalRevenue) {}

    /**
     * Returns a sales summary for a given event, including total tickets sold and total revenue generated.
     */
    public SalesSummary generateEventSalesReport(String eventId) {
        List<PurchaseRecord> eventRecords = purchaseRecordRepository.findByEvent(eventId);

        int totalTickets = eventRecords.stream()
                .flatMap(record -> record.items().stream())
                .mapToInt(PurchasedItem::quantity)
                .sum();

        BigDecimal totalRevenue = eventRecords.stream()
                .map(PurchaseRecord::totalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalesSummary(eventId, totalTickets, totalRevenue);
    }
}