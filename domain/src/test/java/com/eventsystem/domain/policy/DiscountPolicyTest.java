package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountInfo;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.DiscountSummary;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.rule.basic.CodePolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * - UAT-48 / UC16: contradictory discount/purchase constraints can be detected
 * by evaluation.
 * - UAT-26 / UC9: successful checkout price calculation receives a correct
 * discount snapshot.
 */
class DiscountPolicyTest {

        private static PurchaseContext context(EventId eventId, CompanyId companyId, String code) {
                return new PurchaseContext(
                                eventId,
                                companyId,
                                List.of(REGULAR_ZONE),
                                LocalDate.now().minusYears(25),
                                code);
        }

        @Test
        void newCompanyWideDiscountPolicyDoesNotApplyUntilActivatedForCompanyOrEvent() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.valueOf(20)));

                assertThat(policy.appliesTo(contextWithTickets(REGULAR_ZONE))).isFalse();
                assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(policy.isPurchaseEligibleForDiscount(contextWithTickets(REGULAR_ZONE))).isFalse();
        }

        @Test
        void NoDiscountPolicyCannotBeActivated() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                assertThrows(DiscountPolicyException.class, () -> {
                        policy.activate();
                });
        }

        @Test
        void newEventDiscountPolicyDoesNotApplyUntilActivatedForCompanyOrEvent() {
                DiscountPolicy policy = DiscountPolicy.inactiveForEvents(COMPANY_ID, Set.of(EVENT_ID));
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.valueOf(20)));

                assertThat(policy.appliesTo(contextWithTickets(REGULAR_ZONE))).isFalse();
                assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(policy.isPurchaseEligibleForDiscount(contextWithTickets(REGULAR_ZONE))).isFalse();
        }

        @Test
        void companyWideVisibleDiscountAppliesToAnyEventOfSameCompany_UAT45() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Early bird", BigDecimal.valueOf(20)));
                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isTrue();
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                                .isTrue();
                assertThat(policy
                                .getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null,
                                                REGULAR_ZONE)))
                                .isEqualByComparingTo("20");
        }

        @Test
        void companyWideDiscountDoesNotApplyToDifferentCompany() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Early bird", BigDecimal.valueOf(20)));

                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.appliesTo(contextForCompanyAndEvent(OTHER_COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isFalse();
                assertThat(policy
                                .getFullDiscountPercent(contextForCompanyAndEvent(OTHER_COMPANY_ID, EVENT_ID, null,
                                                REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void eventSpecificDiscountAppliesOnlyToActivatedEvent_UAT45() {
                DiscountPolicy policy = DiscountPolicy.inactiveEventPolicy(COMPANY_ID, EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Event only", BigDecimal.valueOf(10)));

                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isTrue();
                assertThat(policy.getFullDiscountPercent(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isEqualByComparingTo("10");

                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                                .isFalse();
                assertThat(policy
                                .getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null,
                                                REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void deactivatingCompanyWideAndEventSpecificDiscountStopsApplication() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.activateForEvent(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Discount", BigDecimal.valueOf(10)));

                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.getFullDiscountPercent(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isEqualByComparingTo("10");

                policy.deactivateCompanyWide();
                policy.deactivateForEvent(EVENT_ID);

                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isFalse();
                assertThat(policy.getFullDiscountPercent(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void couponCodeDiscountAppliesOnlyForCorrectCode_UAT46_UAT47() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(new Discount("Coupon", BigDecimal.valueOf(15), new CodePolicy("SAVE15")));

                assertDoesNotThrow(() -> {
                        policy.activate();
                });
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
                policy.addDiscount(GeneralNoExpiryDiscount("Small", BigDecimal.valueOf(10)));
                policy.addDiscount(GeneralNoExpiryDiscount("Large", BigDecimal.valueOf(25)));

                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.isStackable()).isFalse();
                assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE))).isEqualByComparingTo("25");
        }

        @Test
        void nonStackablePolicyIgnoresInvalidDiscountEvenWhenItsPercentIsHigher_UAT47() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.setCompanyWide();
                policy.disallowStacking();
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.valueOf(10)));
                policy.addDiscount(new Discount("Wrong coupon", BigDecimal.valueOf(80), new CodePolicy("SECRET")));

                assertThat(policy.isStackable()).isFalse();
                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.getFullDiscountPercent(contextWithCode("WRONG", REGULAR_ZONE)))
                                .isEqualByComparingTo("10");
        }

        @Test
        void stackablePolicySumsOnlyValidDiscountsAndCapsAt100_UAT45() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.setCompanyWide();
                policy.allowStacking();
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.valueOf(40)));
                policy.addDiscount(new Discount("Valid coupon", BigDecimal.valueOf(50), new CodePolicy("STACK")));
                policy.addDiscount(new Discount("Invalid coupon", BigDecimal.valueOf(50), new CodePolicy("NOPE")));
                policy.addDiscount(GeneralNoExpiryDiscount("Cap", BigDecimal.valueOf(30)));

                assertThat(policy.isStackable()).isTrue();
                assertDoesNotThrow(() -> {
                        policy.activate();
                });
                assertThat(policy.getFullDiscountPercent(contextWithCode("STACK", REGULAR_ZONE)))
                                .isEqualByComparingTo("100");
        }

        @Test
        void discountSnapshotCalculatesMoneyAmountByPercent_UAT26() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.setCompanyWide();
                policy.addDiscount(GeneralNoExpiryDiscount("Early bird", BigDecimal.valueOf(20)));
                Money baseCost = Money.of(BigDecimal.valueOf(250), "ILS");

                assertDoesNotThrow(() -> {
                        policy.activate();
                });

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
                PurchasePolicy purchasePolicy = PurchasePolicy.newMaxTicketPolicy(COMPANY_ID, "MaxTicket4", 4);
                Discount requiresFiveTickets = new Discount("Buy five", BigDecimal.valueOf(20), new MinTicketPolicy(5));

                assertThat(purchasePolicy
                                .isPurchaseAllowedInContext(
                                                contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE)))
                                .isFalse();
                assertThat(requiresFiveTickets
                                .validateDiscount(contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE)))
                                .isTrue();
        }

        @Test
        void fullDiscountSummaryReturnsNoDiscountWhenPolicyDoesNotApply() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Early bird", BigDecimal.valueOf(20)));

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextWithTickets(REGULAR_ZONE),
                                Money.of(BigDecimal.valueOf(100), "ILS"));

                assertThat(summary.appliedDiscountsNames()).isEmpty();
                assertThat(summary.appliedDiscountPercents()).isEmpty();
                assertThat(summary.actualDiscountAmount()).isEmpty();
                assertThat(summary.totalDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void nonStackableSummaryIncludesOnlyBestValidDiscount() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.disallowStacking();
                policy.addDiscount(GeneralNoExpiryDiscount("Small", BigDecimal.valueOf(10)));
                policy.addDiscount(GeneralNoExpiryDiscount("Large", BigDecimal.valueOf(25)));
                policy.activate();
                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextWithTickets(REGULAR_ZONE),
                                Money.of(BigDecimal.valueOf(200), "ILS"));

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
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.valueOf(20)));
                policy.addDiscount(new Discount("Valid coupon", BigDecimal.valueOf(15), new CodePolicy("OK")));
                policy.addDiscount(new Discount("Invalid coupon", BigDecimal.valueOf(50), new CodePolicy("NOPE")));
                policy.activate();

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextWithCode("OK", REGULAR_ZONE),
                                Money.of(BigDecimal.valueOf(100), "ILS"));

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
                policy.addDiscount(GeneralNoExpiryDiscount("Seventy", BigDecimal.valueOf(70)));
                policy.addDiscount(GeneralNoExpiryDiscount("Fifty", BigDecimal.valueOf(50)));
                policy.activate();

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextWithTickets(REGULAR_ZONE),
                                Money.of(BigDecimal.valueOf(100), "ILS"));

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
                                Money.of(BigDecimal.valueOf(100), "ILS"));

                assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void discountsAndDiscountedEventIdsAccessorsReturnDefensiveCopies() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));
                policy.activateForEvent(EVENT_ID);

                assertThatThrownBy(() -> policy.discounts().add(GeneralNoExpiryDiscount("Other", BigDecimal.ONE)))
                                .isInstanceOf(UnsupportedOperationException.class);

                assertThatThrownBy(() -> policy.discountedEventIds().add(new EventId("other-event")))
                                .isInstanceOf(UnsupportedOperationException.class);
        }

        // UAT-45 / DP-11: non-stackable discount policy uses the best matching
        // discount.
        @Test
        void getFullDiscountSummary_whenNotStackable_usesBestApplicableDiscountOnly_UAT45() {
                DiscountPolicy policy = activeEventPolicy(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Small", BigDecimal.TEN));
                policy.addDiscount(GeneralNoExpiryDiscount("Large", BigDecimal.valueOf(30)));

                DiscountSummary summary = policy.getFullDiscountSummary(context(EVENT_ID, COMPANY_ID, null),
                                baseCost100());

                assertThat(summary.appliedDiscountsNames()).containsExactly("Large");
                assertThat(summary.appliedDiscountPercents()).hasSize(1);
                assertThat(summary.appliedDiscountPercents().get(0)).isEqualByComparingTo("30");
                assertThat(summary.actualDiscountAmount()).hasSize(1);
                assertThat(summary.actualDiscountAmount().get(0)).isEqualByComparingTo("30");
                assertThat(summary.totalDiscount()).isEqualByComparingTo("30");
        }

        // UAT-48 / DP-12 / DP-13: stackable discounts accumulate but total percent is
        // capped at 100%.
        @Test
        void getFullDiscountSummary_whenStackable_capsTotalAt100_UAT48() {
                DiscountPolicy policy = eventPolicy(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Sixty", BigDecimal.valueOf(60)));
                policy.addDiscount(GeneralNoExpiryDiscount("Fifty", BigDecimal.valueOf(50)));
                policy.activate();
                policy.allowStacking();

                DiscountSummary summary = policy.getFullDiscountSummary(context(EVENT_ID, COMPANY_ID, null),
                                baseCost100());

                assertThat(summary.appliedDiscountsNames()).containsExactly("Sixty", "Fifty");
                assertThat(summary.isDiscountCappedAt100()).isTrue();
                assertThat(summary.totalDiscount()).isEqualByComparingTo("100");
        }

        // UAT-46 / UAT-47: coupon discount is hidden and applies only with the right
        // code.
        @Test
        void isPurchaseEligibleForSpecificDiscount_whenCouponCodeMatches_returnsTrue_UAT46() {
                DiscountPolicy policy = activeEventPolicy(EVENT_ID);
                policy.addDiscount(new Discount("SAVE20", BigDecimal.valueOf(20), new CodePolicy("SAVE20")));

                assertThat(policy.isPurchaseEligibleForSpecificDiscount(context(EVENT_ID, COMPANY_ID, "SAVE20"),
                                "SAVE20"))
                                .isTrue();
        }

        @Test
        void isPurchaseEligibleForSpecificDiscount_whenCouponNameMissing_returnsFalse_UAT47() {
                DiscountPolicy policy = activeEventPolicy(EVENT_ID);
                policy.addDiscount(new Discount("SAVE20", BigDecimal.valueOf(20), new CodePolicy("SAVE20")));

                assertThat(policy.isPurchaseEligibleForSpecificDiscount(context(EVENT_ID, COMPANY_ID, "SAVE20"),
                                "OTHER"))
                                .isFalse();
        }

        @Test
        void appliesTo_whenInactiveOrWrongCompanyOrWrongEvent_returnsFalse() {
                DiscountPolicy inactive = eventPolicy(EVENT_ID);
                inactive.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));

                DiscountPolicy active = activeEventPolicy(EVENT_ID);
                active.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));

                assertThat(inactive.appliesTo(context(EVENT_ID, COMPANY_ID, null))).isFalse();
                assertThat(active.appliesTo(context(EVENT_ID, OTHER_COMPANY_ID, null))).isFalse();
                assertThat(active.appliesTo(context(OTHER_EVENT_ID, COMPANY_ID, null))).isFalse();
        }

        @Test
        void deactivateCompanyWide_whenNoEventScope_deactivatesPolicy() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));
                policy.activate();

                policy.deactivateCompanyWide();

                assertThat(policy.scope().isCompanyWide()).isFalse();
                assertThat(policy.scope().eventIds()).isEmpty();
                assertThat(policy.isActive()).isFalse();
        }

        @Test
        void clearAllEventsFromScope_whenCompanyWide_preservesActiveCompanyWidePolicy() {
                DiscountPolicy policy = activeEventPolicy(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));
                policy.setCompanyWide();

                policy.clearAllEventsFromScope();

                assertThat(policy.scope().isCompanyWide()).isTrue();
                assertThat(policy.scope().eventIds()).isEmpty();
                assertThat(policy.isActive()).isTrue();
        }

        @Test
        void activate_whenNoDiscounts_throwsDiscountPolicyException() {
                DiscountPolicy policy = DiscountPolicy.inactiveEventPolicy(COMPANY_ID, EVENT_ID);

                assertThatThrownBy(policy::activate)
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("No Discounts");
        }

        @Test
        void activate_whenClearScope_throwsDiscountPolicyException() {
                DiscountPolicy policy = DiscountPolicy.clearScope(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));

                assertThatThrownBy(policy::activate)
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("Event or Company wide");
        }

        @Test
        void generateDiscountSnapshot_joinsAppliedDiscountNames() {
                DiscountPolicy policy = eventPolicy(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Early", BigDecimal.TEN));
                policy.addDiscount(GeneralNoExpiryDiscount("Student", BigDecimal.valueOf(15)));
                policy.activate();
                policy.allowStacking();

                DiscountSummary summary = policy.getFullDiscountSummary(context(EVENT_ID, COMPANY_ID, null),
                                baseCost100());

                assertThat(policy.generateDiscountSnapshot(summary, baseCost100()).discountName())
                                .isEqualTo("Early ; Student");
        }

        @Test
        void constructorRejectsNullIdOrScope() {
                assertThatThrownBy(() -> new DiscountPolicy(null, COMPANY_ID, PolicyScope.companyWideScope()))
                                .isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> new DiscountPolicy(DiscountPolicyId.random(), COMPANY_ID, null))
                                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void clearScopePolicyIsInvalidAndCannotBeActivatedEvenWithDiscount() {
                DiscountPolicy policy = DiscountPolicy.clearScope(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));

                assertThat(policy.isValidDiscountPolicy()).isFalse();
                assertThatThrownBy(policy::activate)
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("Event or Company wide");
        }

        @Test
        void changeScopeToClearScopeDeactivatesPreviouslyActivePolicy() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));
                policy.activate();

                policy.changeScope(PolicyScope.clearScope());

                assertThat(policy.isActive()).isFalse();
                assertThat(policy.appliesTo(contextWithTickets(REGULAR_ZONE))).isFalse();
                assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void changeScopeRejectsNullScope() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                assertThatThrownBy(() -> policy.changeScope(null))
                                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void deactivatingCompanyWideKeepsActiveEventSpecificScope_UAT45() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.activateForEvent(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Event backup", BigDecimal.valueOf(15)));
                policy.activate();

                policy.deactivateCompanyWide();

                assertThat(policy.isActive()).isTrue();
                assertThat(policy.scope().isCompanyWide()).isFalse();
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isTrue();
                assertThat(policy.getFullDiscountPercent(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isEqualByComparingTo("15");
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                                .isFalse();
        }

        @Test
        void clearAllEventsFromScopeKeepsCompanyWidePolicyActive_UAT45() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.activateForEvent(EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Company wide", BigDecimal.valueOf(12)));
                policy.activate();

                policy.clearAllEventsFromScope();

                assertThat(policy.isActive()).isTrue();
                assertThat(policy.scope().isCompanyWide()).isTrue();
                assertThat(policy.scope().eventIds()).isEmpty();
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null, REGULAR_ZONE)))
                                .isTrue();
                assertThat(policy
                                .getFullDiscountPercent(contextForCompanyAndEvent(COMPANY_ID, OTHER_EVENT_ID, null,
                                                REGULAR_ZONE)))
                                .isEqualByComparingTo("12");
        }

        @Test
        void clearAllEventsFromScopeDeactivatesEventOnlyPolicy() {
                DiscountPolicy policy = DiscountPolicy.inactiveEventPolicy(COMPANY_ID, EVENT_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Event only", BigDecimal.valueOf(12)));
                policy.activate();

                policy.clearAllEventsFromScope();

                assertThat(policy.isActive()).isFalse();
                assertThat(policy.scope().isScopedToEventsOrCompany()).isFalse();
                assertThat(policy.appliesTo(contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE)))
                                .isFalse();
        }

        @Test
        void specificDiscountEligibilityCoversValidMissingAndInvalidCoupon_UAT46_UAT47() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));
                policy.addDiscount(new Discount("Secret coupon", BigDecimal.valueOf(25), new CodePolicy("SECRET")));
                policy.activate();

                assertThat(policy.isPurchaseEligibleForSpecificDiscount(contextWithTickets(REGULAR_ZONE), "Visible"))
                                .isTrue();
                assertThat(policy.isPurchaseEligibleForSpecificDiscount(contextWithTickets(REGULAR_ZONE), "Missing"))
                                .isFalse();
                assertThat(
                                policy.isPurchaseEligibleForSpecificDiscount(contextWithCode("WRONG", REGULAR_ZONE),
                                                "Secret coupon"))
                                .isFalse();
                assertThat(
                                policy.isPurchaseEligibleForSpecificDiscount(contextWithCode("SECRET", REGULAR_ZONE),
                                                "Secret coupon"))
                                .isTrue();
        }

        @Test
        void eligibleForDiscountReturnsFalseWhenEveryDiscountPredicateFails_UAT47() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(new Discount("Secret coupon", BigDecimal.valueOf(25), new CodePolicy("SECRET")));
                policy.activate();

                assertThat(policy.isPurchaseEligibleForDiscount(contextWithCode("WRONG", REGULAR_ZONE)))
                                .isFalse();
                assertThat(policy.getFullDiscountSummary(
                                contextWithCode("WRONG", REGULAR_ZONE),
                                Money.of(BigDecimal.valueOf(100), "ILS")).appliedDiscountsNames()).isEmpty();
        }

        @Test
        void fullDiscountSummaryRejectsNullBaseCost() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(GeneralNoExpiryDiscount("Visible", BigDecimal.TEN));
                policy.activate();

                assertThatThrownBy(() -> policy.getFullDiscountSummary(contextWithTickets(REGULAR_ZONE), null))
                                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void generatedSnapshotFromStackedSummaryJoinsDiscountNames_UAT45() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.allowStacking();
                policy.addDiscount(GeneralNoExpiryDiscount("Early bird", BigDecimal.valueOf(10)));
                policy.addDiscount(GeneralNoExpiryDiscount("Member", BigDecimal.valueOf(5)));
                policy.activate();
                Money baseCost = Money.of(BigDecimal.valueOf(200), "ILS");

                DiscountSummary summary = policy.getFullDiscountSummary(contextWithTickets(REGULAR_ZONE), baseCost);
                DiscountSnapshot snapshot = policy.generateDiscountSnapshot(summary, baseCost);

                assertThat(snapshot.discountName()).isEqualTo("Early bird ; Member");
                assertThat(snapshot.discountAmount().amount()).isEqualByComparingTo("30");
                assertThat(snapshot.discountAmount().currency()).isEqualTo("ILS");
        }

        @Test
        void generateDiscountSnapshotRejectsNullBaseCost() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                DiscountSummary summary = DiscountSummary.noDiscountSummary();

                assertThatThrownBy(() -> policy.generateDiscountSnapshot(summary, null))
                                .isInstanceOf(NullPointerException.class);
        }

        // DP-08 / DP-14 / UAT-45 / UAT-46:
        // Public preview should include only visible, non-expired discounts.
        @Test
        void visibleDiscounts_shouldReturnOnlyVisibleNonExpiredDiscounts_UAT45_UAT46_DP08_DP14() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Visible valid",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                policy.addDiscount(new Discount(
                                "Visible expired",
                                BigDecimal.valueOf(30),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));

                policy.addDiscount(new Discount(
                                "Hidden valid",
                                BigDecimal.valueOf(40),
                                AlwaysTruePolicy.INSTANCE,
                                false,
                                LocalDate.now().plusDays(1)));

                assertThat(policy.visibleDiscounts())
                                .extracting(Discount::getDiscountName)
                                .containsExactly("Visible valid");

                assertThat(policy.doesHaveVisibleDiscounts()).isTrue();
        }

        // DP-14 / TST-09:
        // Expired/non-expired query methods should classify discounts by endDate.
        @Test
        void expiredAndNonExpiredDiscounts_shouldClassifyByEndDate_DP14_TST09() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Expired",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));

                policy.addDiscount(new Discount(
                                "Valid",
                                BigDecimal.valueOf(30),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                policy.addDiscount(new Discount(
                                "No end date",
                                BigDecimal.valueOf(10),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                null));

                assertThat(policy.expiredDiscounts())
                                .extracting(Discount::getDiscountName)
                                .containsExactly("Expired");

                assertThat(policy.nonExpiredDiscounts())
                                .extracting(Discount::getDiscountName)
                                .containsExactlyInAnyOrder("Valid", "No end date");
        }

        // DP-08 / DP-10 / DP-14:
        // Context-visible discounts should not expose hidden coupons, even if the
        // coupon code is valid.
        @Test
        void visibleDiscountsWithContext_shouldNotExposeHiddenCouponEvenWhenCodeMatches_UAT46_DP08_DP10() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Visible promo",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                policy.addDiscount(new Discount(
                                "Hidden coupon",
                                BigDecimal.valueOf(30),
                                new CodePolicy("SECRET30"),
                                false,
                                LocalDate.now().plusDays(1)));

                policy.activate();

                assertThat(policy.visibleDiscounts(contextWithCode("SECRET30", REGULAR_ZONE)))
                                .extracting(Discount::getDiscountName)
                                .containsExactly("Visible promo");
        }

        // DP-06 / DP-10:
        // Applicable discounts should include non-expired discounts whose rule passes.
        @Test
        void applicableDiscounts_shouldReturnOnlyNonExpiredDiscountsWhoseRulePasses_DP10_TST09() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Valid applicable",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                policy.addDiscount(new Discount(
                                "Expired applicable",
                                BigDecimal.valueOf(30),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));

                policy.addDiscount(new Discount(
                                "Wrong coupon",
                                BigDecimal.valueOf(40),
                                new CodePolicy("SAVE40"),
                                false,
                                LocalDate.now().plusDays(1)));

                policy.activate();

                assertThat(policy.applicableDiscounts(contextWithCode("WRONG", REGULAR_ZONE)))
                                .extracting(Discount::getDiscountName)
                                .containsExactly("Valid applicable");
        }

        // DP-14 / UAT-45:
        // Policy with only expired visible discounts should not count as having visible
        // discounts.
        @Test
        void doesHaveVisibleDiscounts_whenOnlyVisibleDiscountIsExpired_shouldReturnFalse_DP14() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Expired visible",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));
                assertThat(! policy.visibleDiscounts(LocalDate.now().minusDays(1)).isEmpty());
                assertThat(policy.visibleDiscounts()).isEmpty();
                assertThat(policy.visibleDiscounts(LocalDate.now())).isEmpty();
                assertThat(policy.doesHaveVisibleDiscounts()).isFalse();
        }

        // UC9 / DP-14:
        // Expired discounts should not affect checkout discount percent.
        @Test
        void getFullDiscountPercent_shouldIgnoreExpiredDiscounts_UC9_DP14() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Expired 90",
                                BigDecimal.valueOf(90),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));

                policy.addDiscount(new Discount(
                                "Valid 20",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                policy.activate();

                assertThat(policy.getFullDiscountPercent(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo("20");
        }

        // UC9 / DP-14:
        // Expired discounts should not appear in checkout summary.
        @Test
        void getFullDiscountSummary_shouldIgnoreExpiredDiscounts_UC9_DP14() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Expired 90",
                                BigDecimal.valueOf(90),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));

                policy.addDiscount(new Discount(
                                "Valid 20",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));

                policy.activate();

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextWithTickets(REGULAR_ZONE),
                                Money.of(BigDecimal.valueOf(100), "ILS"));

                assertThat(summary.appliedDiscountsNames()).containsExactly("Valid 20");
                assertThat(summary.appliedDiscountPercents().get(0)).isEqualByComparingTo("20");
                assertThat(summary.totalDiscount()).isEqualByComparingTo("20");
        }

        // DP-14 / PP-02 style reason reporting:
        // Expired discount should make the reason-aware policy evaluation fail.
        @Test
        void evaluateIfDiscountApplyForPurchase_whenOnlyDiscountExpired_shouldReturnFailureReason_DP14() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);

                policy.addDiscount(new Discount(
                                "Expired",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1)));

                policy.activate();

                PolicyValidationResult result = policy
                                .evaluateIfDiscountApplyForPurchase(contextWithTickets(REGULAR_ZONE));

                assertThat(result.isSuccess()).isFalse();
                assertThat(policy.isPurchaseEligibleForDiscount(contextWithTickets(REGULAR_ZONE))).isFalse();
        }

        // DP-10:
        // discountedEventIds should return real EventId values from scope, not a
        // stringified set.
        @Test
        void discountedEventIds_shouldReturnScopedEventIds_DP10() {
                DiscountPolicy policy = DiscountPolicy.inactiveForEvents(
                                COMPANY_ID,
                                Set.of(EVENT_ID, OTHER_EVENT_ID));

                assertThat(policy.discountedEventIds())
                                .containsExactlyInAnyOrder(EVENT_ID, OTHER_EVENT_ID);
        }

        // DP-14 / UAT-45:
        // activeVisibleDiscountsInfo should expose only visible discounts for active
        // policies.
        @Test
        void activeVisibleDiscountsInfo_whenActive_shouldReturnOnlyVisibleDiscounts_UAT45_DP14() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.GeneralDiscount(
                                "Visible",
                                BigDecimal.valueOf(20),
                                LocalDate.now().plusDays(5)));
                policy.addDiscount(Discount.CouponDiscount(
                                "Hidden",
                                BigDecimal.valueOf(15),
                                LocalDate.now().plusDays(5),
                                "SAVE15"));
                policy.activate();

                List<DiscountInfo> infos = policy.activeVisibleDiscountsInfo();

                assertThat(infos).hasSize(1);
                assertThat(infos.get(0).discountName()).isEqualTo("Visible");
                assertThat(infos.get(0).discountPercent()).isEqualByComparingTo("20");
        }

        // DP-14:
        // activeVisibleDiscountsInfo should return empty for inactive policy.
        @Test
        void activeVisibleDiscountsInfo_whenInactive_shouldReturnEmpty() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.GeneralDiscount(
                                "Visible",
                                BigDecimal.valueOf(20),
                                LocalDate.now().plusDays(5)));

                assertThat(policy.activeVisibleDiscountsInfo()).isEmpty();
        }

        // DP-09 / DP-11:
        // Non-stackable policy should choose the maximum applicable discount.
        @Test
        void getFullDiscountSummary_whenNotStackable_shouldUseBestDiscountOnly_DP11() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.GeneralDiscount("Ten", BigDecimal.TEN, null));
                policy.addDiscount(Discount.GeneralDiscount("Twenty", BigDecimal.valueOf(20), null));
                policy.activate();

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE),
                                new Money(BigDecimal.valueOf(200), "ILS"));

                assertThat(summary.appliedDiscountsNames()).containsExactly("Twenty");
                assertThat(summary.appliedDiscountPercents()).containsExactly(BigDecimal.valueOf(20));
                assertThat(summary.actualDiscountAmount()).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class).containsExactly(BigDecimal.valueOf(40));
                assertThat(summary.totalDiscount()).isEqualByComparingTo("40");
        }

        // DP-09 / DP-12:
        // Stackable policy should sum all applicable discounts.
        @Test
        void getFullDiscountSummary_whenStackable_shouldSumApplicableDiscounts_DP12() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.allowStacking();
                policy.addDiscount(Discount.GeneralDiscount("Ten", BigDecimal.TEN, null));
                policy.addDiscount(Discount.GeneralDiscount("Twenty", BigDecimal.valueOf(20), null));
                policy.activate();

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE),
                                new Money(BigDecimal.valueOf(200), "ILS"));

                assertThat(summary.appliedDiscountsNames()).containsExactly("Ten", "Twenty");
                assertThat(summary.appliedDiscountPercents())
                                .containsExactly(BigDecimal.TEN, BigDecimal.valueOf(20));
                assertThat(summary.totalDiscount()).isEqualByComparingTo("60");
        }

        // DP-13:
        // Stacked discount over 100% should cap total discount amount at base cost.
        @Test
        void getFullDiscountSummary_whenStackedPercentExceeds100_shouldCapTotalAtBaseCost_DP13() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.allowStacking();
                policy.addDiscount(Discount.GeneralDiscount("Sixty", BigDecimal.valueOf(60), null));
                policy.addDiscount(Discount.GeneralDiscount("Fifty", BigDecimal.valueOf(50), null));
                policy.activate();

                DiscountSummary summary = policy.getFullDiscountSummary(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE),
                                new Money(BigDecimal.valueOf(100), "ILS"));

                assertThat(summary.cappedAt100Percent()).isTrue();
                assertThat(summary.totalDiscount()).isEqualByComparingTo("100");
        }

        // DP-07 / DP-08 / UAT-46 / UAT-47:
        // Coupon discount should apply only with matching code and stay hidden from
        // visible list.
        @Test
        void couponDiscount_shouldApplyOnlyWithMatchingCodeAndStayHidden_UAT46_UAT47() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.CouponDiscount(
                                "Secret",
                                BigDecimal.valueOf(15),
                                null,
                                "SAVE15"));
                policy.activate();

                assertThat(policy.visibleDiscounts()).isEmpty();

                assertThat(policy.getFullDiscountPercent(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, "SAVE15", REGULAR_ZONE)))
                                .isEqualByComparingTo("15");

                assertThat(policy.getFullDiscountPercent(
                                contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, "WRONG", REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        // DP-06 / TST-09:
        // Expired discounts should not be applicable.
        @Test
        void applicableDiscounts_shouldExcludeExpiredDiscounts_TST09() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.GeneralDiscount(
                                "Expired",
                                BigDecimal.valueOf(20),
                                LocalDate.now().minusDays(1)));
                policy.activate();

                PurchaseContext context = contextForCompanyAndEvent(COMPANY_ID, EVENT_ID, null, REGULAR_ZONE);

                assertThat(policy.applicableDiscounts(context)).isEmpty();
                assertThat(policy.getFullDiscountPercent(context)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        // DP-14:
        // dateOfLatestActiveDiscount should return latest end date among matching
        // discounts.
        @Test
        void dateOfLatestActiveDiscount_shouldReturnLatestEndDate_DP14() {
                LocalDate earlier = LocalDate.now().plusDays(2);
                LocalDate later = LocalDate.now().plusDays(8);

                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.GeneralDiscount("Earlier", BigDecimal.TEN, earlier));
                policy.addDiscount(Discount.GeneralDiscount("Later", BigDecimal.valueOf(20), later));

                assertThat(policy.dateOfLatestActiveDiscount(true)).isEqualTo(later);
        }

        // DP-14:
        // If any matching discount has no end date, policy-level latest date is
        // open-ended/null.
        @Test
        void dateOfLatestActiveDiscount_whenAnyMatchingDiscountHasNoEndDate_shouldReturnNull() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.GeneralDiscount("Open ended", BigDecimal.TEN, null));
                policy.addDiscount(Discount.GeneralDiscount("Limited", BigDecimal.valueOf(20),
                                LocalDate.now().plusDays(3)));

                assertThat(policy.dateOfLatestActiveDiscount(true)).isNull();
        }

        // DP-14:
        @Test
        void dateOfLatestActiveDiscount_whenNoVisibleDiscountsAndRequireVisible_shouldThrow() {
                DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(COMPANY_ID);
                policy.addDiscount(Discount.CouponDiscount("Hidden", BigDecimal.TEN, null, "CODE"));

                assertThatThrownBy(() -> policy.dateOfLatestActiveDiscount(true))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("No matching discounts");
        }

        // DP-10:
        // withDiscounts should not mutate original policy and should preserve
        // identity/scope.
        @Test
        void withDiscounts_shouldReturnNewPolicyWithAddedDiscountsWithoutMutatingOriginal() {
                DiscountPolicy original = DiscountPolicy.inactiveEventPolicy(COMPANY_ID, EVENT_ID);
                Discount discount = Discount.GeneralDiscount("Visible", BigDecimal.TEN, null);

                DiscountPolicy updated = DiscountPolicy.withDiscounts(original, List.of(discount));

                assertThat(original.discounts()).isEmpty();
                assertThat(updated.discounts()).containsExactly(discount);
                assertThat(updated.id()).isEqualTo(original.id());
                assertThat(updated.companyId()).isEqualTo(COMPANY_ID);
                assertThat(updated.scope()).isEqualTo(original.scope());
        }

        // DDD safety:
        // Public constructor should defensively copy the supplied discount list.
        // This test will fail until you fix the public constructor copy issue.
        @Test
        void constructor_shouldDefensivelyCopyDiscountList() {
                List<Discount> discounts = new ArrayList<>();
                discounts.add(Discount.GeneralDiscount("Initial", BigDecimal.TEN, null));

                DiscountPolicy policy = new DiscountPolicy(
                                COMPANY_ID,
                                PolicyScope.companyWideScope(),
                                discounts,
                                false,
                                true);

                discounts.add(Discount.GeneralDiscount("Late mutation", BigDecimal.valueOf(20), null));

                assertThat(policy.discounts()).hasSize(1);
                assertThat(policy.discounts().get(0).getDiscountName()).isEqualTo("Initial");
        }

        private static DiscountPolicy eventPolicy(EventId eventId) {
                return DiscountPolicy.inactiveEventPolicy(COMPANY_ID, eventId);
        }

        private static DiscountPolicy activeEventPolicy(EventId eventId) {
                DiscountPolicy policy = eventPolicy(eventId);
                policy.addDiscount(GeneralNoExpiryDiscount("Base", BigDecimal.ONE));
                policy.activate();
                return policy;
        }

        private static Money baseCost100() {
                return Money.of(BigDecimal.valueOf(100), "ILS");
        }

}
