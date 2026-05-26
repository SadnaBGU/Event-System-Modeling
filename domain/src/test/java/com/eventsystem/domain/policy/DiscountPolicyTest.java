package com.eventsystem.domain.policy;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DiscountPolicy aggregate-level tests.
 *
 * UAT mapping:
 * - UAT-45 / UC16: visible company/event discount can be defined and applied.
 * - UAT-46 / UC16: hidden coupon-code discount can be defined and applied.
 * - UAT-47 / UC16: invalid coupon at checkout does not apply a discount.
 * - UAT-48 / UC16: contradictory discount/purchase constraints can be detected by evaluation.
 * - UAT-26 / UC9: successful checkout price calculation receives a correct discount snapshot.
 */
class DiscountPolicyTest {

    @Test
    void newDiscountPolicyDoesNotApplyUntilActivatedForCompanyOrEvent() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));

        assertThat(policy.appliesTo(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(policy.getDiscountPercent(contextWithTickets(REGULAR_ZONE))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.doesDiscountApplyForPurchase(contextWithTickets(REGULAR_ZONE))).isFalse();
    }

    @Test
    void companyWideVisibleDiscountAppliesToAnyEventOfSameCompany_UAT45() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));

        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isTrue();
        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE))).isTrue();
        assertThat(policy.getDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo("20");
    }

    @Test
    void companyWideDiscountDoesNotApplyToDifferentCompany() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));

        assertThat(policy.appliesTo(contextForCompanyAndEvent(OTHER_COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isFalse();
        assertThat(policy.getDiscountPercent(contextForCompanyAndEvent(OTHER_COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void eventSpecificDiscountAppliesOnlyToActivatedEvent_UAT45() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateForEvent(EVENT_ID.toString());
        policy.addDiscount(Discount.GeneralDiscount("Event only", BigDecimal.valueOf(10)));

        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isTrue();
        assertThat(policy.getDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo("10");

        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE))).isFalse();
        assertThat(policy.getDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deactivatingCompanyWideAndEventSpecificDiscountStopsApplication() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.activateForEvent(EVENT_ID.toString());
        policy.addDiscount(Discount.GeneralDiscount("Discount", BigDecimal.valueOf(10)));

        assertThat(policy.getDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo("10");

        policy.deactivateCompanyWide();
        policy.deactivateForEvent(EVENT_ID.toString());

        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isFalse();
        assertThat(policy.getDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void couponCodeDiscountAppliesOnlyForCorrectCode_UAT46_UAT47() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.addDiscount(new Discount("Coupon", BigDecimal.valueOf(15), new CodePolicy("SAVE15")));

        assertThat(policy.getDiscountPercent(contextWithCode("SAVE15", REGULAR_ZONE)))
                .isEqualByComparingTo("15");
        assertThat(policy.getDiscountPercent(contextWithCode("WRONG", REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.doesDiscountApplyForPurchase(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
    }

    @Test
    void nonStackablePolicyChoosesBestValidDiscount() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.disallowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Small", BigDecimal.valueOf(10)));
        policy.addDiscount(Discount.GeneralDiscount("Large", BigDecimal.valueOf(25)));

        assertThat(policy.getDiscountPercent(contextWithTickets(REGULAR_ZONE))).isEqualByComparingTo("25");
    }

    @Test
    void nonStackablePolicyIgnoresInvalidDiscountEvenWhenItsPercentIsHigher_UAT47() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.disallowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(10)));
        policy.addDiscount(new Discount("Wrong coupon", BigDecimal.valueOf(80), new CodePolicy("SECRET")));

        assertThat(policy.getDiscountPercent(contextWithCode("WRONG", REGULAR_ZONE))).isEqualByComparingTo("10");
    }

    @Test
    void stackablePolicySumsOnlyValidDiscountsAndCapsAt100_UAT45() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.allowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(40)));
        policy.addDiscount(new Discount("Valid coupon", BigDecimal.valueOf(50), new CodePolicy("STACK")));
        policy.addDiscount(new Discount("Invalid coupon", BigDecimal.valueOf(50), new CodePolicy("NOPE")));
        policy.addDiscount(Discount.GeneralDiscount("Cap", BigDecimal.valueOf(30)));

        assertThat(policy.getDiscountPercent(contextWithCode("STACK", REGULAR_ZONE))).isEqualByComparingTo("100");
    }

    @Test
    void discountSnapshotCalculatesMoneyAmountByPercent_UAT26() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        policy.activateCompanyWide();
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));
        Money baseCost = Money.of(BigDecimal.valueOf(250), "ILS");

        DiscountSnapshot snapshot = policy.getDiscountSnapshot(contextWithTickets(REGULAR_ZONE), baseCost);

        assertThat(snapshot.discountName()).isEqualTo("Early bird");
        assertThat(snapshot.discountAmount().currency()).isEqualTo("ILS");
        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo("50.0");
    }

    @Test
    void eventSpecificNullActivationArgumentsAreRejected() {
        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);

        assertThatThrownBy(() -> policy.activateForEvent((String) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.deactivateForEvent((String) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void discountPolicyRejectsNullCompanyContextAndDiscount() {
        assertThatThrownBy(() -> new DiscountPolicy(null))
                .isInstanceOf(NullPointerException.class);

        DiscountPolicy policy = new DiscountPolicy(COMPANY_ID);
        assertThatThrownBy(() -> policy.addDiscount(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.appliesTo(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void policyConflictCanBeDetectedByEvaluatingPurchaseAndDiscountRules_UAT48() {
        PurchasePolicy purchasePolicy = new PurchasePolicy(new com.eventsystem.domain.policy.basic.MaxTicketPolicy(4));
        Discount requiresFiveTickets = new Discount("Buy five", BigDecimal.valueOf(20), new MinTicketPolicy(5));

        assertThat(purchasePolicy.validatePurchasePolicy(contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE)))
                .isFalse();
        assertThat(requiresFiveTickets.validateDiscount(contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE)))
                .isTrue();
    }
}
