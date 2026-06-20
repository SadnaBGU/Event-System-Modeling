package com.eventsystem.domain.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.eventsystem.domain.policy.PolicyTestFixtures.REGULAR_ZONE;
import static com.eventsystem.domain.policy.PolicyTestFixtures.VIP_ZONE;
import static com.eventsystem.domain.policy.PolicyTestFixtures.contextWithTickets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PurchasePolicy aggregate-level tests.
 *
 * UAT mapping:
 * - UAT-44 / UC16: owner defines a max 4 tickets purchase policy.
 * - UAT-27 / UC9: checkout fails when order violates purchase policy.
 */
class PurchasePolicyTest {

        private static final CompanyId COMPANY_ID = new CompanyId("company-1");
        private static final EventId EVENT_ID = new EventId("event-1");
        private static final EventId OTHER_EVENT_ID = new EventId("event-2");
        private static final String POLICY_NAME = "Test purchase policy";

        @Test
        void allowAllPolicyAcceptsAnyPurchase() {
                PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(COMPANY_ID, "Allow all");

                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();

                assertThatCode(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE, VIP_ZONE)))
                                .doesNotThrowAnyException();
        }

        @Test
        void neverAllowedPolicyRejectsAnyPurchase() {
                PurchasePolicy policy = PurchasePolicy.newNeverAllowedPolicy(COMPANY_ID, "Never allow");

                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE))).isFalse();

                assertThatThrownBy(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE)))
                                .isInstanceOf(PurchasePolicyException.class)
                                .hasMessageContaining("Purchase policy");
        }

        @Test
        void purchasePolicyCreatedFromSinglePolicyValidatesPurchase_UAT44() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
        }

        @Test
        void purchasePolicyCreatedFromListRequiresAllRules() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                List.of(
                                                new MinTicketPolicy(2),
                                                new MaxTicketPolicy(4)));

                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isTrue();
                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE))).isFalse();
        }

        @Test
        void purchasePolicyThrowsWrappedViolationOnRequire_UAT27() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(1));

                assertThatThrownBy(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE, VIP_ZONE)))
                                .isInstanceOf(PurchasePolicyException.class)
                                .hasMessageContaining("Purchase policy")
                                .hasMessageContaining("Cannot purchase more than 1 tickets");
        }

        @Test
        void purchasePolicyEvaluateReturnsSuccessWhenPolicyPasses() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                PolicyValidationResult result = policy.evaluate(contextWithTickets(REGULAR_ZONE, VIP_ZONE));

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.failureReason()).isEmpty();
        }

        @Test
        void purchasePolicyEvaluateReturnsFailureWhenPolicyFails() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(1));

                PolicyValidationResult result = policy.evaluate(contextWithTickets(REGULAR_ZONE, VIP_ZONE));

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.failureReason()).isPresent();
        }

        @Test
        void purchasePolicyRejectsInvalidConstruction() {
                assertThatThrownBy(() -> new PurchasePolicy(
                                null,
                                COMPANY_ID,
                                POLICY_NAME,
                                PolicyScope.clearScope(),
                                new MaxTicketPolicy(1))).isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                null,
                                POLICY_NAME,
                                PolicyScope.clearScope(),
                                new MaxTicketPolicy(1))).isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                null,
                                PolicyScope.clearScope(),
                                new MaxTicketPolicy(1))).isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                POLICY_NAME,
                                null,
                                new MaxTicketPolicy(1))).isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                POLICY_NAME,
                                PolicyScope.clearScope(),
                                (IPolicy) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void purchasePolicyRejectsInvalidPolicyListConstruction() {
                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                POLICY_NAME,
                                PolicyScope.clearScope(),
                                (List<IPolicy>) null)).isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                POLICY_NAME,
                                PolicyScope.clearScope(),
                                List.of())).isInstanceOf(PolicyException.class);

                assertThatThrownBy(() -> new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                POLICY_NAME,
                                PolicyScope.clearScope(),
                                Arrays.asList(new MaxTicketPolicy(1), null)))
                                .isInstanceOf(PolicyException.class);
        }

        @Test
        void purchasePolicyRequireDoesNotThrowWhenAllRulesPass_UAT44() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                List.of(
                                                new MinTicketPolicy(2),
                                                new MaxTicketPolicy(4)));

                assertThatCode(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE, VIP_ZONE)))
                                .doesNotThrowAnyException();
        }

        @Test
        void purchasePolicyListConstructorDefensivelyCopiesPolicies() {
                java.util.ArrayList<IPolicy> policies = new java.util.ArrayList<>();
                policies.add(new MaxTicketPolicy(1));

                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                policies);

                policies.clear();

                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isFalse();
        }

        @Test
        void purchasePolicyStoresIdentityAndOwnerMetadata() {
                PurchasePolicyId policyId = PurchasePolicyId.random();
                PolicyScope scope = PolicyScope.clearScope();

                PurchasePolicy policy = new PurchasePolicy(
                                policyId,
                                COMPANY_ID,
                                POLICY_NAME,
                                scope,
                                new MaxTicketPolicy(4));

                assertThat(policy.id()).isEqualTo(policyId);
                assertThat(policy.companyId()).isEqualTo(COMPANY_ID);
                assertThat(policy.policyName()).isEqualTo(POLICY_NAME);
        }

        @Test
        void purchasePolicyCanRenamePolicy() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                policy.setNameTo("Updated name");

                assertThat(policy.policyName()).isEqualTo("Updated name");
        }

        @Test
        void purchasePolicyRejectsNullNameUpdate() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                assertThatThrownBy(() -> policy.setNameTo(null))
                                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void newPurchasePolicyStartsInactiveBecauseScopeIsClear() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                assertThat(policy.isActive()).isFalse();
                assertThat(policy.isActiveForEvent(EVENT_ID)).isFalse();
        }

        @Test
        void purchasePolicyCanBecomeCompanyWide() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                policy.setCompanyWide();

                assertThat(policy.isActive()).isTrue();
                assertThat(policy.isActiveForEvent(EVENT_ID)).isTrue();
                assertThat(policy.isActiveForEvent(OTHER_EVENT_ID)).isTrue();
        }

        @Test
        void purchasePolicyCanDeactivateCompanyWide() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                policy.setCompanyWide();
                policy.deactivateCompanyWide();

                assertThat(policy.isActive()).isFalse();
                assertThat(policy.isActiveForEvent(EVENT_ID)).isFalse();
        }

        @Test
        void purchasePolicyCanActivateAndDeactivateSpecificEvent() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                policy.activateForEvent(EVENT_ID);

                assertThat(policy.isActive()).isTrue();
                assertThat(policy.isActiveForEvent(EVENT_ID)).isTrue();
                assertThat(policy.isActiveForEvent(OTHER_EVENT_ID)).isFalse();

                policy.deactivateForEvent(EVENT_ID);

                assertThat(policy.isActiveForEvent(EVENT_ID)).isFalse();
                assertThat(policy.isActive()).isFalse();
        }

        @Test
        void purchasePolicyRejectsNullEventActivationChanges() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                assertThatThrownBy(() -> policy.activateForEvent(null))
                                .isInstanceOf(NullPointerException.class);

                assertThatThrownBy(() -> policy.deactivateForEvent(null))
                                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void purchasePolicyCanSetScopeDirectly() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                POLICY_NAME,
                                new MaxTicketPolicy(4));

                PolicyScope newScope = new PolicyScope(false, Set.of(EVENT_ID));
                policy.setScopeTo(newScope);

                assertThat(policy.isActive()).isTrue();
                assertThat(policy.isActiveForEvent(EVENT_ID)).isTrue();
                assertThat(policy.isActiveForEvent(OTHER_EVENT_ID)).isFalse();
        }

        @Test
        void assertInvalidPolicyValidationResultThrows() {
                assertThrows(IllegalArgumentException.class, () -> {
                        new PolicyValidationResult(false, null);
                });
                assertThrows(IllegalArgumentException.class, () -> {
                        new PolicyValidationResult(true, "NOT NULL");
                });
        }

        @Test
        void assertInvalidPolicyScopeThrows() {
                assertThrows(Exception.class, () -> {
                        new PolicyScope(false, Set.of((EventId) null));
                });
        }

        @Test
        void newNeverAllowedPolicyRejectsPurchaseAfterActivation() {
                PurchasePolicy policy = PurchasePolicy.newNeverAllowedPolicy(COMPANY_ID, "Closed sale");
                policy.activateForEvent(EVENT_ID);

                assertThat(policy.evaluate(contextWithTickets(REGULAR_ZONE)).isSuccess()).isFalse();

                assertThatThrownBy(() -> policy.requirePurchasePolicy(contextWithTickets(REGULAR_ZONE)))
                                .isInstanceOf(com.eventsystem.domain.domainexceptions.PurchasePolicyException.class)
                                .hasMessageContaining("Purchase policy violation");
        }

        @Test
        void evaluateWhenContextHasNoTickets_shouldFailBeforeInnerPolicy() {
                PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(COMPANY_ID, "Allow all");
                policy.activateForEvent(EVENT_ID);

                PolicyValidationResult result = policy.evaluate(contextWithTickets());

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("no tickets");
        }

        // PP-01 / PP-02:
        // emptyScope factory should create inactive policy with readable evaluation.
        @Test
        void emptyScopeFactory_shouldCreateInactivePolicyButStillEvaluateRule() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(
                                COMPANY_ID,
                                "Max 1",
                                new MaxTicketPolicy(1));

                assertThat(policy.isActive()).isFalse();
                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE))).isTrue();
                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isFalse();
        }

        // PP-06 / UAT-44:
        @Test
        void newMaxTicketPolicy_shouldCreateMaxTicketPolicy() {
                PurchasePolicy policy = PurchasePolicy.newMaxTicketPolicy(
                                COMPANY_ID,
                                "Max 1",
                                1);

                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE))).isTrue();
                assertThat(policy.isPurchaseAllowedInContext(contextWithTickets(REGULAR_ZONE, VIP_ZONE))).isFalse();
        }

        // PP-02 / UC9:
        @Test
        void evaluate_whenNoTickets_shouldReturnFailureBeforeInnerPolicy() {
                PurchasePolicy policy = PurchasePolicy.newAllowAllPolicy(COMPANY_ID, "Allow all");

                PolicyValidationResult result = policy.evaluate(contextWithTickets());

                assertThat(result.isSuccess()).isFalse();
                assertThat(result.reason()).contains("no tickets");
        }

        // =========================================================================================
        // ADDED TESTS TO ACHIEVE 100% COVERAGE ON PurchasePolicy.java AND PurchasePolicyId.java
        // =========================================================================================

        @Test
        void jpaProtectedConstructor_existsAndCreatesEmptyObject() throws Exception {
                // Testing the protected no-arg constructor required by JPA
                java.lang.reflect.Constructor<PurchasePolicy> constructor = PurchasePolicy.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                PurchasePolicy policy = constructor.newInstance();
                
                assertThat(policy.id()).isNull();
                assertThat(policy.companyId()).isNull();
        }

        @Test
        void secondaryConstructor_createsRandomId() {
                // Testing the constructor that generates a new ID internally
                IPolicy inner = new MaxTicketPolicy(1);
                PurchasePolicy policy = new PurchasePolicy(COMPANY_ID, "Test Secondary Constructor", PolicyScope.clearScope(), inner);
                
                assertThat(policy.id()).isNotNull();
                assertThat(policy.policyName()).isEqualTo("Test Secondary Constructor");
        }

        @Test
        void policyGetter_returnsInnerPolicy() {
                IPolicy inner = new MaxTicketPolicy(5);
                PurchasePolicy policy = PurchasePolicy.emptyScope(COMPANY_ID, "Getter Test", inner);
                
                assertThat(policy.policy()).isEqualTo(inner);
        }

        @Test
        void isSingleEventPolicy_returnsTrueOnlyWhenExactlyOneEvent() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(COMPANY_ID, "Event Count Test", new MaxTicketPolicy(1));
                
                // 0 events -> false
                assertThat(policy.isSingleEventPolicy()).isFalse(); 

                // 1 event -> true
                policy.activateForEvent(EVENT_ID);
                assertThat(policy.isSingleEventPolicy()).isTrue(); 

                // 2 events -> false
                policy.activateForEvent(OTHER_EVENT_ID);
                assertThat(policy.isSingleEventPolicy()).isFalse(); 

                // Company wide -> false
                policy.setCompanyWide();
                assertThat(policy.isSingleEventPolicy()).isFalse(); 
        }

        @Test
        void isSpecificFor_returnsTrueOnlyWhenExactlyThatEvent() {
                PurchasePolicy policy = PurchasePolicy.emptyScope(COMPANY_ID, "Specific Event Test", new MaxTicketPolicy(1));
                
                assertThat(policy.isSpecificFor(EVENT_ID)).isFalse();

                policy.activateForEvent(EVENT_ID);
                assertThat(policy.isSpecificFor(EVENT_ID)).isTrue();
                assertThat(policy.isSpecificFor(OTHER_EVENT_ID)).isFalse();

                // Add another event - no longer specific to ONE event
                policy.activateForEvent(OTHER_EVENT_ID);
                assertThat(policy.isSpecificFor(EVENT_ID)).isFalse(); 

                // Company wide
                policy.setCompanyWide();
                assertThat(policy.isSpecificFor(EVENT_ID)).isFalse();
        }

        @Test
        void purchasePolicyId_rejectsNullAndBlank() {
                assertThatThrownBy(() -> new PurchasePolicyId(null))
                        .isInstanceOf(NullPointerException.class)
                        .hasMessageContaining("value must not be null");
                        
                assertThatThrownBy(() -> new PurchasePolicyId(""))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("value must not be blank");
                        
                assertThatThrownBy(() -> new PurchasePolicyId("   "))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("value must not be blank");
        }

        @Test
        void purchasePolicyId_random_and_toString_behaveCorrectly() {
                PurchasePolicyId id = PurchasePolicyId.random();
                assertThat(id.value()).isNotNull().isNotBlank();
                assertThat(id.toString()).isEqualTo(id.value());
        }
}