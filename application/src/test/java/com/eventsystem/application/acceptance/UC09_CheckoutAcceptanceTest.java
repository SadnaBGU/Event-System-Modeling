package com.eventsystem.application.acceptance;

import com.eventsystem.application.appexceptions.IssuanceFailedException;
import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.appexceptions.PaymentFailedException;
import com.eventsystem.application.order.CheckoutResult;
import com.eventsystem.application.order.TicketIssuanceItem;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.zone.SeatStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UC09_CheckoutAcceptanceTest {

    // REQ: USR-10, INV-10, INV-11
    // UC: UC 9 - Checkout and Payment Completion
    // UAT: UAT-26 - Successful Checkout
    @Test
    void checkout_withValidPaymentAndTicketIssuance_marksInventorySoldCreatesReceiptAndSendsSuccessNotification() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createStrictOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");

        CheckoutResult result = app.checkoutSaga.executeCheckout(orderId, "payment-token", null);

        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CHECKED_OUT.name());
        assertThat(result.totalPaid().amount()).isEqualByComparingTo("100.00");
        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.CHECKED_OUT);
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.SOLD);
        assertThat(app.purchaseRecords.findAll()).hasSize(1);
        assertThat(app.payment.charges).isEqualTo(1);
        assertThat(app.payment.refunds).isZero();
        assertThat(app.ticketing.attempts).isEqualTo(1);
        assertThat(app.notifications.purchaseSuccesses).hasSize(1);
        assertThat(app.notifications.purchaseFailures).isEmpty();
    }

    // REQ: USR-10, INV-10, INV-11
    // UC: UC 9 - Checkout and Payment Completion
    // UAT: UAT-26 - Successful Checkout
    // Regression: successful checkout must calculate total price by summing
    // quantity * unit price
    // across multiple tickets from multiple zones.
    @Test
    void checkout_withMultipleTicketsFromMultipleZones_chargesCorrectTotalAndSellsAllInventory() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        BuyerReference buyer = app.memberBuyer("buyer-1");
        EventId eventId = app.eventId("event-multi-zone-multi-quantity-success");

        /*
         * Standing zones are the cleanest way to test quantity > 1 from the same zone.
         *
         * VIP: 2 tickets × 100 = 200
         * Balcony: 3 tickets × 50 = 150
         * Expected total: 350
         */
        app.createStandingZone(eventId.value(), "zone-vip", "VIP", "100.00", 10);
        app.createStandingZone(eventId.value(), "zone-balcony", "Balcony", "50.00", 10);

        String orderId = app.createStrictOrder(buyer, eventId.value()).orderId();

        app.reserveStanding(orderId, "zone-vip", 2);
        app.reserveStanding(orderId, "zone-balcony", 3);

        assertThat(app.order(orderId).getItems()).hasSize(2);
        assertThat(app.order(orderId).calculateBaseTotal().amount())
                .isEqualByComparingTo("350.00");

        CheckoutResult result = app.checkoutSaga.executeCheckout(orderId, "payment-token", null);

        assertThat(result).isNotNull();

        assertThat(app.payment.charges).isEqualTo(1);
        assertThat(app.payment.lastChargedAmount.amount())
                .isEqualByComparingTo("350.00");

        assertThat(app.ticketing.attempts).isEqualTo(1);
        assertThat(app.ticketing.lastIssuedItems).hasSize(2);
        assertThat(app.ticketing.lastIssuedItems.stream().mapToInt(TicketIssuanceItem::quantity).sum())
                .isEqualTo(5);

        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.CHECKED_OUT);

        /*
         * Standing zones only track availableCount.
         * After successful checkout, reservations become sold, but available count
         * should remain reduced.
         */
        assertThat(app.zone("zone-vip").totalCapacity()).isEqualTo(10);
        assertThat(app.zone("zone-vip").getAvailableCount()).isEqualTo(8);
        assertThat(app.zone("zone-vip").totalCapacity() - app.zone("zone-vip").getAvailableCount())
                .isEqualTo(2);

        assertThat(app.zone("zone-balcony").totalCapacity()).isEqualTo(10);
        assertThat(app.zone("zone-balcony").getAvailableCount()).isEqualTo(7);
        assertThat(app.zone("zone-balcony").totalCapacity() - app.zone("zone-balcony").getAvailableCount())
                .isEqualTo(3);

        assertThat(app.purchaseRecords.findAll()).hasSize(1);
        assertThat(app.purchaseRecords.findAll().get(0).getItems()).hasSize(2);
        assertThat(app.purchaseRecords.findAll().get(0).getTotalPaid().amount())
                .isEqualByComparingTo("350.00");

        assertThat(app.notifications.purchaseSuccesses).hasSize(1);
        assertThat(app.notifications.purchaseFailures).isEmpty();
    }

    // REQ: USR-10, PP-07, INV-10
    // UC: UC 9 - Checkout and Payment Completion
    // UAT: UAT-27 - Checkout Policy Violation
    @Test
    void checkout_whenPurchasePolicyViolated_doesNotChargeDoesNotIssueTicketsAndKeepsOrderActive() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createStrictOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");
        app.purchasePolicies.result = PolicyValidationResult.failure("max 4 tickets");

        assertThatThrownBy(() -> app.checkoutSaga.executeCheckout(orderId, "payment-token", null))
                .isInstanceOf(OrderViolatesPolicyException.class)
                .hasMessageContaining("max 4 tickets");

        assertThat(app.payment.charges).isZero();
        assertThat(app.payment.refunds).isZero();
        assertThat(app.ticketing.attempts).isZero();
        assertThat(app.purchaseRecords.findAll()).isEmpty();
        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.ACTIVE);
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
    }

    // REQ: USR-10, INV-10
    // UC: UC 9 - Checkout and Payment Completion
    // UAT: UAT-28 - Payment Declined
    @Test
    void checkout_whenPaymentDeclined_doesNotIssueTicketsDoesNotCreateReceiptAndOrderRemainsActive() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createStrictOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");
        app.payment.mode = ApplicationAcceptanceFixture.FakePaymentGatewayPort.Mode.DECLINED;

        assertThatThrownBy(() -> app.checkoutSaga.executeCheckout(orderId, "bad-card", null))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("card declined");

        assertThat(app.payment.charges).isEqualTo(1);
        assertThat(app.payment.refunds).isZero();
        assertThat(app.ticketing.attempts).isZero();
        assertThat(app.purchaseRecords.findAll()).isEmpty();
        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.ACTIVE);
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(app.notifications.purchaseFailures).hasSize(1);
    }

    // REQ: ROB-01, NFR-08, INV-10
    // UC: UC 9 - Checkout and Payment Completion
    // UAT: UAT-29 - Payment Gateway Timeout
    @Test
    void checkout_whenPaymentGatewayThrowsTimeout_abortsWithoutTicketIssuanceAndOrderRemainsActive() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createStrictOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");
        app.payment.mode = ApplicationAcceptanceFixture.FakePaymentGatewayPort.Mode.THROW_TIMEOUT;

        assertThatThrownBy(() -> app.checkoutSaga.executeCheckout(orderId, "payment-token", null))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("timeout");

        assertThat(app.payment.charges).isEqualTo(1);
        assertThat(app.payment.refunds).isZero();
        assertThat(app.ticketing.attempts).isZero();
        assertThat(app.purchaseRecords.findAll()).isEmpty();
        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.ACTIVE);
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(app.notifications.purchaseFailures).hasSize(1);
    }

    // REQ: INV-10, ROB-01
    // UC: UC 9 - Checkout and Payment Completion
    // UAT: UAT-30 - Supply System Failure
    @Test
    void checkout_whenTicketIssuanceFailsAfterPayment_refundsReleasesInventoryCancelsOrderAndCreatesNoReceipt() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.createSeatedZone("event-1", "zone-vip", "A-1", "100.00");
        String orderId = app.createStrictOrder(buyer, "event-1").orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");
        app.ticketing.mode = ApplicationAcceptanceFixture.FakeTicketIssuancePort.Mode.FAILED;

        assertThatThrownBy(() -> app.checkoutSaga.executeCheckout(orderId, "payment-token", null))
                .isInstanceOf(IssuanceFailedException.class)
                .hasMessageContaining("ticket supply failed");

        assertThat(app.payment.charges).isEqualTo(1);
        assertThat(app.payment.refunds).isEqualTo(1);
        assertThat(app.payment.lastRefundTransactionId).isEqualTo("TXN-1");
        assertThat(app.ticketing.attempts).isEqualTo(1);
        assertThat(app.purchaseRecords.findAll()).isEmpty();
        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(app.zone("zone-vip").rows().get(0).seats().get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(app.notifications.purchaseFailures).hasSize(1);
        assertThat(app.notifications.purchaseSuccesses).isEmpty();
    }
}
