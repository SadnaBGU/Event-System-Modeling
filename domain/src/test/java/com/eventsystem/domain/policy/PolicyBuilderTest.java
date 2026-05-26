package com.eventsystem.domain.policy;

import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

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
}
