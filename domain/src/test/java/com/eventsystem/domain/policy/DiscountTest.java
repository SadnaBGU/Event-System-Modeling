package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountInfo;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.rule.basic.CodePolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;

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

        // DP-01 / DP-13:
        // Discount percent must be valid and bounded by business limits.
        @Test
        void discountConstructor_rejectsInvalidPercent_DP01_DP13() {
                assertThatThrownBy(() -> new Discount("Zero", BigDecimal.ZERO, AlwaysTruePolicy.INSTANCE))
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("between 0 and 100");

                assertThatThrownBy(() -> new Discount("Negative", BigDecimal.valueOf(-1), AlwaysTruePolicy.INSTANCE))
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("between 0 and 100");

                assertThatThrownBy(() -> new Discount("Too high", BigDecimal.valueOf(101), AlwaysTruePolicy.INSTANCE))
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("between 0 and 100");

                assertThatThrownBy(() -> new Discount("Null percent", null, AlwaysTruePolicy.INSTANCE))
                                .isInstanceOf(DiscountPolicyException.class)
                                .hasMessageContaining("between 0 and 100");
        }

        // DP-14 / UAT-45:
        // Visible discount exposes name, percent, and promotion end date through
        // info().
        @Test
        void info_shouldExposeDiscountNamePercentAndEndDate_UAT45_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(10);

                Discount discount = new Discount(
                                "Early bird",
                                BigDecimal.valueOf(20),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                endDate);

                DiscountInfo info = discount.info();

                assertThat(info.discountName()).isEqualTo("Early bird");
                assertThat(info.discountPercent()).isEqualByComparingTo("20");
                assertThat(info.endDate()).isEqualTo(endDate);
        }

        // DP-08 / UAT-46:
        // Coupon discount factory should create hidden discount that requires the code.
        @Test
        void couponDiscountFactory_shouldCreateHiddenDiscountThatRequiresCode_UAT46() {
                Discount discount = Discount.CouponDiscount(
                                "Secret coupon",
                                BigDecimal.valueOf(15),
                                LocalDate.now().plusDays(5),
                                "SAVE15");

                assertThat(discount.isVisible()).isFalse();
                assertThat(discount.validateDiscount(contextWithCode("SAVE15", REGULAR_ZONE))).isTrue();
                assertThat(discount.validateDiscount(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
                assertThat(discount.getDiscountPercentForContext(contextWithCode("SAVE15", REGULAR_ZONE)))
                                .isEqualByComparingTo("15");
                assertThat(discount.getDiscountPercentForContext(contextWithCode("WRONG", REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        // DP-06 / TST-09:
        // Expired discount should not apply.
        @Test
        void expiredDiscount_shouldNotApply_TST09() {
                Discount discount = new Discount(
                                "Expired",
                                BigDecimal.valueOf(25),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().minusDays(1));

                PolicyValidationResult result = discount.evaluateDiscount(contextWithTickets(REGULAR_ZONE));

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("expired");
                assertThat(discount.getDiscountPercentForContext(contextWithTickets(REGULAR_ZONE)))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        // DP-14:
        // General discount factory should create visible discount with optional end
        // date.
        @Test
        void generalDiscountFactory_shouldCreateVisibleDiscountWithEndDate_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(3);

                Discount discount = Discount.GeneralDiscount(
                                "General",
                                BigDecimal.valueOf(10),
                                endDate);

                assertThat(discount.isVisible()).isTrue();
                assertThat(discount.getDiscountName()).isEqualTo("General");
                assertThat(discount.getDiscountPercent()).isEqualByComparingTo("10");
                assertThat(discount.getEndDate()).isEqualTo(endDate);
                assertThat(discount.canExpire()).isTrue();
        }

        // DP-08 / DP-14:
        // Visibility conversion helpers should keep discount data and only change
        // visibility.
        @Test
        void toHiddenAndToVisible_shouldPreserveDiscountData_DP08_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(9);

                Discount visible = new Discount(
                                "Promo",
                                BigDecimal.valueOf(30),
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                endDate);

                Discount hidden = Discount.toHidden(visible);
                Discount visibleAgain = Discount.toVisible(hidden);

                assertThat(hidden.isVisible()).isFalse();
                assertThat(hidden.getDiscountName()).isEqualTo("Promo");
                assertThat(hidden.getDiscountPercent()).isEqualByComparingTo("30");
                assertThat(hidden.getEndDate()).isEqualTo(endDate);

                assertThat(visibleAgain.isVisible()).isTrue();
                assertThat(visibleAgain.getDiscountName()).isEqualTo("Promo");
                assertThat(visibleAgain.getDiscountPercent()).isEqualByComparingTo("30");
                assertThat(visibleAgain.getEndDate()).isEqualTo(endDate);
        }

            // DP-14:
    // DiscountInfo exposes promotion metadata and calculates amount off.
    @Test
    void amountOffWithDiscount_shouldCalculatePercentOfBaseCost_DP14() {
        DiscountInfo info = new DiscountInfo(
                "Early bird",
                BigDecimal.valueOf(20),
                LocalDate.now().plusDays(5)
        );

        assertThat(info.amountOffWithDiscount(BigDecimal.valueOf(200)))
                .isEqualByComparingTo("40");
    }

    @Test
    void amountOffWithDiscount_shouldRejectNullBaseCost() {
        DiscountInfo info = new DiscountInfo("Promo", BigDecimal.TEN, null);

        assertThatThrownBy(() -> info.amountOffWithDiscount(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseCost");
    }

    @Test
    void amountOffWithDiscount_shouldRejectNegativeBaseCost() {
        DiscountInfo info = new DiscountInfo("Promo", BigDecimal.TEN, null);

        assertThatThrownBy(() -> info.amountOffWithDiscount(BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseCost cannot be negative");
    }

    @Test
    void constructor_shouldRejectNullRequiredFields() {
        assertThatThrownBy(() -> new DiscountInfo(null, BigDecimal.TEN, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new DiscountInfo("Promo", null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
