package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.PolicyType;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for basic policy rules.
 *
 * UAT mapping:
 * - UAT-44 / UC16: max-ticket purchase policy can be defined and evaluated.
 * - UAT-45 / UC16: visible date-based discount condition can be evaluated.
 * - UAT-46 / UC16: coupon-code condition can be defined and evaluated.
 * - UAT-47 / UC16: invalid coupon code is rejected by policy validation.
 * - UAT-27 / UC9: checkout fails when a purchase violates the purchase policy.
 */
class BasicPolicyTest {

    @Test
    void maxTicketPolicy_acceptsQuantityAtOrBelowLimit_UAT44() {
        MaxTicketPolicy policy = new MaxTicketPolicy(2);

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
    }

    @Test
    void maxTicketPolicy_rejectsQuantityAboveLimit_UAT27() {
        MaxTicketPolicy policy = new MaxTicketPolicy(2);
        PurchaseContext context = contextWithTickets(REGULAR_ZONE, VIP_ZONE, BALCONY_ZONE);

        assertThat(policy.validate(context)).isFalse();
        assertThatThrownBy(() -> policy.require(context))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Cannot purchase more than 2 tickets")
                .hasMessageContaining(EVENT_ID.toString());
    }

    @Test
    void maxTicketPolicy_rejectsInvalidLimit() {
        assertThatThrownBy(() -> new MaxTicketPolicy(0))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void minTicketPolicy_acceptsQuantityAtOrAboveMinimum() {
        MinTicketPolicy policy = new MinTicketPolicy(2);

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE, BALCONY_ZONE))).isTrue();
    }

    @Test
    void minTicketPolicy_rejectsQuantityBelowMinimum() {
        MinTicketPolicy policy = new MinTicketPolicy(2);
        PurchaseContext context = contextWithTickets(REGULAR_ZONE);

        assertThat(policy.validate(context)).isFalse();
        assertThatThrownBy(() -> policy.require(context))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Cannot purchase less than 2 tickets")
                .hasMessageContaining(EVENT_ID.toString());
    }

    @Test
    void minTicketPolicy_rejectsInvalidMinimum() {
        assertThatThrownBy(() -> new MinTicketPolicy(0))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("at least 1");
    }

    @Test
    void codePolicy_acceptsMatchingCoupon_UAT46() {
        CodePolicy policy = new CodePolicy("SAVE20");

        assertThat(policy.validate(contextWithCode("SAVE20", REGULAR_ZONE))).isTrue();
        assertThatCode(() -> policy.require(contextWithCode("SAVE20", REGULAR_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void codePolicy_rejectsWrongOrMissingCoupon_UAT47() {
        CodePolicy policy = new CodePolicy("SAVE20");

        assertThat(policy.validate(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
        assertThat(policy.validate(contextWithCode(null, REGULAR_ZONE))).isFalse();
        assertThatThrownBy(() -> policy.require(contextWithCode("WRONG", REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Invalid coupon code");
    }

    @Test
    void codePolicy_rejectsInvalidConfiguredCode() {
        assertThatThrownBy(() -> new CodePolicy(null))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new CodePolicy(""))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new CodePolicy("   "))
                .isInstanceOf(PolicyException.class);
    }

    @Test
    void minAgePolicy_acceptsBuyerAtOrAboveMinimumAge() {
        MinAgePolicy policy = new MinAgePolicy(18);

        assertThat(policy.validate(contextWithBirthDate(LocalDate.now().minusYears(18), REGULAR_ZONE))).isTrue();
        assertThat(policy.validate(contextWithBirthDate(LocalDate.now().minusYears(25), REGULAR_ZONE))).isTrue();
    }

    @Test
    void minAgePolicy_rejectsBuyerBelowMinimumAge() {
        MinAgePolicy policy = new MinAgePolicy(18);
        PurchaseContext context = contextWithBirthDate(LocalDate.now().minusYears(17), REGULAR_ZONE);

        assertThat(policy.validate(context)).isFalse();
        assertThatThrownBy(() -> policy.require(context))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Buyer must be over age 18")
                .hasMessageContaining(EVENT_ID.toString());
    }

    @Test
    void minAgePolicy_rejectsNegativeMinimumAge() {
        assertThatThrownBy(() -> new MinAgePolicy(-1))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("not be negative");
    }

    @Test
    void untilDatePolicy_acceptsUntilAndIncludingDeadline_UAT45() {
        assertThat(new UntilDatePolicy(LocalDate.now()).validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThat(new UntilDatePolicy(LocalDate.now().plusDays(1)).validate(contextWithTickets(REGULAR_ZONE)))
                .isTrue();
    }

    @Test
    void untilDatePolicy_rejectsAfterDeadline_UAT45() {
        UntilDatePolicy policy = new UntilDatePolicy(LocalDate.now().minusDays(1));
        PurchaseContext context = contextWithTickets(REGULAR_ZONE);

        assertThat(policy.validate(context)).isFalse();
        assertThatThrownBy(() -> policy.require(context))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("after date");
    }

    @Test
    void afterDatePolicy_acceptsOnlyAfterConfiguredDate() {
        assertThat(new AfterDatePolicy(LocalDate.now().minusDays(1)).validate(contextWithTickets(REGULAR_ZONE)))
                .isTrue();
        assertThat(new AfterDatePolicy(LocalDate.now()).validate(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(new AfterDatePolicy(LocalDate.now().plusDays(1)).validate(contextWithTickets(REGULAR_ZONE)))
                .isFalse();
    }

    @Test
    void datePoliciesRejectNullDate() {
        assertThatThrownBy(() -> new UntilDatePolicy(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AfterDatePolicy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void alwaysTruePolicy_alwaysValidatesAndNeverThrows() {
        assertThat(AlwaysTruePolicy.INSTANCE.validate(contextWithTickets())).isTrue();
        assertThatCode(() -> AlwaysTruePolicy.INSTANCE.require(contextWithTickets()))
                .doesNotThrowAnyException();
    }

    @Test
    void minAgePolicy_rejectsBuyerWhoseBirthdayIsTomorrow() {
        MinAgePolicy policy = new MinAgePolicy(18);

        PurchaseContext context = contextWithBirthDate(
                LocalDate.now().minusYears(18).plusDays(1),
                REGULAR_ZONE);

        assertThat(policy.validate(context)).isFalse();
    }

    @Test
    void minAgePolicy_acceptsBuyerAfterBirthday() {
        MinAgePolicy policy = new MinAgePolicy(18);

        PurchaseContext context = contextWithBirthDate(
                LocalDate.now().minusYears(18).minusDays(1),
                REGULAR_ZONE);

        assertThat(policy.validate(context)).isTrue();
    }

    @Test
    void datePolicyRequireDoesNotThrowWhenValid() {
        assertThatCode(() -> new UntilDatePolicy(LocalDate.now().plusDays(1))
                .require(contextWithTickets(REGULAR_ZONE)))
                .doesNotThrowAnyException();

        assertThatCode(() -> new AfterDatePolicy(LocalDate.now().minusDays(1))
                .require(contextWithTickets(REGULAR_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void afterDatePolicy_requireThrowsWhenBeforeStartDate() {
        AfterDatePolicy policy = new AfterDatePolicy(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class);
    }

    @Test
    void maxTicketPolicyEvaluateReturnsFailureReason_whenTooManyTickets() {
        MaxTicketPolicy policy = new MaxTicketPolicy(2);

        PolicyValidationResult result = policy.evaluate(contextWithTickets(REGULAR_ZONE, REGULAR_ZONE, REGULAR_ZONE));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("Cannot purchase more than 2 tickets");
    }

    @Test
    void minAgePolicyUsesPurchaseDateFromContext() {
        LocalDate purchaseDate = LocalDate.of(2026, 6, 1);
        LocalDate exactly18 = LocalDate.of(2008, 6, 1);

        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                exactly18,
                purchaseDate,
                null);

        MinAgePolicy policy = new MinAgePolicy(18);

        assertThat(policy.evaluate(context).isSuccess()).isTrue();
    }

    @Test
    void minAgePolicyRejectsBuyerBelowMinimumUsingPurchaseDate() {
        LocalDate purchaseDate = LocalDate.of(2026, 6, 1);
        LocalDate notYet18 = LocalDate.of(2008, 6, 2);

        PurchaseContext context = new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                List.of(REGULAR_ZONE),
                notYet18,
                purchaseDate,
                null);

        MinAgePolicy policy = new MinAgePolicy(18);

        assertThat(policy.evaluate(context).isSuccess()).isFalse();
    }

    @Test
    void untilDatePolicyAllowsPurchaseOnDeadlineDate() {
        LocalDate deadline = LocalDate.of(2026, 6, 1);

        PurchaseContext context = contextWithTickets(REGULAR_ZONE).withPurchaseDate(deadline);

        UntilDatePolicy policy = new UntilDatePolicy(deadline);

        assertThat(policy.evaluate(context).isSuccess()).isTrue();
    }

    @Test
    void untilDatePolicyRejectsPurchaseAfterDeadlineDate() {
        LocalDate deadline = LocalDate.of(2026, 6, 1);

        PurchaseContext context = contextWithTickets(REGULAR_ZONE).withPurchaseDate(deadline.plusDays(1));

        UntilDatePolicy policy = new UntilDatePolicy(deadline);

        assertThat(policy.evaluate(context).isSuccess()).isFalse();
    }

    @Test
    void afterDatePolicyRejectsPurchaseOnDeadlineDate_whenStrictAfter() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);

        PurchaseContext context = contextWithTickets(REGULAR_ZONE).withPurchaseDate(startDate);

        AfterDatePolicy policy = new AfterDatePolicy(startDate);

        assertThat(policy.evaluate(context).isSuccess()).isFalse();
    }

    @Test
    void afterDatePolicyAllowsPurchaseAfterDeadlineDate() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);

        PurchaseContext context = contextWithTickets(REGULAR_ZONE).withPurchaseDate(startDate.plusDays(1));

        AfterDatePolicy policy = new AfterDatePolicy(startDate);

        assertThat(policy.evaluate(context).isSuccess()).isTrue();
    }

    @Test
    void neverAllowPolicy_evaluateValidateAndRequireAlwaysReject() {
        PolicyValidationResult result = NeverAllowPolicy.INSTANCE.evaluate(contextWithTickets(REGULAR_ZONE));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("Purchase policy restricts current purchase");
        assertThat(NeverAllowPolicy.INSTANCE.validate(contextWithTickets(REGULAR_ZONE))).isFalse();

        assertThatThrownBy(() -> NeverAllowPolicy.INSTANCE.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Purchase policy restricts current purchase");
    }

    // PP-05 / PP-06 / DP-06 / DP-07:
    // Basic policies expose the correct PolicyType used by conflict detection and
    // policy building.
    @Test
    void basicPoliciesExposeCorrectPolicyTypes() {
        assertThat(AlwaysTruePolicy.INSTANCE.type()).isEqualTo(PolicyType.ALWAYS_TRUE);
        assertThat(NeverAllowPolicy.INSTANCE.type()).isEqualTo(PolicyType.NEVER_ALLOW);

        assertThat(new MinTicketPolicy(2).type()).isEqualTo(PolicyType.MIN_TICKETS);
        assertThat(new MaxTicketPolicy(4).type()).isEqualTo(PolicyType.MAX_TICKETS);
        assertThat(new MinAgePolicy(18).type()).isEqualTo(PolicyType.MIN_AGE);

        assertThat(new AfterDatePolicy(LocalDate.now().minusDays(1)).type()).isEqualTo(PolicyType.AFTER_DATE);
        assertThat(new UntilDatePolicy(LocalDate.now().plusDays(1)).type()).isEqualTo(PolicyType.UNTIL_DATE);

        assertThat(new CodePolicy("SAVE20").type()).isEqualTo(PolicyType.CODE);
    }

    // PP-06:
    // Ticket quantity policy types are categorized correctly.
    @Test
    void policyTypeIdentifiesTicketQuantityRules() {
        assertThat(PolicyType.MIN_TICKETS.isTicketQuantityRule()).isTrue();
        assertThat(PolicyType.MAX_TICKETS.isTicketQuantityRule()).isTrue();

        assertThat(PolicyType.MIN_AGE.isTicketQuantityRule()).isFalse();
        assertThat(PolicyType.CODE.isTicketQuantityRule()).isFalse();
        assertThat(PolicyType.AFTER_DATE.isTicketQuantityRule()).isFalse();
        assertThat(PolicyType.UNTIL_DATE.isTicketQuantityRule()).isFalse();
        assertThat(PolicyType.ALWAYS_TRUE.isTicketQuantityRule()).isFalse();
        assertThat(PolicyType.NEVER_ALLOW.isTicketQuantityRule()).isFalse();
    }

    // DP-07 / DP-08:
    // Coupon policy type is categorized correctly.
    @Test
    void policyTypeIdentifiesCouponRule() {
        assertThat(PolicyType.CODE.isCouponRule()).isTrue();

        assertThat(PolicyType.MIN_TICKETS.isCouponRule()).isFalse();
        assertThat(PolicyType.MAX_TICKETS.isCouponRule()).isFalse();
        assertThat(PolicyType.MIN_AGE.isCouponRule()).isFalse();
        assertThat(PolicyType.AFTER_DATE.isCouponRule()).isFalse();
        assertThat(PolicyType.UNTIL_DATE.isCouponRule()).isFalse();
        assertThat(PolicyType.ALWAYS_TRUE.isCouponRule()).isFalse();
        assertThat(PolicyType.NEVER_ALLOW.isCouponRule()).isFalse();
    }

    // DP-06 / TST-09:
    // Date policy types are categorized correctly.
    @Test
    void policyTypeIdentifiesDateRules() {
        assertThat(PolicyType.AFTER_DATE.isDateRule()).isTrue();
        assertThat(PolicyType.UNTIL_DATE.isDateRule()).isTrue();

        assertThat(PolicyType.MIN_TICKETS.isDateRule()).isFalse();
        assertThat(PolicyType.MAX_TICKETS.isDateRule()).isFalse();
        assertThat(PolicyType.MIN_AGE.isDateRule()).isFalse();
        assertThat(PolicyType.CODE.isDateRule()).isFalse();
        assertThat(PolicyType.ALWAYS_TRUE.isDateRule()).isFalse();
        assertThat(PolicyType.NEVER_ALLOW.isDateRule()).isFalse();
    }

    // DDD/extensibility:
    // Basic policy types are not composite rules.
    @Test
    void basicPolicyTypesAreNotCompositeRules() {
        assertThat(PolicyType.ALWAYS_TRUE.isCompositeRule()).isFalse();
        assertThat(PolicyType.NEVER_ALLOW.isCompositeRule()).isFalse();
        assertThat(PolicyType.MIN_TICKETS.isCompositeRule()).isFalse();
        assertThat(PolicyType.MAX_TICKETS.isCompositeRule()).isFalse();
        assertThat(PolicyType.MIN_AGE.isCompositeRule()).isFalse();
        assertThat(PolicyType.AFTER_DATE.isCompositeRule()).isFalse();
        assertThat(PolicyType.UNTIL_DATE.isCompositeRule()).isFalse();
        assertThat(PolicyType.CODE.isCompositeRule()).isFalse();
    }

}
