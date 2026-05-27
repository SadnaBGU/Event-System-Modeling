package com.eventsystem.domain.policy;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.eventsystem.domain.policy.PolicyTestFixtures.REGULAR_ZONE;
import static com.eventsystem.domain.policy.PolicyTestFixtures.VIP_ZONE;
import static com.eventsystem.domain.policy.PolicyTestFixtures.contextWithTickets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PurchasePolicy aggregate-level tests.
 *
 * UAT mapping:
 * - UAT-44 / UC16: owner defines a max 4 tickets purchase policy.
 * - UAT-27 / UC9: checkout fails when order violates purchase policy.
 */
class PurchasePolicyTest {

    @Test
    void allowAllPolicyAcceptsAnyPurchase() {
        PurchasePolicy policy = PurchasePolicy.AllowAll();

        assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThatCode(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE, VIP_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void purchasePolicyCreatedFromSinglePolicyValidatesPurchase_UAT44() {
        PurchasePolicy policy = new PurchasePolicy(new MaxTicketPolicy(4));

        assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
    }

    @Test
    void purchasePolicyCreatedFromListRequiresAllRules() {
        PurchasePolicy policy = new PurchasePolicy(List.of(
                new MinTicketPolicy(2),
                new MaxTicketPolicy(4)
        ));

        assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE))).isFalse();
    }

    @Test
    void purchasePolicyThrowsWrappedViolationOnRequire_UAT27() {
        PurchasePolicy policy = new PurchasePolicy(new MaxTicketPolicy(1));

        assertThatThrownBy(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE, VIP_ZONE)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("Purchase Policy")
                .hasMessageContaining("Cannot Purchase more than 1 tickets");
    }

    @Test
    void purchasePolicyRejectsInvalidConstruction() {
        assertThatThrownBy(() -> new PurchasePolicy((IPolicy) null))
                .isInstanceOf(PurchasePolicyException.class);
        assertThatThrownBy(() -> new PurchasePolicy((List<IPolicy>) null))
                .isInstanceOf(PurchasePolicyException.class);
        assertThatThrownBy(() -> new PurchasePolicy(List.of()))
                .isInstanceOf(PurchasePolicyException.class);
        assertThatThrownBy(() -> new PurchasePolicy(Arrays.asList(new MaxTicketPolicy(1), null)))
                .isInstanceOf(PurchasePolicyException.class)
                .hasMessageContaining("null policies");
    }

    @Test
    void purchasePolicyRequireDoesNotThrowWhenAllRulesPass_UAT44() {
        PurchasePolicy policy = new PurchasePolicy(List.of(
                new MinTicketPolicy(2),
                new MaxTicketPolicy(4)
        ));

        assertThatCode(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE, VIP_ZONE)))
                .doesNotThrowAnyException();
    }

    @Test
    void purchasePolicyListConstructorDefensivelyCopiesPolicies() {
        java.util.ArrayList<IPolicy> policies = new java.util.ArrayList<>();
        policies.add(new MaxTicketPolicy(1));

        PurchasePolicy policy = new PurchasePolicy(policies);

        policies.clear();

        assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isFalse();
    }
}
