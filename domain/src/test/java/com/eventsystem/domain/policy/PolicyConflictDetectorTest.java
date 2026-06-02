package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.basic.AfterDatePolicy;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinAgePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.basic.UntilDatePolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.composite.ICompositePolicy;
import com.eventsystem.domain.policy.composite.OrPolicy;
import com.eventsystem.domain.policy.composite.ZoneSpecificPolicy;
import com.eventsystem.domain.zone.ZoneId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PolicyConflictDetectorTest {

        private static final ZoneId VIP_ZONE = new ZoneId("vip-zone");

        // PP-06 / PP-09 / TST-06:
        // Purchase policies support min/max ticket rules and must reject impossible
        // internal combinations.
        @Test
        void detectInnerConflicts_whenMinTicketsGreaterThanMaxTickets_shouldFail() {
                IPolicy policy = new AndPolicy(List.of(
                                new MinTicketPolicy(5),
                                new MaxTicketPolicy(4)));

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("minimum tickets 5")
                                .contains("maximum tickets 4");
        }

        // DP-06 / TST-09:
        // Discount conditions support time ranges and should reject impossible date
        // windows.
        @Test
        void detectInnerConflicts_whenAfterDateIsNotBeforeUntilDate_shouldFail() {
                LocalDate later = LocalDate.of(2026, 6, 10);
                LocalDate earlier = LocalDate.of(2026, 6, 1);

                IPolicy policy = new AndPolicy(List.of(
                                new AfterDatePolicy(later),
                                new UntilDatePolicy(earlier)));

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
                                new MaxTicketPolicy(4)));

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
                                new MaxTicketPolicy(4)));

                IPolicy dateConflict = new AndPolicy(List.of(
                                new AfterDatePolicy(LocalDate.of(2026, 6, 10)),
                                new UntilDatePolicy(LocalDate.of(2026, 6, 1))));

                IPolicy policy = new OrPolicy(List.of(ticketConflict, dateConflict));

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("OR policy is impossible")
                                .contains("branches conflict");
        }

        // PP-07 / PP-08:
        // ZONE_SPECIFIC_0_PASS can pass when no affected tickets exist, so its child
        // conflict is not global.
        @Test
        void detectInnerConflicts_zoneSpecificZeroPassWithConflictingChild_shouldPass() {
                IPolicy conflictingChild = new AndPolicy(List.of(
                                new MinTicketPolicy(5),
                                new MaxTicketPolicy(4)));

                IPolicy policy = new ZoneSpecificPolicy(
                                Set.of(VIP_ZONE),
                                conflictingChild,
                                true);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isTrue();
        }

        // PP-07 / PP-08 / PP-09:
        // ZONE_SPECIFIC_0_FAIL is strict enough that child conflicts should invalidate
        // the wrapper.
        @Test
        void detectInnerConflicts_zoneSpecificZeroFailWithConflictingChild_shouldFail() {
                IPolicy conflictingChild = new AndPolicy(List.of(
                                new MinTicketPolicy(5),
                                new MaxTicketPolicy(4)));

                IPolicy policy = new ZoneSpecificPolicy(
                                Set.of(VIP_ZONE),
                                conflictingChild,
                                false);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("minimum tickets 5");
        }

        // UC16 / UAT-48:
        // A discount requiring 5 tickets conflicts with a purchase policy allowing at
        // most 4.
        @Test
        void detectConflictBetween_purchaseMaxAndDiscountMin_shouldFail_UAT48() {
                IPolicy purchasePolicy = new MaxTicketPolicy(4);
                IPolicy discountCondition = new MinTicketPolicy(5);

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                                purchasePolicy,
                                discountCondition);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("requires more tickets")
                                .contains("at most 4");
        }

        // UC16 / UAT-48:
        // OR conflict is rejected only when every branch requires more tickets than
        // allowed.
        @Test
        void detectConflictBetween_orDiscountAllBranchesConflict_shouldFail_UAT48() {
                IPolicy purchasePolicy = new MaxTicketPolicy(4);
                IPolicy discountCondition = new OrPolicy(List.of(
                                new MinTicketPolicy(5),
                                new MinTicketPolicy(6)));

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                                purchasePolicy,
                                discountCondition);

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
                                new CodePolicy("VIP")));

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(
                                purchasePolicy,
                                discountCondition);

                assertThat(result.isSuccess()).isTrue();
        }

        // PP-09:
        // The domain helper should throw when required as a guard.
        @Test
        void requireValidPolicy_whenInnerConflict_shouldThrowPolicyException() {
                IPolicy policy = new AndPolicy(List.of(
                                new MinTicketPolicy(5),
                                new MaxTicketPolicy(4)));

                assertThatThrownBy(() -> PolicyConflictDetector.requireValidPolicy(policy))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Invalid purchase policy")
                                .hasMessageContaining("minimum tickets 5");
        }

        @Test
        void detectInnerConflicts_whenPolicyIsNull_shouldThrowNullPointerException() {
                assertThatThrownBy(() -> PolicyConflictDetector.detectInnerConflicts(null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("policy must not be null");
        }

        @Test
        void detectConflictBetween_whenLimitingPolicyHasInnerConflict_shouldFail() {
                IPolicy limitingPolicy = new AndPolicy(List.of(
                                new MinTicketPolicy(5),
                                new MaxTicketPolicy(4)));

                IPolicy requiringPolicy = new MinTicketPolicy(2);

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Conflict inside limiting policy");
        }

        @Test
        void detectConflictBetween_whenRequiringPolicyHasInnerConflict_shouldFail() {
                IPolicy limitingPolicy = new MaxTicketPolicy(10);

                IPolicy requiringPolicy = new AndPolicy(List.of(
                                new AfterDatePolicy(LocalDate.of(2026, 6, 10)),
                                new UntilDatePolicy(LocalDate.of(2026, 6, 1))));

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Conflict inside requiring policy");
        }

        @Test
        void requireValidPolicy_whenPolicyListIsEmpty_shouldThrowPolicyException() {
                assertThatThrownBy(() -> PolicyConflictDetector.requireValidPolicy(List.of()))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("cannot be empty");
        }

        @Test
        void requireValidPolicy_whenPolicyListContainsNull_shouldThrowPolicyException() {
                assertThatThrownBy(
                                () -> PolicyConflictDetector.requireValidPolicy(List.of(new MaxTicketPolicy(4), null)))
                                .isInstanceOf(NullPointerException.class);

        }

        private static final class FakePolicy implements IPolicy {
                private final PolicyType type;
                private final boolean validPolicy;

                private FakePolicy(PolicyType type) {
                        this(type, true);
                }

                private FakePolicy(PolicyType type, boolean validPolicy) {
                        this.type = type;
                        this.validPolicy = validPolicy;
                }

                @Override
                public PolicyType type() {
                        return type;
                }

                @Override
                public PolicyValidationResult evaluate(PurchaseContext context) {
                        return PolicyValidationResult.success();
                }

                @Override
                public boolean isValidPolicy() {
                        return validPolicy;
                }

                @Override
                public boolean isComposite() {
                        return false;
                }
        }

        private static final class FakeCompositePolicy implements ICompositePolicy {
                private final PolicyType type;
                private final List<IPolicy> children;

                private FakeCompositePolicy(PolicyType type, List<IPolicy> children) {
                        this.type = type;
                        this.children = children;
                }

                @Override
                public PolicyType type() {
                        return type;
                }

                @Override
                public PolicyValidationResult evaluate(PurchaseContext context) {
                        return PolicyValidationResult.success();
                }

                @Override
                public List<IPolicy> children() {
                        return children;
                }
        }

        @Test
        void privateConstructor_shouldThrowUnsupportedOperationException() throws Exception {
                Constructor<PolicyConflictDetector> constructor = PolicyConflictDetector.class.getDeclaredConstructor();

                constructor.setAccessible(true);

                assertThatThrownBy(constructor::newInstance)
                                .isInstanceOf(InvocationTargetException.class)
                                .hasCauseInstanceOf(UnsupportedOperationException.class)
                                .hasRootCauseMessage("PolicyConflictDetector is a utility class");
        }

        @Test
        void detectConflictBetween_whenLimitingPolicyIsNull_shouldThrowNullPointerException() {
                assertThatThrownBy(() -> PolicyConflictDetector.detectConflictBetween(null, new MinTicketPolicy(1)))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("limitingPolicy must not be null");
        }

        @Test
        void detectConflictBetween_whenRequiringPolicyIsNull_shouldThrowNullPointerException() {
                assertThatThrownBy(() -> PolicyConflictDetector.detectConflictBetween(new MaxTicketPolicy(4), null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("requiringPolicy must not be null");
        }

        @Test
        void detectInnerConflicts_whenAndTypeDoesNotExposeChildren_shouldFail() {
                IPolicy fakeAnd = new FakePolicy(PolicyType.AND);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(fakeAnd);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("AND policy must expose children");
        }

        @Test
        void detectInnerConflicts_whenOrTypeDoesNotExposeChildren_shouldFail() {
                IPolicy fakeOr = new FakePolicy(PolicyType.OR);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(fakeOr);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("OR policy must expose children");
        }

        @Test
        void detectInnerConflicts_whenZoneFailTypeDoesNotExposeChildren_shouldFail() {
                IPolicy fakeZone = new FakePolicy(PolicyType.ZONE_SPECIFIC_0_FAIL);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(fakeZone);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Zone-specific policy must expose children");
        }

        @Test
        void detectInnerConflicts_whenAndChildrenAreNull_shouldFail() {
                IPolicy policy = new FakeCompositePolicy(PolicyType.AND, null);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("AND policy must contain at least one child policy");
        }

        @Test
        void detectInnerConflicts_whenAndChildrenAreEmpty_shouldFail() {
                IPolicy policy = new FakeCompositePolicy(PolicyType.AND, List.of());

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("AND policy must contain at least one child policy");
        }

        @Test
        void detectInnerConflicts_whenOrChildrenAreNull_shouldFail() {
                IPolicy policy = new FakeCompositePolicy(PolicyType.OR, null);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("OR policy must contain at least one child policy");
        }

        @Test
        void detectInnerConflicts_whenOrChildrenAreEmpty_shouldFail() {
                IPolicy policy = new FakeCompositePolicy(PolicyType.OR, List.of());

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("OR policy must contain at least one child policy");
        }

        @Test
        void detectInnerConflicts_whenZoneFailChildrenAreNull_shouldFail() {
                IPolicy policy = new FakeCompositePolicy(PolicyType.ZONE_SPECIFIC_0_FAIL, null);

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Zone-specific policy must contain a child policy");
        }

        @Test
        void detectInnerConflicts_whenZoneFailChildrenAreEmpty_shouldFail() {
                IPolicy policy = new FakeCompositePolicy(PolicyType.ZONE_SPECIFIC_0_FAIL, List.of());

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Zone-specific policy must contain a child policy");
        }

        @Test
        void detectConflictBetween_andRequiringPolicyRequiresMoreTickets_shouldFail() {
                IPolicy limitingPolicy = new MaxTicketPolicy(4);

                IPolicy requiringPolicy = new AndPolicy(List.of(
                                new CodePolicy("VIP"),
                                new MinTicketPolicy(5)));

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("requires more tickets")
                                .contains("at most 4");
        }

        @Test
        void detectConflictBetween_andRequiringPolicyDoesNotRequireMoreTickets_shouldPass() {
                IPolicy limitingPolicy = new MaxTicketPolicy(4);

                IPolicy requiringPolicy = new AndPolicy(List.of(
                                new CodePolicy("VIP"),
                                new MinTicketPolicy(4)));

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void detectConflictBetween_zoneFailRequiringPolicyRequiresMoreTickets_shouldFail() {
                IPolicy limitingPolicy = new MaxTicketPolicy(4);

                IPolicy requiringPolicy = new ZoneSpecificPolicy(
                                Set.of(VIP_ZONE),
                                new MinTicketPolicy(5),
                                false);

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("requires more tickets")
                                .contains("at most 4");
        }

        @Test
        void detectConflictBetween_zonePassRequiringPolicyWithHighMinTickets_shouldPass() {
                IPolicy limitingPolicy = new MaxTicketPolicy(4);

                IPolicy requiringPolicy = new ZoneSpecificPolicy(
                                Set.of(VIP_ZONE),
                                new MinTicketPolicy(5),
                                true);

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void detectConflictBetween_orRequiringPolicyEmptyChildren_shouldPassBecauseNoBranchRequiresMoreTickets() {
                IPolicy limitingPolicy = new MaxTicketPolicy(4);
                IPolicy requiringPolicy = new FakeCompositePolicy(PolicyType.OR, List.of());

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("Conflict inside requiring policy");
        }

        @Test
        void detectConflictBetween_limitingAndPolicyExtractsStrongestMaxTickets_shouldFail() {
                IPolicy limitingPolicy = new AndPolicy(List.of(
                                new MaxTicketPolicy(10),
                                new MaxTicketPolicy(4)));

                IPolicy requiringPolicy = new MinTicketPolicy(5);

                PolicyValidationResult result = PolicyConflictDetector.detectConflictBetween(limitingPolicy,
                                requiringPolicy);

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason())
                                .contains("requires more tickets")
                                .contains("at most 4");
        }

        @Test
        void detectInnerConflicts_andWithTwoMinTicketPoliciesKeepsStrongestMinAndPasses() {
                IPolicy policy = new AndPolicy(List.of(
                                new MinTicketPolicy(2),
                                new MinTicketPolicy(5)));

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void detectInnerConflicts_andWithTwoMaxTicketPoliciesKeepsStrongestMaxAndPasses() {
                IPolicy policy = new AndPolicy(List.of(
                                new MaxTicketPolicy(10),
                                new MaxTicketPolicy(4)));

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void detectInnerConflicts_andWithTwoAfterDatePoliciesKeepsLaterAfterDateAndPasses() {
                IPolicy policy = new AndPolicy(List.of(
                                new AfterDatePolicy(LocalDate.of(2026, 6, 1)),
                                new AfterDatePolicy(LocalDate.of(2026, 6, 10))));

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void detectInnerConflicts_andWithTwoUntilDatePoliciesKeepsEarlierUntilDateAndPasses() {
                IPolicy policy = new AndPolicy(List.of(
                                new UntilDatePolicy(LocalDate.of(2026, 6, 20)),
                                new UntilDatePolicy(LocalDate.of(2026, 6, 10))));

                PolicyValidationResult result = PolicyConflictDetector.detectInnerConflicts(policy);

                assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void requireValidPolicy_whenPolicyIsNull_shouldThrowNullPointerException() {
                assertThatThrownBy(() -> PolicyConflictDetector.requireValidPolicy((IPolicy) null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("eventIds must not be null");
        }

        @Test
        void requireValidPolicy_whenPolicyReportsInvalid_shouldThrowPolicyException() {
                IPolicy invalidPolicy = new FakePolicy(PolicyType.ALWAYS_TRUE, false);

                assertThatThrownBy(() -> PolicyConflictDetector.requireValidPolicy(invalidPolicy))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Invalid policy for purchase policy");
        }

        @Test
        void requireValidPolicy_whenPolicyListIsNull_shouldThrowNullPointerException() {
                assertThatThrownBy(() -> PolicyConflictDetector.requireValidPolicy((List<IPolicy>) null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessageContaining("eventIds must not be null");
        }
}