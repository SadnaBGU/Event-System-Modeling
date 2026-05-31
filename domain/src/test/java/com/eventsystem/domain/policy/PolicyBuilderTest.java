package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.basic.IBasicPolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


/**
 * PolicyBuilder tests.
 *
 * UAT mapping:
 * - UAT-44 / UC16: fluent builder can create max-ticket purchase policy.
 * - UAT-45 / UC16: fluent builder can create visible date-based discount condition.
 * - UAT-46 / UC16: fluent builder can create coupon-code condition.
 */
class PolicyBuilderTest {

    @Test
    void emptyBuilderCreatesAllowAllPolicy() {
        IPolicy policy = PolicyBuilder.start().build();

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
    }

    @Test
    void builderCreatesAndPolicyFromChainedRules_UAT44() {
        IPolicy policy = PolicyBuilder.start()
                .minTickets(2)
                .maxTickets(4)
                .build();

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE, BALCONY_ZONE, REGULAR_ZONE, VIP_ZONE))).isFalse();
    }

    @Test
    void builderCreatesDateAndCouponConditions_UAT45_UAT46() {
        IPolicy policy = PolicyBuilder.start()
                .beforeDate(LocalDate.now().plusDays(1))
                .code("EARLY20")
                .build();

        assertThat(policy.validate(contextWithCode("EARLY20", REGULAR_ZONE))).isTrue();
        assertThat(policy.validate(contextWithCode("WRONG", REGULAR_ZONE))).isFalse();
    }

    @Test
    void staticAndRequiresAllPolicies() {
        IPolicy policy = PolicyBuilder.and(new MinTicketPolicy(2), new MaxTicketPolicy(2));

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE, BALCONY_ZONE))).isFalse();
    }

    @Test
    void staticOrRequiresAnyPolicy() {
        IPolicy policy = PolicyBuilder.or(new MinTicketPolicy(3), new MaxTicketPolicy(1));

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE, BALCONY_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isFalse();
    }

    @Test
    void zoneSpecificBuilderSupportsRestrictionMode() {
        IPolicy policy = PolicyBuilder.PolicyRestrictToZones(
                Set.of(VIP_ZONE),
                new MaxTicketPolicy(1)
        );

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(VIP_ZONE))).isTrue();
        assertThat(policy.validate(contextWithTickets(VIP_ZONE, VIP_ZONE))).isFalse();
    }

    @Test
    void zoneSpecificBuilderSupportsRequirementMode() {
        IPolicy policy = PolicyBuilder.PolicyRequireFromZones(
                Set.of(VIP_ZONE),
                List.of(new MinTicketPolicy(2))
        );

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE))).isFalse();
        assertThat(policy.validate(contextWithTickets(VIP_ZONE))).isFalse();
        assertThat(policy.validate(contextWithTickets(VIP_ZONE, VIP_ZONE, REGULAR_ZONE))).isTrue();
    }

    @Test
    void staticAndRejectsNullPolicy() {
        assertThatThrownBy(() -> PolicyBuilder.and(new MaxTicketPolicy(1), null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void staticOrRejectsEmptyPolicies() {
        assertThatThrownBy(() -> PolicyBuilder.or())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void builderAddRejectsNullPolicy() {
        assertThatThrownBy(() -> PolicyBuilder.start().add(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void zoneSpecificBuilderRejectsInvalidArguments() {
        assertThatThrownBy(() -> PolicyBuilder.PolicyRestrictToZones(Set.of(), new MaxTicketPolicy(1)))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> PolicyBuilder.PolicyRestrictToZones(Set.of(VIP_ZONE), (IPolicy) null))
                .isInstanceOf(RuntimeException.class);
    }


    @Test
    void singleRuleBuilderReturnsThatRuleAndCoversMinAge_UAT44_PP05() {
        IPolicy policy = PolicyBuilder.start()
                .minAge(18)
                .build();

        assertThat(policy.isComposite()).isFalse();
        assertThat(policy.validate(contextWithBirthDate(LocalDate.now().minusYears(20), REGULAR_ZONE))).isTrue();
        assertThat(policy.validate(contextWithBirthDate(LocalDate.now().minusYears(16), REGULAR_ZONE))).isFalse();

        assertDoesNotThrow(() -> policy.require(contextWithBirthDate(LocalDate.now().minusYears(20), REGULAR_ZONE)));
        assertThatThrownBy(() -> policy.require(contextWithBirthDate(LocalDate.now().minusYears(16), REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Buyer must be over age 18");
    }

    @Test
    void builderAddAcceptsCustomPolicyAndSingleBuildReturnsSamePolicy_UAT44() {
        AtomicInteger validateCalls = new AtomicInteger(0);
        AtomicInteger requireCalls = new AtomicInteger(0);

        IPolicy customPolicy = new IBasicPolicy() {
            @Override
            public boolean validate(PurchaseContext context) {
                validateCalls.incrementAndGet();
                return context.ticketCount() == 2;
            }

            @Override
            public void require(PurchaseContext context) {
                requireCalls.incrementAndGet();
                if (!validate(context)) {
                    throw new PurchasePolicyException("expected exactly two tickets");
                }
            }
        };

        IPolicy built = PolicyBuilder.start()
                .add(customPolicy)
                .build();

        assertThat(built).isSameAs(customPolicy);
        assertThat(built.validate(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(validateCalls).hasValue(1);

        assertDoesNotThrow(() -> built.require(contextWithTickets(REGULAR_ZONE, VIP_ZONE)));
        assertThat(requireCalls).hasValue(1);
    }

    @Test
    void builderCreatesAfterDatePolicy_UAT45() {
        IPolicy alreadyAfterDate = PolicyBuilder.start()
                .afterDate(LocalDate.now().minusDays(1))
                .build();

        IPolicy notAfterFutureDate = PolicyBuilder.start()
                .afterDate(LocalDate.now().plusDays(1))
                .build();

        assertThat(alreadyAfterDate.validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThat(notAfterFutureDate.validate(contextWithTickets(REGULAR_ZONE))).isFalse();

        assertDoesNotThrow(() -> alreadyAfterDate.require(contextWithTickets(REGULAR_ZONE)));
        assertThatThrownBy(() -> notAfterFutureDate.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("before Date");
    }

    @Test
    void chainedBuilderCombinesMinAgeAfterDateCodeAndTicketRules_UAT44_UAT45_UAT46() {
        IPolicy policy = PolicyBuilder.start()
                .minAge(18)
                .afterDate(LocalDate.now().minusDays(1))
                .code("EARLY20")
                .add(new MinTicketPolicy(2))
                .maxTickets(3)
                .build();

        assertThat(policy.isComposite()).isTrue();
        assertThat(policy.validate(contextWithCode("EARLY20", REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(policy.validate(contextWithBirthDate(LocalDate.now().minusYears(16), REGULAR_ZONE, VIP_ZONE))).isFalse();
        assertThat(policy.validate(contextWithCode("WRONG", REGULAR_ZONE, VIP_ZONE))).isFalse();
        assertThat(policy.validate(contextWithCode("EARLY20", REGULAR_ZONE))).isFalse();
        assertThat(policy.validate(contextWithCode("EARLY20", REGULAR_ZONE, VIP_ZONE, BALCONY_ZONE, REGULAR_ZONE))).isFalse();
    }

    @Test
    void policyRequireFromZonesWithSinglePolicyRequiresAffectedZoneTickets_UAT45() {
        IPolicy vipRequiresAtLeastOneTicket = PolicyBuilder.PolicyRequireFromZones(
                Set.of(VIP_ZONE),
                new MinTicketPolicy(1)
        );

        assertThat(vipRequiresAtLeastOneTicket.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE))).isTrue();
        assertThat(vipRequiresAtLeastOneTicket.validate(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE))).isFalse();

        assertDoesNotThrow(() -> vipRequiresAtLeastOneTicket.require(contextWithTickets(VIP_ZONE, REGULAR_ZONE)));
        assertThatThrownBy(() -> vipRequiresAtLeastOneTicket.require(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("No tickets purchased for zones");
    }

    @Test
    void policyRestrictToZonesWithListBuildsAndPolicyOverAffectedTickets_UAT45_PP07() {
        IPolicy atMostOneVipTicket = PolicyBuilder.PolicyRestrictToZones(
                Set.of(VIP_ZONE),
                List.of(new MinTicketPolicy(1), new MaxTicketPolicy(1))
        );

        assertThat(atMostOneVipTicket.validate(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE))).isTrue();
        assertThat(atMostOneVipTicket.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE))).isTrue();
        assertThat(atMostOneVipTicket.validate(contextWithTickets(VIP_ZONE, VIP_ZONE, REGULAR_ZONE))).isFalse();

        assertDoesNotThrow(() -> atMostOneVipTicket.require(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE)));
        assertDoesNotThrow(() -> atMostOneVipTicket.require(contextWithTickets(VIP_ZONE, REGULAR_ZONE)));
        assertThatThrownBy(() -> atMostOneVipTicket.require(contextWithTickets(VIP_ZONE, VIP_ZONE, REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Purchase policy violation for zones")
                .hasMessageContaining("Cannot Purchase more than 1 tickets");
    }

    @Test
    void staticZoneBuildersRejectInvalidListArguments_UAT48_PP09() {
        assertThatThrownBy(() -> PolicyBuilder.PolicyRestrictToZones(Set.of(VIP_ZONE), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Policies cannot be null or empty");

        assertThatThrownBy(() -> PolicyBuilder.PolicyRequireFromZones(Set.of(), List.of(new MinTicketPolicy(1))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ZoneSpecificPolicy must contain at least one affected zone");
    }

    @Test
    void builderPropagatesInvalidBasicPolicyArguments_UAT48() {
        assertThatThrownBy(() -> PolicyBuilder.start().minAge(-1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Minimum age must not be negative");

        assertThatThrownBy(() -> PolicyBuilder.start().afterDate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Chosen date cannot be null");
    }

}
