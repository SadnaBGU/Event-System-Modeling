package com.eventsystem.application.acceptance;

import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.order.CheckoutResult;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyOwnerCommand;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.zone.SeatStatus;
import com.eventsystem.domain.order.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UC16_PolicyAcceptanceTest {

    private static PolicyScopeCommand eventScope(String eventId) {
        return new PolicyScopeCommand(false, Set.of(eventId));
    }

    private static PolicyScopeCommand companyWideScope() {
        return new PolicyScopeCommand(true, Set.of());
    }

    private static PolicyRuleCommand maxTickets(int max) {
        return new PolicyRuleCommand("MAX_TICKETS", max, null, null, null, null);
    }

    private static PolicyRuleCommand minTickets(int min) {
        return new PolicyRuleCommand("MIN_TICKETS", min, null, null, null, null);
    }

    private static PolicyRuleCommand codeRule(String code) {
        return new PolicyRuleCommand("CODE", null, code, null, null, null);
    }

    private static PurchasePolicyCommand maxTicketsPolicyCommand(
            MemberId actor,
            CompanyId companyId,
            EventId eventId,
            int max) {
        return new PurchasePolicyCommand(
                actor.value(),
                companyId.value(),
                "Max " + max + " tickets",
                eventScope(eventId.value()),
                maxTickets(max),
                true,
        PolicyOwnerCommand.EVENT );
    }

    private static DiscountPolicyCommand couponPolicyCommand(
            MemberId actor,
            CompanyId companyId,
            EventId eventId,
            String code,
            BigDecimal percent) {
        return new DiscountPolicyCommand(
                actor.value(),
                companyId.value(),
                "Coupon " + code,
                eventScope(eventId.value()),
                java.util.List.of(new DiscountCommand(
                        "Coupon " + code,
                        percent,
                        codeRule(code),
                        "HIDDEN",
                        null)),
                false,
                true,
        PolicyOwnerCommand.COMPANY);
    }

    private static PolicyRuleCommand allowAllRule() {
        return new PolicyRuleCommand(
                "ALLOW_ALL",
                null,
                null,
                null,
                null,
                null);
    }

    private static PolicyRuleCommand zoneRestrictRule(String zoneId) {
        return new PolicyRuleCommand(
                "ZONE_RESTRICT",
                null,
                null,
                null,
                java.util.List.of(zoneId),
                java.util.List.of(allowAllRule()));
    }

    private static DiscountPolicyCommand stackedZoneDiscountPolicyCommand(
            MemberId actor,
            CompanyId companyId,
            EventId eventId) {
        return new DiscountPolicyCommand(
                actor.value(),
                companyId.value(),
                "Zone-specific stacked discounts",
                eventScope(eventId.value()),
                java.util.List.of(
                        new DiscountCommand(
                                "VIP 10%",
                                BigDecimal.TEN,
                                zoneRestrictRule("zone-vip"),
                                "VISIBLE",
                                null),
                        new DiscountCommand(
                                "Balcony 20%",
                                BigDecimal.valueOf(20),
                                zoneRestrictRule("zone-balcony"),
                                "VISIBLE",
                                null)),
                true,
                true,
                PolicyOwnerCommand.EVENT);
    }

    // REQ: PP-04, PP-07, PRD-03
    // UC: UC 16 - Define Purchase and Discount Policies + UC 9 checkout validation
    // UAT: UAT-44 - Set Purchase Policy, UAT-27 - Checkout Policy Violation
    @Test
    void ownerSetsMaxTicketsPurchasePolicy_checkoutValidationUsesItAndBlocksPayment() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        EventId eventId = app.eventId("event-policy-1");

        /*
         * Create two available seats for the same event.
         * We use two zones because the current fixture helper creates one seat per
         * zone.
         */
        app.createSeatedZoneForCompany(eventId.value(), "zone-vip-1", "A-1", "100.00", companyId);
        app.createSeatedZoneForCompany(eventId.value(), "zone-vip-2", "A-2", "100.00", companyId);

        /*
         * Valid policy: max allowed tickets is 1.
         * The violation should happen during checkout, not during policy creation.
         */
        PurchasePolicyId policyId = app.policyManagementService.createPurchasePolicy(
                maxTicketsPolicyCommand(founder, companyId, eventId, 1));

        assertThat(app.realPurchasePolicies.findById(policyId)).isPresent();

        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.saveMember("buyer-1");

        String orderId = app.createStrictOrder(buyer, eventId.value()).orderId();

        /*
         * Buyer reserves 2 tickets, while policy allows only 1.
         */
        app.reserveSeat(orderId, "zone-vip-1", "A-1");
        app.reserveSeat(orderId, "zone-vip-2", "A-2");

        assertThatThrownBy(() -> app.checkoutSagaWithRealPolicies.executeCheckout(orderId, "payment-token", null))
                .isInstanceOf(OrderViolatesPolicyException.class)
                .hasMessageContaining("Purchase policy");

        assertThat(app.payment.charges).isZero();
        assertThat(app.ticketing.attempts).isZero();
        assertThat(app.purchaseRecords.findAll()).isEmpty();

        /*
         * Policy failure should not cancel the order or release inventory.
         * The buyer should still be able to edit the active order.
         */
        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.ACTIVE);

        assertThat(app.zone("zone-vip-1").rows().get(0).seats().get(0).status())
                .isEqualTo(SeatStatus.RESERVED);

        assertThat(app.zone("zone-vip-2").rows().get(0).seats().get(0).status())
                .isEqualTo(SeatStatus.RESERVED);
    }

    // REQ: DP-06, DP-07
    // UC: UC 16 - Define Purchase and Discount Policies
    // UAT: UAT-46 reinterpret - coupon is percentage-based
    @Test
    void ownerCreatesCouponPercentageDiscount_checkoutWithValidCodeAppliesDiscount() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        EventId eventId = app.eventId("event-coupon-1");

        app.createSeatedZoneForCompany(eventId.value(), "zone-vip", "A-1", "100.00", companyId);

        DiscountPolicyId discountPolicyId = app.policyManagementService.createDiscountPolicy(
                couponPolicyCommand(founder, companyId, eventId, "SAVE10", BigDecimal.TEN));

        assertThat(app.realDiscountPolicies.findById(discountPolicyId)).isPresent();

        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.saveMember("buyer-1");

        String orderId = app.createStrictOrder(buyer, eventId.value()).orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");

        CheckoutResult result = app.checkoutSagaWithRealPolicies.executeCheckout(
                orderId,
                "payment-token",
                "SAVE10");

        assertThat(result.totalPaid().amount()).isEqualByComparingTo("90.00");
        assertThat(app.payment.lastChargedAmount.amount()).isEqualByComparingTo("90.00");
        assertThat(app.purchaseRecords.findAll()).hasSize(1);
        assertThat(app.purchaseRecords.findAll().get(0).getDiscountsApplied()).hasSize(1);
        assertThat(app.purchaseRecords.findAll().get(0).getDiscountsApplied().get(0).discountAmount().amount())
                .isEqualByComparingTo("10.00");
    }

    // REQ: DP-07
    // UC: UC 16 + UC 9
    // UAT: UAT-47 - Invalid Coupon at Checkout
    @Test
    void checkoutWithInvalidCoupon_doesNotApplyHiddenDiscount() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        EventId eventId = app.eventId("event-coupon-2");

        app.createSeatedZoneForCompany(eventId.value(), "zone-vip", "A-1", "100.00", companyId);

        app.policyManagementService.createDiscountPolicy(
                couponPolicyCommand(founder, companyId, eventId, "SAVE10", BigDecimal.TEN));

        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.saveMember("buyer-1");

        String orderId = app.createStrictOrder(buyer, eventId.value()).orderId();
        app.reserveSeat(orderId, "zone-vip", "A-1");

        CheckoutResult result = app.checkoutSagaWithRealPolicies.executeCheckout(
                orderId,
                "payment-token",
                "WRONG");

        assertThat(result.totalPaid().amount()).isEqualByComparingTo("100.00");
        assertThat(app.payment.lastChargedAmount.amount()).isEqualByComparingTo("100.00");
        assertThat(app.purchaseRecords.findAll()).hasSize(1);
        assertThat(app.purchaseRecords.findAll().get(0).getDiscountsApplied().get(0).discountAmount().amount())
                .isEqualByComparingTo("0.00");
    }

    // REQ: PP-07, DP-11
    // UC: UC 16 - Define Purchase and Discount Policies
    // UAT: UAT-48 - Policy Conflict
    @Test
    void conflictingPurchasePolicyAndDiscountPolicy_isRejectedByPolicyManagement() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        EventId eventId = app.eventId("event-conflict-1");

        app.createSeatedZoneForCompany(eventId.value(), "zone-vip", "A-1", "100.00", companyId);

        app.policyManagementService.createPurchasePolicy(
                maxTicketsPolicyCommand(founder, companyId, eventId, 4));

        DiscountPolicyCommand conflictingDiscount = new DiscountPolicyCommand(
                founder.value(),
                companyId.value(),
                "Buy 5 discount",
                eventScope(eventId.value()),
                java.util.List.of(new DiscountCommand(
                        "Buy 5 get discount",
                        BigDecimal.valueOf(20),
                        minTickets(5),
                        "VISIBLE",
                        null)),
                false,
                true, 
        PolicyOwnerCommand.EVENT);

        assertThatThrownBy(() -> app.policyManagementService.createDiscountPolicy(conflictingDiscount))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Policy conflict");

        assertThat(app.realDiscountPolicies.findActiveByCompanyId(companyId)).isEmpty();
    }

    // REQ: PRD-03, PRD-15
    // UC: UC 16 + UC 20
    // UAT: UAT-44 support, UAT-62 support
    @Test
    void managerWithoutPolicyPermission_cannotCreateCompanyWidePurchasePolicy() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        MemberId manager = app.memberId("manager-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        app.createMemberIfMissing(manager.value());

        app.companyService.appointManager(
                companyId,
                founder,
                manager,
                Set.of(com.eventsystem.domain.company.Permission.EVENT_INVENTORY_MANAGEMENT));
        app.companyService.acceptAppointment(companyId, manager);

        PurchasePolicyCommand companyWidePolicy = new PurchasePolicyCommand(
                manager.value(),
                companyId.value(),
                "Company max 4",
                companyWideScope(),
                maxTickets(4),
                true,
        PolicyOwnerCommand.COMPANY);

        assertThatThrownBy(() -> app.policyManagementService.createPurchasePolicy(companyWidePolicy))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed to manage purchase policies");

        assertThat(app.realPurchasePolicies.findByCompanyId(companyId)).isEmpty();
    }

    // REQ: DP-03, DP-06, DP-11, USR-10, INV-10
    // UC: UC 16 - Define Discount Policies + UC 9 - Checkout
    // UAT: UAT-45 - Set Visible Discount, UAT-26 - Successful Checkout
    // Regression: different zone discounts must be calculated from each zone
    // subtotal,
    // not from the full order total for each matching zone discount.
    @Test
    void checkoutWithDifferentDiscountsPerZone_appliesEachDiscountOnlyToThatZoneSubtotal() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId founder = app.memberId("founder-1");
        CompanyId companyId = app.createCompanyWithFounder(founder.value());
        EventId eventId = app.eventId("event-zone-specific-discounts");

        /*
         * VIP: 2 tickets × 100 = 200, VIP discount 10% = 20
         * Balcony: 3 tickets × 50 = 150, Balcony discount 20% = 30
         *
         * Base total: 350
         * Expected total discount: 50
         * Expected paid: 300
         */
        app.createStandingZoneForCompany(
                eventId.value(),
                "zone-vip",
                "VIP",
                "100.00",
                10,
                companyId);

        app.createStandingZoneForCompany(
                eventId.value(),
                "zone-balcony",
                "Balcony",
                "50.00",
                10,
                companyId);

        DiscountPolicyId discountPolicyId = app.policyManagementService.createDiscountPolicy(
                stackedZoneDiscountPolicyCommand(founder, companyId, eventId));

        assertThat(app.realDiscountPolicies.findById(discountPolicyId)).isPresent();

        BuyerReference buyer = app.memberBuyer("buyer-1");
        app.saveMember("buyer-1");

        String orderId = app.createStrictOrder(buyer, eventId.value()).orderId();

        app.reserveStanding(orderId, "zone-vip", 2);
        app.reserveStanding(orderId, "zone-balcony", 3);

        assertThat(app.order(orderId).getItems()).hasSize(2);
        assertThat(app.order(orderId).calculateBaseTotal().amount())
                .isEqualByComparingTo("350.00");

        CheckoutResult result = app.checkoutSagaWithRealPolicies.executeCheckout(
                orderId,
                "payment-token",
                null);

        assertThat(result).isNotNull();

        assertThat(app.payment.charges).isEqualTo(1);

        /*
         * Correct expected behavior:
         * VIP discount: 10% of 200 = 20
         * Balcony discount: 20% of 150 = 30
         * Total discount: 50
         * Charged amount: 350 - 50 = 300
         */
        assertThat(app.payment.lastChargedAmount.amount())
                .isEqualByComparingTo("300.00");

        assertThat(app.purchaseRecords.findAll()).hasSize(1);
        assertThat(app.purchaseRecords.findAll().get(0).getTotalPaid().amount())
                .isEqualByComparingTo("300.00");

        assertThat(app.purchaseRecords.findAll().get(0).getDiscountsApplied()).hasSize(1);
        assertThat(app.purchaseRecords.findAll().get(0).getDiscountsApplied().get(0).discountAmount().amount())
                .isEqualByComparingTo("50.00");

        assertThat(app.ticketing.attempts).isEqualTo(1);
        assertThat(app.ticketing.lastIssuedItems).hasSize(2);
        assertThat(app.ticketing.lastIssuedItems.stream().mapToInt(OrderItem::getQuantity).sum())
                .isEqualTo(5);

        assertThat(app.order(orderId).getStatus()).isEqualTo(OrderStatus.CHECKED_OUT);

        assertThat(app.zone("zone-vip").getAvailableCount()).isEqualTo(8);
        assertThat(app.zone("zone-balcony").getAvailableCount()).isEqualTo(7);

        assertThat(app.notifications.purchaseSuccesses).hasSize(1);
        assertThat(app.notifications.purchaseFailures).isEmpty();
    }
}