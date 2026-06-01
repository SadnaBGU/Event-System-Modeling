package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.basic.AfterDatePolicy;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinAgePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.basic.UntilDatePolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.composite.OrPolicy;
import com.eventsystem.domain.policy.composite.ZoneSpecificPolicy;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PolicyConflictDetectorTest {

    private static final ZoneId VIP_ZONE = new ZoneId("vip-zone");

    // PP-06 / PP-09 / TST-06:
    // Purchase policies support min/max ticket rules and must reject impossible internal combinations.
    @Test
    void detectInnerConflicts_whenMinTicketsGreaterThanMaxTickets_shouldFail() {
        IPolicy policy = new AndPolicy(List.of(
                new MinTicketPolicy(5),
                new MaxTicketPolicy(4)
        ));

        PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason())
                .contains("minimum tickets 5")
                .contains("maximum tickets 4");
    }

    // DP-06 / TST-09:
    // Discount conditions support time ranges and should reject impossible date windows.
    @Test
    void detectInnerConflicts_whenAfterDateIsNotBeforeUntilDate_shouldFail() {
        LocalDate later = LocalDate.of(2026, 6, 10);
        LocalDate earlier = LocalDate.of(2026, 6, 1);

        IPolicy policy = new AndPolicy(List.of(
                new AfterDatePolicy(later),
                new UntilDatePolicy(earlier)
        ));

        PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason())
                .contains("impossible date window")
                .contains(later.toString())
                .contains(earlier.toString());
    }

    // PP-07 / PP-08 / PP-09:
    // OR is valid if at least one branch is internally valid.
    @Test
    void detectInnerConflicts_orWithOneValidBranch_shouldPass() {
        IPolicy impossibleBranch = new AndPolicy(List.of(
                new MinTicketPolicy(5),
                new MaxTicketPolicy(4)
        ));

        IPolicy validBranch = new MinAgePolicy(18);

        IPolicy policy = new OrPolicy(List.of(impossibleBranch, validBranch));

        PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

        assertThat(result.isSuccess()).isTrue();
    }

    // PP-07 / PP-08 / PP-09:
    // OR is impossible only if all branches are impossible.
    @Test
    void detectInnerConflicts_orWithAllBranchesConflicting_shouldFail() {
        IPolicy ticketConflict = new AndPolicy(List.of(
                new MinTicketPolicy(5),
                new MaxTicketPolicy(4)
        ));

        IPolicy dateConflict = new AndPolicy(List.of(
                new AfterDatePolicy(LocalDate.of(2026, 6, 10)),
                new UntilDatePolicy(LocalDate.of(2026, 6, 1))
        ));

        IPolicy policy = new OrPolicy(List.of(ticketConflict, dateConflict));

        PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason())
                .contains("OR policy is impossible")
                .contains("branches conflict");
    }

    // PP-07 / PP-08:
    // ZONE_SPECIFIC_0_PASS can pass when no affected tickets exist, so its child conflict is not global.
    @Test
    void detectInnerConflicts_zoneSpecificZeroPassWithConflictingChild_shouldPass() {
        IPolicy conflictingChild = new AndPolicy(List.of(
                new MinTicketPolicy(5),
                new MaxTicketPolicy(4)
        ));

        IPolicy policy = new ZoneSpecificPolicy(
                Set.of(VIP_ZONE),
                conflictingChild,
                true
        );

        PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

        assertThat(result.isSuccess()).isTrue();
    }

    // PP-07 / PP-08 / PP-09:
    // ZONE_SPECIFIC_0_FAIL is strict enough that child conflicts should invalidate the wrapper.
    @Test
    void detectInnerConflicts_zoneSpecificZeroFailWithConflictingChild_shouldFail() {
        IPolicy conflictingChild = new AndPolicy(List.of(
                new MinTicketPolicy(5),
                new MaxTicketPolicy(4)
        ));

        IPolicy policy = new ZoneSpecificPolicy(
                Set.of(VIP_ZONE),
                conflictingChild,
                false
        );

        PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("minimum tickets 5");
    }

    // UC16 / UAT-48:
    // A discount requiring 5 tickets conflicts with a purchase policy allowing at most 4.
    @Test
    void detectConflictBetween_purchaseMaxAndDiscountMin_shouldFail_UAT48() {
        IPolicy purchasePolicy = new MaxTicketPolicy(4);
        IPolicy discountCondition = new MinTicketPolicy(5);

        PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                purchasePolicy,
                discountCondition
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason())
                .contains("requires more tickets")
                .contains("at most 4");
    }

    // UC16 / UAT-48:
    // OR conflict is rejected only when every branch requires more tickets than allowed.
    @Test
    void detectConflictBetween_orDiscountAllBranchesConflict_shouldFail_UAT48() {
        IPolicy purchasePolicy = new MaxTicketPolicy(4);
        IPolicy discountCondition = new OrPolicy(List.of(
                new MinTicketPolicy(5),
                new MinTicketPolicy(6)
        ));

        PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                purchasePolicy,
                discountCondition
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("requires more tickets");
    }

    // UC16 / UAT-48:
    // OR discount with one non-conflicting branch remains compatible.
    @Test
    void detectConflictBetween_orDiscountWithAlternativeBranch_shouldPass() {
        IPolicy purchasePolicy = new MaxTicketPolicy(4);
        IPolicy discountCondition = new OrPolicy(List.of(
                new MinTicketPolicy(5),
                new CodePolicy("VIP")
        ));

        PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                purchasePolicy,
                discountCondition
        );

        assertThat(result.isSuccess()).isTrue();
    }

    // PP-09:
    // The domain helper should throw when required as a guard.
    @Test
    void requireValidPolicy_whenInnerConflict_shouldThrowPolicyException() {
        IPolicy policy = new AndPolicy(List.of(
                new MinTicketPolicy(5),
                new MaxTicketPolicy(4)
        ));

        assertThatThrownBy(() -> PolicyConflictDetector.requireValidPolicy(policy))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Invalid purchase policy")
                .hasMessageContaining("minimum tickets 5");
    }
}