package com.eventsystem.domain.policy.composite;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PurchaseContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
        return new IPolicy() {
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
        return new IPolicy() {
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
                .hasMessageContaining("inner failed");
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
                .hasMessageContaining("inner zone rule failed");
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

    private static IPolicy countedTicketsMustEqual(int expected) {
        return new IPolicy() {
            @Override
            public boolean validate(PurchaseContext context) {
                return context.zonesOfEachEventTicket().size() == expected;
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
        return new IPolicy() {
            @Override
            public boolean validate(PurchaseContext context) {
                return context.zonesOfEachEventTicket().size() <= max;
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
        return new IPolicy() {
            @Override
            public boolean validate(PurchaseContext context) {
                return context.zonesOfEachEventTicket().size() >= min;
            }

            @Override
            public void require(PurchaseContext context) {
                if (!validate(context)) {
                    throw new PurchasePolicyException("too few tickets");
                }
            }
        };
    }
}
