package com.eventsystem.domain.policy.composite;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.basic.IBasicPolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinAgePolicy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eventsystem.domain.policy.PolicyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for composite policies.
 *
 * UAT mapping:
 * - UAT-44 / UC16: composed purchase policy can combine multiple commercial restrictions.
 * - UAT-45 / UC16: composed visible discount conditions can be combined.
 * - UAT-48 / UC16: contradictory policies can be detected by evaluating a context against both sides.
 * - UAT-27 / UC9: checkout rejects orders that violate composed purchase policy restrictions.
 */
class CompositePolicyTest {

    private static IPolicy passingPolicy() {
        return new IBasicPolicy() {

            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                return validate(context)
                    ? PolicyValidationResult.success()
                    : PolicyValidationResult.failure("Fail");
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return true;
            }

            @Override
            public void require(PurchaseContext context) {
                // pass
            }
        };
    }

    private static IPolicy failingPolicy(String message) {
        return new IBasicPolicy() {

            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                return validate(context)
                    ? PolicyValidationResult.success()
                    : PolicyValidationResult.failure("Fail");
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return false;
            }

            @Override
            public void require(PurchaseContext context) {
                throw new PurchasePolicyException(message);
            }
        };
    }

    @Test
    void andPolicy_validatesOnlyWhenAllInnerPoliciesPass_UAT44() {
        IPolicy policy = new AndPolicy(List.of(passingPolicy(), passingPolicy()));

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isTrue();
    }

    @Test
    void andPolicy_rejectsWhenAnyInnerPolicyFails_UAT27() {
        IPolicy policy = new AndPolicy(List.of(passingPolicy(), failingPolicy("inner failed")));

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Fail");
    }

    @Test
    void andPolicy_rejectsNullEmptyOrNullElementLists() {
        assertThatThrownBy(() -> new AndPolicy(null))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new AndPolicy(List.of()))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new AndPolicy(java.util.Arrays.asList(passingPolicy(), null)))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("null policies");
    }

    @Test
    void orPolicy_validatesWhenAnyInnerPolicyPasses() {
        IPolicy policy = new OrPolicy(List.of(failingPolicy("first failed"), passingPolicy()));

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThatCode(() -> policy.require(contextWithTickets(REGULAR_ZONE))).doesNotThrowAnyException();
    }

    @Test
    void orPolicy_rejectsWhenAllInnerPoliciesFail() {
        IPolicy policy = new OrPolicy(List.of(failingPolicy("first failed"), failingPolicy("second failed")));

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isFalse();
        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class);
    }

    @Test
    void orPolicy_rejectsNullEmptyOrNullElementLists() {
        assertThatThrownBy(() -> new OrPolicy(null))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new OrPolicy(List.of()))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new OrPolicy(java.util.Arrays.asList(passingPolicy(), null)))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("null policies");
    }

    @Test
    void zoneSpecificPolicy_withNoAffectedTicketsPassTrue_ignoresIrrelevantZones() {
        IPolicy policy = new ZoneSpecificPolicy(
                Set.of(VIP_ZONE),
                failingPolicy("should not be evaluated when no VIP tickets are selected"),
                true
        );

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE))).isTrue();
        assertThatCode(() -> policy.require(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void zoneSpecificPolicy_withNoAffectedTicketsPassFalse_requiresAtLeastOneAffectedZoneTicket_UAT45() {
        IPolicy policy = new ZoneSpecificPolicy(
                Set.of(VIP_ZONE),
                passingPolicy(),
                false
        );

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE))).isFalse();
        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE, BALCONY_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("No tickets purchased for zones");
    }

    @Test
    void zoneSpecificPolicy_filtersContextBeforeApplyingInnerPolicy() {
        IPolicy atLeastTwoVipTickets = new ZoneSpecificPolicy(
                Set.of(VIP_ZONE),
                countedTicketsMustEqual(2),
                false
        );

        assertThat(atLeastTwoVipTickets.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(atLeastTwoVipTickets.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE, BALCONY_ZONE))).isFalse();
    }

    @Test
    void zoneSpecificPolicy_wrapsInnerPolicyFailureWithZoneMessage_UAT27() {
        IPolicy policy = new ZoneSpecificPolicy(
                Set.of(VIP_ZONE),
                failingPolicy("inner zone rule failed"),
                true
        );

        assertThatThrownBy(() -> policy.require(contextWithTickets(VIP_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Purchase policy violation for zones")
                .hasMessageContaining("Fail");
    }

    @Test
    void zoneSpecificPolicy_rejectsInvalidConstructorArguments() {
        assertThatThrownBy(() -> new ZoneSpecificPolicy(null, passingPolicy(), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ZoneSpecificPolicy(Set.of(), passingPolicy(), true))
                .isInstanceOf(PolicyException.class);
        assertThatThrownBy(() -> new ZoneSpecificPolicy(Set.of(VIP_ZONE), (IPolicy) null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void contradictoryMinAndMaxPoliciesCanBeDetectedByEvaluation_UAT48() {
        IPolicy maxFour = countedTicketsAtMost(4);
        IPolicy discountRequiresFive = countedTicketsAtLeast(5);
        PurchaseContext fiveTickets = contextWithTickets(VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE, VIP_ZONE);

        assertThat(maxFour.validate(fiveTickets)).isFalse();
        assertThat(discountRequiresFive.validate(fiveTickets)).isTrue();
    }


    @Test
    void andPolicyRequireStopsAtFirstFailure() {
        java.util.concurrent.atomic.AtomicBoolean secondEvaluated = new java.util.concurrent.atomic.AtomicBoolean(false);

        IPolicy first = failingPolicy("first failed");
        IPolicy second = new IBasicPolicy() {
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                return validate(context)
                    ? PolicyValidationResult.success()
                    : PolicyValidationResult.failure("Fail");
            }

            @Override
            public boolean validate(PurchaseContext context) {
                secondEvaluated.set(true);
                return true;
            }

            @Override
            public void require(PurchaseContext context) {
                secondEvaluated.set(true);
            }
        };

        IPolicy policy = new AndPolicy(List.of(first, second));

        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class);

        assertThat(secondEvaluated).isFalse();
    }

    @Test
    void zoneSpecificPolicy_passModeDoesNotEvaluateInnerPolicyWhenNoAffectedTickets() {
        IPolicy explodingPolicy = new IBasicPolicy() {
            
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                throw new AssertionError("inner policy should not be evaluated");
            }

            @Override
            public boolean validate(PurchaseContext context) {
                throw new AssertionError("inner policy should not be evaluated");
            }

            @Override
            public void require(PurchaseContext context) {
                throw new AssertionError("inner policy should not be evaluated");
            }
        };

        IPolicy policy = new ZoneSpecificPolicy(Set.of(VIP_ZONE), explodingPolicy, true);

        assertThat(policy.validate(contextWithTickets(REGULAR_ZONE))).isTrue();
        assertThatCode(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void zoneSpecificPolicyRejectsNullZoneElement() {
        java.util.Set<com.eventsystem.domain.zone.ZoneId> zones = new java.util.HashSet<>();
        zones.add(VIP_ZONE);
        zones.add(null);

        assertThatThrownBy(() -> new ZoneSpecificPolicy(zones, passingPolicy(), true))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("null zones");
    }

    private static IPolicy countedTicketsMustEqual(int expected) {
        return new IBasicPolicy() {
                        
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                // not relevant
                return validate(context)
                 ? PolicyValidationResult.success()
                 : PolicyValidationResult.failure("expected exactly " + expected + " tickets"); 
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return context.ticketCount() == expected;
            }

            @Override
            public void require(PurchaseContext context) {
                if (!validate(context)) {
                    throw new PurchasePolicyException("expected exactly " + expected + " tickets");
                }
            }
        };
    }

    private static IPolicy countedTicketsAtMost(int max) {
        return new IBasicPolicy() {

                        
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                // not relevant
                return validate(context)
                 ? PolicyValidationResult.success()
                 : PolicyValidationResult.failure("too many tickets"); 
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return context.ticketCount() <= max;
            }

            @Override
            public void require(PurchaseContext context) {
                if (!validate(context)) {
                    throw new PurchasePolicyException("too many tickets");
                }
            }
        };
    }

    private static IPolicy countedTicketsAtLeast(int min) {
        return new IBasicPolicy() {

            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                // not relevant
                return validate(context)
                 ? PolicyValidationResult.success()
                 : PolicyValidationResult.failure("too few tickets"); 
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return context.ticketCount() >= min;
            }

            @Override
            public void require(PurchaseContext context) {
                if (!validate(context)) {
                    throw new PurchasePolicyException("too few tickets");
                }
            }
        };
    }


    @Test
    void compositePoliciesExposeImmutableChildrenAndCompositeMarker_UAT44() {
        IPolicy first = passingPolicy();
        IPolicy second = passingPolicy();
        AndPolicy andPolicy = new AndPolicy(List.of(first, second));
        OrPolicy orPolicy = new OrPolicy(List.of(first, second));

        assertThat(andPolicy.isComposite()).isTrue();
        assertThat(orPolicy.isComposite()).isTrue();
        assertThat(andPolicy.isValidPolicy()).isTrue();
        assertThat(orPolicy.isValidPolicy()).isTrue();
        assertThat(andPolicy.children()).containsExactly(first, second);
        assertThat(orPolicy.children()).containsExactly(first, second);

        assertThatThrownBy(() -> andPolicy.children().add(passingPolicy()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> orPolicy.children().add(passingPolicy()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void compositePolicyValidityReturnsFalseForNullEmptyNullChildAndInvalidChild_UAT48() {
        assertThat(customCompositeReturning(null).isValidPolicy()).isFalse();
        assertThat(customCompositeReturning(List.of()).isValidPolicy()).isFalse();

        List<IPolicy> policiesWithNull = new ArrayList<>();
        policiesWithNull.add(passingPolicy());
        policiesWithNull.add(null);
        assertThat(customCompositeReturning(policiesWithNull).isValidPolicy()).isFalse();

        IPolicy invalidChild = new IBasicPolicy() {
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                return PolicyValidationResult.success();
            }
            @Override
            public boolean validate(PurchaseContext context) {
                return true;
            }

            @Override
            public void require(PurchaseContext context) {
                // pass
            }

            @Override
            public boolean isValidPolicy() {
                return false;
            }
        };

        assertThat(customCompositeReturning(List.of(passingPolicy(), invalidChild)).isValidPolicy()).isFalse();
    }

    @Test
    void andPolicyRequireCallsAllChildrenWhenAllPass_UAT44() {
        AtomicInteger evalCalls = new AtomicInteger(0);
        IPolicy first = trackingPolicy(true, evalCalls, "first failed");
        IPolicy second = trackingPolicy(true, evalCalls, "second failed");

        AndPolicy policy = new AndPolicy(List.of(first, second));

        assertThatCode(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .doesNotThrowAnyException();

        assertThat(evalCalls).hasValue(2);
    }

    @Test
    void orPolicyEvaluateStopsWhenOneChildValid_UAT44() {
        AtomicInteger evalCalls = new AtomicInteger(0);
        IPolicy invalid = trackingPolicy(false, evalCalls, "first failed");
        IPolicy valid = trackingPolicy(true, evalCalls, "valid");

        OrPolicy policy = new OrPolicy(List.of(invalid, valid));

        assertThatCode(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .doesNotThrowAnyException();

        assertThat(evalCalls).hasValue(2);
    }

    @Test
    void orPolicyRequireCallsChildrenWhenAllValidateFalse_UAT27() {
        AtomicInteger evalCalls = new AtomicInteger(0);
        IPolicy first = trackingPolicy(false, evalCalls, "first failed");
        IPolicy second = trackingPolicy(false, evalCalls, "second failed");

        OrPolicy policy = new OrPolicy(List.of(first, second));

        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("At least one purchase condition");

        assertThat(evalCalls).hasValue(2);
    }

  @Test
    void orPolicyRequireReturnsCombinedFailureWhenAllValidateFalse_UAT27() {
        AtomicInteger evalCalls = new AtomicInteger(0);
        IPolicy first = trackingPolicy(false, evalCalls, "first failed");
        IPolicy second = trackingPolicy(false, evalCalls, "second failed");

        OrPolicy policy = new OrPolicy(List.of(first, second));

        assertThatThrownBy(() -> policy.require(contextWithTickets(REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("At least one purchase condition")
                .hasMessageContaining("first failed")
                .hasMessageContaining("second failed");

        assertThat(evalCalls).hasValue(2);
    }

    @Test
    void zoneSpecificPolicyListConstructorBuildsAndPolicyOverFilteredTickets_UAT45() {
        IPolicy exactlyTwoTickets = new IBasicPolicy() {

            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                // not relevant
                return validate(context)
                 ? PolicyValidationResult.success()
                 : PolicyValidationResult.failure("expected exactly two affected-zone tickets"); 
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return context.ticketCount() == 2;
            }

            @Override
            public void require(PurchaseContext context) {
                if (!validate(context)) {
                    throw new PurchasePolicyException("expected exactly two affected-zone tickets");
                }
            }
        };

        IPolicy onlyVip = new ZoneSpecificPolicy(
                java.util.Set.of(VIP_ZONE),
                List.of(exactlyTwoTickets),
                false
        );

        assertThat(onlyVip.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(onlyVip.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE))).isFalse();
        assertThatCode(() -> onlyVip.require(contextWithTickets(VIP_ZONE, REGULAR_ZONE, VIP_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void zoneSpecificPolicyRequirePassesFilteredContextToInnerPolicy_UAT45() {
        AtomicInteger seenTicketCount = new AtomicInteger(-1);

        IPolicy inner = new IBasicPolicy() {
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                seenTicketCount.set(context.ticketCount());

                return context.ticketCount() == 2
                        ? PolicyValidationResult.success()
                        : PolicyValidationResult.failure("wrong filtered count");
            }
        };

        ZoneSpecificPolicy policy = new ZoneSpecificPolicy(
                java.util.Set.of(VIP_ZONE),
                inner,
                false
        );

        assertThatCode(() -> policy.require(contextWithTickets(VIP_ZONE, REGULAR_ZONE, VIP_ZONE)))
                .doesNotThrowAnyException();

        assertThat(seenTicketCount).hasValue(2);
    }

    @Test
    void deeplyNestedCompositePolicyEvaluatesThroughAndOrZoneLayers_UAT44_UAT45() {
        IPolicy nested = new AndPolicy(List.of(
                new OrPolicy(List.of(failingPolicy("irrelevant failure"), passingPolicy())),
                new ZoneSpecificPolicy(java.util.Set.of(VIP_ZONE), ticketCountMustBe(2), false)
        ));

        assertThat(nested.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(nested.validate(contextWithTickets(VIP_ZONE, REGULAR_ZONE))).isFalse();

        assertThatCode(() -> nested.require(contextWithTickets(VIP_ZONE, REGULAR_ZONE, VIP_ZONE)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> nested.require(contextWithTickets(VIP_ZONE, REGULAR_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Purchase policy violation for zones")
                .hasMessageContaining("expected exactly 2 tickets");
    }

    @Test
    void orPolicyEvaluateReturnsCombinedReason_whenAllChildrenFail() {
        OrPolicy policy = new OrPolicy(List.of(
                new MaxTicketPolicy(1),
                new MinAgePolicy(100)
        ));

        PolicyValidationResult result = policy.evaluate(contextWithTickets(REGULAR_ZONE,REGULAR_ZONE));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).contains("At least one purchase condition");
    }

    private static IPolicy trackingPolicy(
            boolean evaluateResult,
            AtomicInteger evalCalls,
            String message
    ) {
        return new IBasicPolicy() {
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                evalCalls.incrementAndGet();

                return evaluateResult
                        ? PolicyValidationResult.success()
                        : PolicyValidationResult.failure(message);
            }
        };
    }

    private static IPolicy ticketCountMustBe(int expected) {
        return new IBasicPolicy() {
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                return validate(context)
                 ? PolicyValidationResult.success()
                 : PolicyValidationResult.failure("expected exactly " + expected + " tickets"); 
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return context.ticketCount() == expected;
            }

            @Override
            public void require(PurchaseContext context) {
                if (!validate(context)) {
                    throw new PurchasePolicyException("expected exactly " + expected + " tickets");
                }
            }
        };
    }

    private static ICompositePolicy customCompositeReturning(List<IPolicy> children) {
        return new ICompositePolicy() {
            @Override
            public PolicyValidationResult evaluate(PurchaseContext context) {
                // not relevant
                return null; 
            }

            @Override
            public List<IPolicy> children() {
                return children;
            }

            @Override
            public boolean validate(PurchaseContext context) {
                return false;
            }

            @Override
            public void require(PurchaseContext context) {
                // not relevant for isValidPolicy tests
            }
        };
    }
}
