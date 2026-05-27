package com.eventsystem.domain.policy.basic;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
                .hasMessageContaining("Cannot Purchase more than 2 tickets")
                .hasMessageContaining("Test Event");
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
                .hasMessageContaining("Cannot Purchase less than 2 tickets")
                .hasMessageContaining("Test Event");
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
                .hasMessageContaining("Wrong code")
                .hasMessageContaining("Test Event");
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
                .hasMessageContaining("Test Event");
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
        assertThat(new UntilDatePolicy(LocalDate.now().plusDays(1)).validate(contextWithTickets(REGULAR_ZONE))).isTrue();
    }

    @Test
    void untilDatePolicy_rejectsAfterDeadline_UAT45() {
        UntilDatePolicy policy = new UntilDatePolicy(LocalDate.now().minusDays(1));
        PurchaseContext context = contextWithTickets(REGULAR_ZONE);

        assertThat(policy.validate(context)).isFalse();
        assertThatThrownBy(() -> policy.require(context))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("after Date");
    }

    @Test
    void afterDatePolicy_acceptsOnlyAfterConfiguredDate() {
        assertThat(new AfterDatePolicy(LocalDate.now().minusDays(1)).validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThat(new AfterDatePolicy(LocalDate.now()).validate(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(new AfterDatePolicy(LocalDate.now().plusDays(1)).validate(contextWithTickets(REGULAR_ZONE))).isFalse();
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
    void noSingleEmptySeatPolicy_isExplicitlyNotImplementedYet() {
        NoSingleEmptySeatPolicy policy = new NoSingleEmptySeatPolicy();

        assertThatThrownBy(() -> policy.validate(contextWithTickets(new ZoneId("z1"))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unimplemented");
    }

    @Test
    void minAgePolicy_rejectsBuyerWhoseBirthdayIsTomorrow() {
        MinAgePolicy policy = new MinAgePolicy(18);

        PurchaseContext context = contextWithBirthDate(
                LocalDate.now().minusYears(18).plusDays(1),
                REGULAR_ZONE
        );

        assertThat(policy.validate(context)).isFalse();
    }

    @Test
    void minAgePolicy_acceptsBuyerAfterBirthday() {
        MinAgePolicy policy = new MinAgePolicy(18);

        PurchaseContext context = contextWithBirthDate(
                LocalDate.now().minusYears(18).minusDays(1),
                REGULAR_ZONE
        );

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
}
