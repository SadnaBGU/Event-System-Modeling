package com.eventsystem.application.acceptance;

import com.eventsystem.application.order.ReportService;
import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.domain.order.BuyerReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for purchase-history and reporting read models:
 *   UC 12 - View Personal Purchase History
 *   UC 18 - Generate Sales Report / Purchase History
 *   UC 23 - View Global Purchase History
 *
 * Each test drives a real checkout (UC 9 flow) so the purchase records under
 * test are produced by the actual purchase pipeline, then queries the history
 * and report services.
 */
class UC12_18_23_HistoryAndReportsAcceptanceTest {

    /** Runs a successful standing-zone checkout, leaving one purchase record behind. */
    private void checkout(
            ApplicationAcceptanceFixture app,
            String buyerId,
            String eventId,
            String zoneId,
            int quantity,
            String unitPrice) {
        BuyerReference buyer = app.memberBuyer(buyerId);
        app.createStandingZone(eventId, zoneId, "General", unitPrice, 100);
        String orderId = app.createStrictOrder(buyer, eventId).orderId();
        app.reserveStanding(orderId, zoneId, quantity);
        app.checkoutSaga.executeCheckout(orderId, "payment-token", null);
    }

    // REQ: PERS-05
    // UC: UC 12 - View Personal Purchase History
    // UAT: UAT-35 - View History (Data)
    @Test
    void memberWithPastPurchase_seesItInPersonalHistory() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        checkout(app, "buyer-1", "event-1", "zone-a", 2, "50.00");

        List<PurchaseRecordDTO> history = app.purchaseHistoryService.getHistoryForBuyer("buyer-1");

        assertThat(history).hasSize(1);
    }

    // REQ: PERS-05
    // UC: UC 12 - View Personal Purchase History
    // UAT: UAT-36 - View History (Empty)
    @Test
    void memberWithNoPurchases_seesEmptyHistory() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        checkout(app, "buyer-1", "event-1", "zone-a", 1, "50.00");

        // A different member has no purchases of their own.
        assertThat(app.purchaseHistoryService.getHistoryForBuyer("buyer-without-purchases")).isEmpty();
    }

    // REQ: PRD-13
    // UC: UC 18 - Generate Sales Report
    // UAT: UAT-55 - Report With Data
    @Test
    void salesReportForEventWithSales_reportsTicketCountAndRevenue() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        checkout(app, "buyer-1", "event-1", "zone-a", 3, "50.00");

        ReportService.SalesSummaryDTO report = app.reportService.generateEventSalesReport("event-1");

        assertThat(report.eventId()).isEqualTo("event-1");
        assertThat(report.totalTicketsSold()).isEqualTo(3);
        assertThat(report.totalRevenue().amount()).isEqualByComparingTo("150.00");
    }

    // REQ: PRD-13
    // UC: UC 18 - Generate Sales Report
    // UAT: UAT-56 - Report Empty
    @Test
    void salesReportForEventWithNoSales_reportsZero() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        ReportService.SalesSummaryDTO report = app.reportService.generateEventSalesReport("event-with-no-sales");

        assertThat(report.totalTicketsSold()).isZero();
        assertThat(report.totalRevenue().amount()).isEqualByComparingTo("0.00");
    }

    // REQ: ADM-04
    // UC: UC 23 - View Global Purchase History
    // UAT: UAT-68 - Global History Query
    @Test
    void globalHistory_returnsRecordsAcrossAllBuyers() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        checkout(app, "buyer-1", "event-1", "zone-a", 1, "50.00");
        checkout(app, "buyer-2", "event-2", "zone-b", 1, "75.00");

        assertThat(app.purchaseHistoryService.getGlobalHistory()).hasSize(2);
    }

    // REQ: ADM-04
    // UC: UC 23 - View Global Purchase History
    // UAT: UAT-69 - Global History Empty
    @Test
    void globalHistory_whenNoPurchasesExist_isEmpty() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        assertThat(app.purchaseHistoryService.getGlobalHistory()).isEmpty();
    }
}