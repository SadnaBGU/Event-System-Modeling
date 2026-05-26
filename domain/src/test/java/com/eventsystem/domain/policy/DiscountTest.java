package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.eventsystem.domain.policy.PolicyTestFixtures.REGULAR_ZONE;
import static com.eventsystem.domain.policy.PolicyTestFixtures.VIP_ZONE;
import static com.eventsystem.domain.policy.PolicyTestFixtures.contextWithCode;
import static com.eventsystem.domain.policy.PolicyTestFixtures.contextWithTickets;
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
        Discount discount = Discount.GeneralDiscount("Early bird", BigDecimal.valueOf(20));

        assertThat(discount.validateDiscount(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThat(discount.getValidDiscountAmount(contextWithTickets(REGULAR_ZONE)))
                .isEqualByComparingTo("20");
        assertThat(discount.getDiscountName()).isEqualTo("Early bird");
        assertThat(discount.getDiscountPercent()).isEqualByComparingTo("20");
    }

    @Test
    void conditionalDiscountReturnsPercentOnlyWhenConditionPasses_UAT46_UAT47() {
        Discount discount = new Discount("Coupon", BigDecimal.valueOf(15), new CodePolicy("SAVE15"));

        assertThat(discount.validateDiscount(contextWithCode("SAVE15", REGULAR_ZONE))).isTrue();
        assertThat(discount.getValidDiscountAmount(contextWithCode("SAVE15", REGULAR_ZONE)))
                .isEqualByComparingTo("15");

        assertThat(discount.validateDiscount(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
        assertThat(discount.getValidDiscountAmount(contextWithCode("WRONG", REGULAR_ZONE)))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void discountCreatedFromPolicyListRequiresAllConditions_UAT45() {
        Discount discount = new Discount(
                "VIP bulk",
                BigDecimal.valueOf(25),
                List.of(new CodePolicy("VIP"), new MinTicketPolicy(2))
        );

        assertThat(discount.validateDiscount(contextWithCode("VIP", REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(discount.validateDiscount(contextWithCode("VIP", REGULAR_ZONE))).isFalse();
        assertThat(discount.validateDiscount(contextWithCode("WRONG", REGULAR_ZONE, VIP_ZONE))).isFalse();
    }

    @Test
    void discountRejectsInvalidPercent() {
        assertThatThrownBy(() -> Discount.GeneralDiscount("bad", null))
                .isInstanceOf(DiscountPolicyException.class);
        assertThatThrownBy(() -> Discount.GeneralDiscount("bad", BigDecimal.ZERO))
                .isInstanceOf(DiscountPolicyException.class);
        assertThatThrownBy(() -> Discount.GeneralDiscount("bad", BigDecimal.valueOf(-1)))
                .isInstanceOf(DiscountPolicyException.class);
        assertThatThrownBy(() -> Discount.GeneralDiscount("bad", BigDecimal.valueOf(101)))
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
        assertThatThrownBy(() -> new Discount("Invalid", BigDecimal.TEN, Arrays.asList(new CodePolicy("A"), null)))
                .isInstanceOf(RuntimeException.class);
    }
}
