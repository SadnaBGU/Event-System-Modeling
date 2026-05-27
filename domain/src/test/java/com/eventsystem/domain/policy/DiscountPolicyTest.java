package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;


import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void newCompanyWideDiscountPolicyDoesNotApplyUntilActivatedForCompanyOrEvent() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));

        assertThat(policy.appliesTo(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.isPurchaseEligibleForDiscount(contextWithTickets(REGULAR_ZONE))).isFalse();
    }

    @Test
    void NoDiscountPolicyCannotBeActivated() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        assertThrows(DiscountPolicyException.class, () -> {policy.activate();});
    }

    @Test
    void newEventDiscountPolicyDoesNotApplyUntilActivatedForCompanyOrEvent() {
        DiscountPolicy policy = DiscountPolicy.inactiveForEvents(COMPANY_ID, Set.of(EVENT_ID));
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));

        assertThat(policy.appliesTo(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.isPurchaseEligibleForDiscount(contextWithTickets(REGULAR_ZONE))).isFalse();
    }

    @Test
    void companyWideVisibleDiscountAppliesToAnyEventOfSameCompany_UAT45() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));
        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isTrue();
        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE))).isTrue();
        assertThat(policy.getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo("20");
    }

    @Test
    void companyWideDiscountDoesNotApplyToDifferentCompany() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));
        
        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.appliesTo(contextForCompanyAndEvent(OTHER_COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isFalse();
        assertThat(policy.getFullDiscountPercent(contextForCompanyAndEvent(OTHER_COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void eventSpecificDiscountAppliesOnlyToActivatedEvent_UAT45() {
        DiscountPolicy policy = DiscountPolicy.inactiveForSingleEvent(COMPANY_ID, EVENT_ID);
        policy.addDiscount(Discount.GeneralDiscount("Event only", BigDecimal.valueOf(10)));

        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isTrue();
        assertThat(policy.getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo("10");

        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE))).isFalse();
        assertThat(policy.getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deactivatingCompanyWideAndEventSpecificDiscountStopsApplication() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.activateForEvent(EVENT_ID);
        policy.addDiscount(Discount.GeneralDiscount("Discount", BigDecimal.valueOf(10)));

        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo("10");

        policy.deactivateCompanyWide();
        policy.deactivateForEvent(EVENT_ID);

        assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE))).isFalse();
        assertThat(policy.getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void couponCodeDiscountAppliesOnlyForCorrectCode_UAT46_UAT47() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(new Discount("Coupon", BigDecimal.valueOf(15), new CodePolicy("SAVE15")));

        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.getFullDiscountPercent(contextWithCode("SAVE15", REGULAR_ZONE)))
                .isEqualByComparingTo("15");
        assertThat(policy.getFullDiscountPercent(contextWithCode("WRONG", REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(policy.isPurchaseEligibleForDiscount(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
    }

    @Test
    void nonStackablePolicyChoosesBestValidDiscount() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.disallowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Small", BigDecimal.valueOf(10)));
        policy.addDiscount(Discount.GeneralDiscount("Large", BigDecimal.valueOf(25)));

        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE))).isEqualByComparingTo("25");
    }

    @Test
    void nonStackablePolicyIgnoresInvalidDiscountEvenWhenItsPercentIsHigher_UAT47() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.setCompanyWide();
        policy.disallowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(10)));
        policy.addDiscount(new Discount("Wrong coupon", BigDecimal.valueOf(80), new CodePolicy("SECRET")));

        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.getFullDiscountPercent(contextWithCode("WRONG", REGULAR_ZONE))).isEqualByComparingTo("10");
    }

    @Test
    void stackablePolicySumsOnlyValidDiscountsAndCapsAt100_UAT45() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.setCompanyWide();
        policy.allowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(40)));
        policy.addDiscount(new Discount("Valid coupon", BigDecimal.valueOf(50), new CodePolicy("STACK")));
        policy.addDiscount(new Discount("Invalid coupon", BigDecimal.valueOf(50), new CodePolicy("NOPE")));
        policy.addDiscount(Discount.GeneralDiscount("Cap", BigDecimal.valueOf(30)));

        assertDoesNotThrow(() -> {policy.activate();});
        assertThat(policy.getFullDiscountPercent(contextWithCode("STACK", REGULAR_ZONE))).isEqualByComparingTo("100");
    }

    @Test
    void discountSnapshotCalculatesMoneyAmountByPercent_UAT26() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.setCompanyWide();
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));
        Money baseCost = Money.of(BigDecimal.valueOf(250), "ILS");

        assertDoesNotThrow(() -> {policy.activate();});

        DiscountSnapshot snapshot = policy.generateDiscountSnapshot(contextWithTickets(REGULAR_ZONE), baseCost);

        assertThat(snapshot.discountName()).isEqualTo("Early bird");
        assertThat(snapshot.discountAmount().currency()).isEqualTo("ILS");
        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo("50.0");
    }

    @Test
    void eventSpecificNullActivationArgumentsAreRejected() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

        assertThatThrownBy(() -> policy.activateForEvent((EventId) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.deactivateForEvent((EventId) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void discountPolicyRejectsNullCompanyContextAndDiscount() {
        assertThatThrownBy(() -> new DiscountPolicy(null))
                .isInstanceOf(NullPointerException.class);

        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        assertThatThrownBy(() -> policy.addDiscount(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.appliesTo(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void policyConflictCanBeDetectedByEvaluatingPurchaseAndDiscountRules_UAT48() {
        PurchasePolicy purchasePolicy = new PurchasePolicy(new com.eventsystem.domain.policy.basic.MaxTicketPolicy(4));
        Discount requiresFiveTickets = new Discount("Buy five", BigDecimal.valueOf(20), new MinTicketPolicy(5));

        assertThat(purchasePolicy.isPurchaseAllowedInContext(contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE)))
                .isFalse();
        assertThat(requiresFiveTickets.validateDiscount(contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE)))
                .isTrue();
    }

    @Test
    void fullDiscountSummaryReturnsNoDiscountWhenPolicyDoesNotApply() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20)));


        DiscountSummary summary = policy.getFullDiscountSummary(
                contextWithTickets(REGULAR_ZONE),
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        assertThat(summary.appliedDiscountsNames()).isEmpty();
        assertThat(summary.appliedDiscountPercents()).isEmpty();
        assertThat(summary.actualDiscountAmount()).isEmpty();
        assertThat(summary.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
    }


    @Test
    void nonStackableSummaryIncludesOnlyBestValidDiscount() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.disallowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Small", BigDecimal.valueOf(10)));
        policy.addDiscount(Discount.GeneralDiscount("Large", BigDecimal.valueOf(25)));
        policy.activate();
        DiscountSummary summary = policy.getFullDiscountSummary(
                contextWithTickets(REGULAR_ZONE),
                Money.of(BigDecimal.valueOf(200), "ILS")
        );

        assertThat(summary.appliedDiscountsNames()).containsExactly("Large");
        assertThat(summary.appliedDiscountPercents())
         .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .containsExactly(BigDecimal.valueOf(25));
        assertThat(summary.actualDiscountAmount().get(0)).isEqualByComparingTo("50");
        assertThat(summary.totalDiscount()).isEqualByComparingTo("50");
    }

    @Test
    void stackableSummaryIncludesOnlyValidAppliedDiscounts() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.allowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.valueOf(20)));
        policy.addDiscount(new Discount("Valid coupon", BigDecimal.valueOf(15), new CodePolicy("OK")));
        policy.addDiscount(new Discount("Invalid coupon", BigDecimal.valueOf(50), new CodePolicy("NOPE")));
        policy.activate();

        DiscountSummary summary = policy.getFullDiscountSummary(
                contextWithCode("OK", REGULAR_ZONE),
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        assertThat(summary.appliedDiscountsNames()).containsExactly("Visible", "Valid coupon");
        assertThat(summary.appliedDiscountPercents())
         .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .containsExactly(BigDecimal.valueOf(20), BigDecimal.valueOf(15));
        assertThat(summary.actualDiscountAmount())
         .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .containsExactly(BigDecimal.valueOf(20), BigDecimal.valueOf(15));
        assertThat(summary.totalDiscount()).isEqualByComparingTo("35");
    }

    @Test
    void stackableSummaryCapsFinalContributionAt100Percent() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.allowStacking();
        policy.addDiscount(Discount.GeneralDiscount("Seventy", BigDecimal.valueOf(70)));
        policy.addDiscount(Discount.GeneralDiscount("Fifty", BigDecimal.valueOf(50)));
        policy.activate();

        DiscountSummary summary = policy.getFullDiscountSummary(
                contextWithTickets(REGULAR_ZONE),
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        assertThat(summary.appliedDiscountsNames()).containsExactly("Seventy", "Fifty");
        assertThat(summary.appliedDiscountPercents())
         .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .containsExactly(BigDecimal.valueOf(70), BigDecimal.valueOf(50));
        assertThat(summary.actualDiscountAmount())
         .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
          .containsExactly(BigDecimal.valueOf(70), BigDecimal.valueOf(50));
        assertThat(summary.totalDiscount()).isEqualByComparingTo("100");
    }

    @Test
    void discountSnapshotForNoDiscountHasZeroAmount() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(new Discount("Coupon", BigDecimal.valueOf(20), new CodePolicy("SAVE")));
        policy.activate();

        DiscountSnapshot snapshot = policy.generateDiscountSnapshot(
                contextWithCode("WRONG", REGULAR_ZONE),
                Money.of(BigDecimal.valueOf(100), "ILS")
        );

        assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void discountsAndDiscountedEventIdsAccessorsReturnDefensiveCopies() {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
        policy.addDiscount(Discount.GeneralDiscount("Visible", BigDecimal.TEN));
        policy.activateForEvent(EVENT_ID);

        assertThatThrownBy(() -> policy.discounts().add(Discount.GeneralDiscount("Other", BigDecimal.ONE)))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> policy.discountedEventIds().add("other-event"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}
