package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Discount value-object tests.
 *
 * UAT mapping:
 * - UAT-45 / UC16: visible discount can be created and evaluated.
 * - UAT-46 / UC16: coupon-code discount can be created and evaluated.
 * - UAT-47 / UC16: wrong coupon gives zero valid discount.
 */
class DiscountTest {

        @Test
        void generalDiscountIsAlwaysValid_UAT45() {
                Discount discount = GeneralNoExpiryDiscount("Early bird", BigDecimal.valueOf(20));

                assertThat(discount.validateDiscount(contextWithTickets(REGULAR_ZONE))).isTrue();
                assertThat(discount.getDiscountPercentForContext(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo("20");
                assertThat(discount.getDiscountName()).isEqualTo("Early bird");
                assertThat(discount.getDiscountPercent()).isEqualByComparingTo("20");
        }

        @Test
        void conditionalDiscountReturnsPercentOnlyWhenConditionPasses_UAT46_UAT47() {
                Discount discount = new Discount("Coupon", BigDecimal.valueOf(15), new CodePolicy("SAVE15"));

                assertThat(discount.validateDiscount(contextWithCode("SAVE15", REGULAR_ZONE))).isTrue();
                assertThat(discount.getDiscountPercentForContext(contextWithCode("SAVE15", REGULAR_ZONE)))
                                .isEqualByComparingTo("15");

                assertThat(discount.validateDiscount(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
                assertThat(discount.getDiscountPercentForContext(contextWithCode("WRONG", REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void discountCreatedFromPolicyListRequiresAllConditions_UAT45() {
                Discount discount = new Discount(
                                "VIP bulk",
                                BigDecimal.valueOf(25),
                                List.of(new CodePolicy("VIP"), new MinTicketPolicy(2)));

                assertThat(discount.validateDiscount(contextWithCode("VIP", REGULAR_ZONE, VIP_ZONE))).isTrue();
                assertThat(discount.validateDiscount(contextWithCode("VIP", REGULAR_ZONE))).isFalse();
                assertThat(discount.validateDiscount(contextWithCode("WRONG", REGULAR_ZONE, VIP_ZONE))).isFalse();
        }

        @Test
        void discountRejectsInvalidPercent() {
                assertThatThrownBy(() -> GeneralNoExpiryDiscount("bad", null))
                                .isInstanceOf(DiscountPolicyException.class);
                assertThatThrownBy(() -> GeneralNoExpiryDiscount("bad", BigDecimal.ZERO))
                                .isInstanceOf(DiscountPolicyException.class);
                assertThatThrownBy(() -> GeneralNoExpiryDiscount("bad", BigDecimal.valueOf(-1)))
                                .isInstanceOf(DiscountPolicyException.class);
                assertThatThrownBy(() -> GeneralNoExpiryDiscount("bad", BigDecimal.valueOf(101)))
                                .isInstanceOf(DiscountPolicyException.class);
        }

        @Test
        void discountRejectsInvalidNameOrPolicy() {
                assertThatThrownBy(() -> new Discount(null, BigDecimal.TEN, new CodePolicy("A")))
                                .isInstanceOf(DiscountPolicyException.class);
                assertThatThrownBy(() -> new Discount("", BigDecimal.TEN, new CodePolicy("A")))
                                .isInstanceOf(DiscountPolicyException.class);
                assertThatThrownBy(() -> new Discount("   ", BigDecimal.TEN, new CodePolicy("A")))
                                .isInstanceOf(DiscountPolicyException.class);
                assertThatThrownBy(() -> new Discount("Valid", BigDecimal.TEN, (IPolicy) null))
                                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void discountRejectsPolicyListWithNullElement() {
                assertThatThrownBy(
                                () -> new Discount("Invalid", BigDecimal.TEN, Arrays.asList(new CodePolicy("A"), null)))
                                .isInstanceOf(RuntimeException.class);
        }

        @Test
        void discountAllowsExactly100Percent() {
                Discount discount = GeneralNoExpiryDiscount("Free", BigDecimal.valueOf(100));

                assertThat(discount.getDiscountPercent()).isEqualByComparingTo("100");
                assertThat(discount.validateDiscount(contextWithTickets(REGULAR_ZONE))).isTrue();
        }

        @Test
        void discountCreatedFromPolicyListDefensivelyCopiesPolicies() {
                java.util.ArrayList<IPolicy> policies = new java.util.ArrayList<>();
                policies.add(new CodePolicy("SAVE"));

                Discount discount = new Discount("Coupon", BigDecimal.TEN, policies);

                policies.clear();

                assertThat(discount.validateDiscount(contextWithCode("SAVE", REGULAR_ZONE))).isTrue();
        }

        @Test
        void discountRejectsNullContextDuringEvaluation() {
                Discount discount = GeneralNoExpiryDiscount("Visible", BigDecimal.TEN);

                assertThatThrownBy(() -> discount.validateDiscount(null))
                                .isInstanceOf(NullPointerException.class);
        }

        // DP-08 / UAT-46:
        // Coupon/hidden discount is not public; visibility flag should mark it hidden.
        @Test
        void hiddenDiscount_shouldNotBeVisible_UAT46_DP08() {
                Discount discount = new Discount(
                                "Secret coupon",
                                BigDecimal.valueOf(20),
                                new CodePolicy("SECRET"),
                                false,
                                null);

                assertThat(discount.isVisible()).isFalse();
                assertThat(discount.getEndDate()).isNull();
                assertThat(discount.canExpire()).isFalse();
                assertThat(discount.isExpired()).isFalse();
        }

        // DP-14 / UAT-45:
        // Visible discount should expose promotion end time when it exists.
        @Test
        void visibleDiscountWithFutureEndDate_shouldExposeEndDateAndRemainValid_UAT45_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(7);

                Discount discount = new Discount(
                                "Early bird",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                endDate);

                assertThat(discount.isVisible()).isTrue();
                assertThat(discount.getEndDate()).isEqualTo(endDate);
                assertThat(discount.canExpire()).isTrue();
                assertThat(discount.isExpired()).isFalse();
                assertThat(discount.validateDiscount(contextWithTickets(REGULAR_ZONE))).isTrue();
                assertThat(discount.getDiscountPercentForContext(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo("20");
        }

        // DP-06 / DP-14 / TST-09:
        // Expired endDate should make discount evaluation fail and percent become zero.
        @Test
        void expiredDiscount_shouldEvaluateFailureAndReturnZeroPercent_UAT45_DP14_TST09() {
                LocalDate endDate = LocalDate.now().minusDays(1);

                Discount discount = new Discount(
                                "Expired early bird",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                endDate);

                PolicyValidationResult result = discount.evaluateDiscount(contextWithTickets(REGULAR_ZONE));

                assertThat(discount.canExpire()).isTrue();
                assertThat(discount.isExpired()).isTrue();
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Discount has expired");
                assertThat(discount.getDiscountPercentForContext(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        // DP-08 / DP-14:
        // Visibility conversion helpers should preserve percent, policy, and endDate
        // while changing exposure.
        @Test
        void toHiddenAndToVisible_shouldPreserveEndDateAndDiscountData_DP08_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(10);

                Discount original = new Discount(
                                "Promo",
                                BigDecimal.valueOf(30),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                endDate);

                Discount hidden = Discount.toHidden(original);
                Discount visibleAgain = Discount.toVisible(hidden);

                assertThat(hidden.isVisible()).isFalse();
                assertThat(hidden.getDiscountName()).isEqualTo("Promo");
                assertThat(hidden.getDiscountPercent()).isEqualByComparingTo("30");
                assertThat(hidden.getEndDate()).isEqualTo(endDate);

                assertThat(visibleAgain.isVisible()).isTrue();
                assertThat(visibleAgain.getEndDate()).isEqualTo(endDate);
                assertThat(visibleAgain.getDiscountPercent()).isEqualByComparingTo("30");
        }

        // DP-07 / DP-08 / UAT-46:
        // Coupon factory should create hidden coupon discount with endDate support.
        @Test
        void couponDiscountFactory_shouldCreateHiddenCouponWithEndDate_UAT46_DP07_DP08() {
                LocalDate endDate = LocalDate.now().plusDays(5);

                Discount discount = Discount.CouponDiscount(
                                "Student coupon",
                                BigDecimal.valueOf(15),
                                endDate,
                                "STUDENT15");

                assertThat(discount.isVisible()).isFalse();
                assertThat(discount.getEndDate()).isEqualTo(endDate);
                assertThat(discount.validateDiscount(contextWithCode("STUDENT15", REGULAR_ZONE))).isTrue();
                assertThat(discount.validateDiscount(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
        }
}
