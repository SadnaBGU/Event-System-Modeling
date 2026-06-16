package com.eventsystem.application.policy;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyManagementServiceTest {

        @Mock
        private IPurchasePolicyRepository purchasePolicyRepository;

        @Mock
        private IDiscountPolicyRepository discountPolicyRepository;

        @Mock
        private ICompanyPermissionServicePort permissionChecker;

        @Mock
        private IEventManagementPort eventOwnershipChecker;

        private PolicyManagementService service;

        private static final MemberId ACTOR_ID = new MemberId("actor-1");
        private static final CompanyId COMPANY_ID = new CompanyId("company-1");
        private static final CompanyId OTHER_COMPANY_ID = new CompanyId("company-2");

        private static final EventId EVENT_ID = new EventId("event-1");
        private static final EventId OTHER_EVENT_ID = new EventId("event-2");

        @BeforeEach
        void setUp() {
                service = new PolicyManagementService(
                                purchasePolicyRepository,
                                discountPolicyRepository,
                                permissionChecker,
                                eventOwnershipChecker,
                                new PolicyCommandAssembler());

                lenient().when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
                lenient().when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
                lenient().when(eventOwnershipChecker.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);
                lenient().when(eventOwnershipChecker.isEventByCompany(OTHER_EVENT_ID, COMPANY_ID)).thenReturn(true);

                lenient().when(discountPolicyRepository.findActiveByCompanyId(any())).thenReturn(List.of());
                lenient().when(purchasePolicyRepository.findActiveByCompanyId(any())).thenReturn(List.of());
        }

        private PolicyScopeCommand eventScope(EventId eventId) {
                return new PolicyScopeCommand(false, Set.of(eventId.value()));
        }

        // private PolicyScopeCommand companyWideScope() {
        // return new PolicyScopeCommand(true, Set.of());
        // }

        private PolicyRuleCommand valueRule(String type, int value) {
                return new PolicyRuleCommand(type, value, null, null, null, null);
        }

        private DiscountCommand discountCommand(String name, int percent, PolicyRuleCommand rule) {
                return new DiscountCommand(name, BigDecimal.valueOf(percent), rule);
        }

        private DiscountPolicy activeDiscountPolicy(EventId eventId, Discount discount) {
                DiscountPolicy policy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(eventId));
                policy.addDiscount(discount);
                policy.activate();
                return policy;
        }

        private PurchasePolicy activePurchasePolicy(EventId eventId, int maxTickets) {
                PurchasePolicy policy = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Max " + maxTickets + " tickets",
                                PolicyScope.forSingleEvent(eventId),
                                new MaxTicketPolicy(maxTickets));
                return policy;
        }

        // UC16 / UAT-44:
        // Owner defines a purchase policy limiting max tickets per buyer.
        @Test
        void createPurchasePolicy_shouldSavePolicy_UAT44() {
                PurchasePolicyCommand command = new PurchasePolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Max 4 tickets",
                                eventScope(EVENT_ID),
                                valueRule("MAX_TICKETS", 4),
                                true);

                PurchasePolicyId id = service.createPurchasePolicy(command);

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.policyName()).isEqualTo("Max 4 tickets");
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(saved.isActive()).isTrue();
        }

        // UC16 / UAT-48:
        // New purchase policy conflicts with an already-active overlapping discount.
        @Test
        void createPurchasePolicy_whenItBlocksActiveDiscount_shouldReject_UAT48() {
                DiscountPolicy existingDiscount = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5)));

                when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(existingDiscount));

                PurchasePolicyCommand command = new PurchasePolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Max 4 tickets",
                                eventScope(EVENT_ID),
                                valueRule("MAX_TICKETS", 4),
                                true);

                assertThatThrownBy(() -> service.createPurchasePolicy(command))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Policy conflict")
                                .hasMessageContaining("Buy five discount");

                verify(purchasePolicyRepository, never()).save(any());
        }

        // UC16 / UAT-48:
        // Same conflict should not matter when scopes do not overlap.
        @Test
        void createPurchasePolicy_whenDiscountScopeDoesNotOverlap_shouldSave() {
                DiscountPolicy otherEventDiscount = activeDiscountPolicy(
                                OTHER_EVENT_ID,
                                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5)));

                when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(otherEventDiscount));

                PurchasePolicyCommand command = new PurchasePolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Max 4 tickets",
                                eventScope(EVENT_ID),
                                valueRule("MAX_TICKETS", 4),
                                true);

                assertThatCode(() -> service.createPurchasePolicy(command))
                                .doesNotThrowAnyException();

                verify(purchasePolicyRepository).save(any(PurchasePolicy.class));
        }

        // UC16 / UAT-45:
        // Owner creates an active visible/conditional discount.
        @Test
        void createDiscountPolicy_whenActiveAndCompatible_shouldSave_UAT45() {
                PurchasePolicy existingPurchasePolicy = activePurchasePolicy(EVENT_ID, 4);

                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(existingPurchasePolicy));

                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Buy two discount policy",
                                eventScope(EVENT_ID),
                                List.of(discountCommand("Buy two discount", 20, valueRule("MIN_TICKETS", 2))),
                                false,
                                true);

                DiscountPolicyId id = service.createDiscountPolicy(command);

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.isActive()).isTrue();
                assertThat(saved.discounts()).hasSize(1);
        }

        // UC16 / UAT-48:
        // Active discount requiring 5 tickets conflicts with active purchase max 4.
        @Test
        void createDiscountPolicy_whenActiveAndConflictsWithPurchasePolicy_shouldReject_UAT48() {
                PurchasePolicy existingPurchasePolicy = activePurchasePolicy(EVENT_ID, 4);

                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(existingPurchasePolicy));

                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Conflicting discount policy",
                                eventScope(EVENT_ID),
                                List.of(discountCommand("Buy five discount", 20, valueRule("MIN_TICKETS", 5))),
                                false,
                                true);

                assertThatThrownBy(() -> service.createDiscountPolicy(command))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Policy conflict")
                                .hasMessageContaining("Buy five discount");

                verify(discountPolicyRepository, never()).save(any());
        }

        // UC16:
        // Inactive discount drafts may be saved even if they would conflict when
        // active.
        // Compatibility is enforced on activation, not draft creation.
        @Test
        void createDiscountPolicy_whenInactiveAndConflicting_shouldSaveAsDraft() {
                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Inactive conflicting draft",
                                eventScope(EVENT_ID),
                                List.of(discountCommand("Buy five discount", 20, valueRule("MIN_TICKETS", 5))),
                                false,
                                false);

                assertThatCode(() -> service.createDiscountPolicy(command))
                                .doesNotThrowAnyException();

                verify(purchasePolicyRepository, never()).findActiveByCompanyId(any());
                verify(discountPolicyRepository).save(any(DiscountPolicy.class));
        }

        // UC16 / UAT-48:
        // Activating a previously inactive conflicting discount should be rejected.
        @Test
        void activateDiscountPolicy_whenConflictsWithActivePurchasePolicy_shouldReject_UAT48() {
                PurchasePolicy existingPurchasePolicy = activePurchasePolicy(EVENT_ID, 4);

                DiscountPolicy discountPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(EVENT_ID));
                discountPolicy.addDiscount(
                                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5)));

                when(discountPolicyRepository.findById(discountPolicy.id()))
                                .thenReturn(Optional.of(discountPolicy));
                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(existingPurchasePolicy));

                assertThatThrownBy(() -> service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, discountPolicy.id()))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Policy conflict")
                                .hasMessageContaining("Buy five discount");

                verify(discountPolicyRepository, never()).save(any());
        }

        // UC16:
        // Adding a discount to an already active policy should re-check compatibility.
        @Test
        void addDiscountToPolicy_whenActivePolicyWouldConflict_shouldReject() {
                PurchasePolicy existingPurchasePolicy = activePurchasePolicy(EVENT_ID, 4);

                DiscountPolicy discountPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(EVENT_ID));
                discountPolicy.addDiscount(
                                new Discount("Safe discount", BigDecimal.valueOf(10), new MinTicketPolicy(2)));
                discountPolicy.activate();

                when(discountPolicyRepository.findById(discountPolicy.id()))
                                .thenReturn(Optional.of(discountPolicy));
                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(existingPurchasePolicy));

                DiscountCommand command = discountCommand(
                                "Buy five discount",
                                20,
                                valueRule("MIN_TICKETS", 5));

                assertThatThrownBy(
                                () -> service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, discountPolicy.id(), command))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Policy conflict")
                                .hasMessageContaining("Buy five discount");

                verify(discountPolicyRepository, never()).save(any());
        }

        // PRD-03 / UC16:
        // Expanding purchase scope to company-wide should re-check active discounts.
        @Test
        void setPurchasePolicyCompanyWide_whenOverlappingActiveDiscountConflicts_shouldReject() {
                PurchasePolicy purchasePolicy = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Max 4 tickets",
                                PolicyScope.forSingleEvent(OTHER_EVENT_ID),
                                new MaxTicketPolicy(4));

                DiscountPolicy eventDiscount = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5)));

                when(purchasePolicyRepository.findById(purchasePolicy.id()))
                                .thenReturn(Optional.of(purchasePolicy));
                when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(eventDiscount));

                assertThatThrownBy(
                                () -> service.setPurchasePolicyCompanyWide(ACTOR_ID, COMPANY_ID, purchasePolicy.id()))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Policy conflict");

                verify(purchasePolicyRepository, never()).save(any());
        }

        // PRD-03 / UC16:
        // Narrowing a policy scope should be allowed and does not need compatibility
        // checks.
        @Test
        void removeEventFromPurchasePolicy_shouldSaveWithoutCheckingDiscountConflicts() {
                PurchasePolicy purchasePolicy = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Max 4 tickets",
                                PolicyScope.forSingleEvent(EVENT_ID),
                                new MaxTicketPolicy(4));

                when(purchasePolicyRepository.findById(purchasePolicy.id()))
                                .thenReturn(Optional.of(purchasePolicy));

                service.removeEventFromPurchasePolicy(
                                ACTOR_ID,
                                COMPANY_ID,
                                purchasePolicy.id(),
                                EVENT_ID);

                verify(discountPolicyRepository, never()).findActiveByCompanyId(any());
                verify(purchasePolicyRepository).save(purchasePolicy);
                assertThat(purchasePolicy.isActive()).isFalse();
        }

        // PRD-03 / TST-13:
        // Policy management must enforce authorization, not only UI hiding.
        @Test
        void createPurchasePolicy_whenActorCannotManagePurchasePolicies_shouldThrowSecurityException() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID))
                                .thenReturn(false);

                PurchasePolicyCommand command = new PurchasePolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Max 4 tickets",
                                eventScope(EVENT_ID),
                                valueRule("MAX_TICKETS", 4),
                                true);

                assertThatThrownBy(() -> service.createPurchasePolicy(command))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("purchase policies");

                verify(purchasePolicyRepository, never()).save(any());
        }

        // PRD-03 / TST-13:
        // Policy management must verify that scoped events belong to the company.
        @Test
        void createDiscountPolicy_whenEventDoesNotBelongToCompany_shouldThrowSecurityException() {
                when(eventOwnershipChecker.isEventByCompany(EVENT_ID, COMPANY_ID))
                                .thenReturn(false);

                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Discount policy",
                                eventScope(EVENT_ID),
                                List.of(discountCommand("Buy two discount", 20, valueRule("MIN_TICKETS", 2))),
                                false,
                                true);

                assertThatThrownBy(() -> service.createDiscountPolicy(command))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("event does not belong");

                verify(discountPolicyRepository, never()).save(any());
        }

        @Test
        void savePurchasePolicy_whenPolicyBelongsToOtherCompany_shouldThrowSecurityException() {
                PurchasePolicy foreignPolicy = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                OTHER_COMPANY_ID,
                                "Foreign policy",
                                PolicyScope.companyWideScope(),
                                new MaxTicketPolicy(4));

                assertThatThrownBy(() -> service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, foreignPolicy))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("another company");

                verify(purchasePolicyRepository, never()).save(any());
        }

        @Test
        void doesHaveActivePurchasePolicy_shouldDelegateToRepositories() {
                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of());
                when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID))
                                .thenReturn(List.of(activePurchasePolicy(EVENT_ID, 4)));

                boolean result = service.doesHaveActivePurchasePolicy(EVENT_ID, COMPANY_ID);

                assertThat(result).isTrue();
        }

        @Test
        void modifyPurchasePolicyScope_whenCompatible_shouldSaveUpdatedScope() {
                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                PolicyScope newScope = PolicyScope.forSingleEvent(OTHER_EVENT_ID);

                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.modifyPurchasePolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope);

                assertThat(policy.scope()).isEqualTo(newScope);
                verify(purchasePolicyRepository).save(policy);
        }

        @Test
        void setPurchasePolicyNotCompanyWide_shouldDeactivateCompanyWideAndSave() {
                PurchasePolicy policy = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Company wide max 4",
                                PolicyScope.companyWideScope(),
                                new MaxTicketPolicy(4));

                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.setPurchasePolicyNotCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

                assertThat(policy.scope().isCompanyWide()).isFalse();
                verify(purchasePolicyRepository).save(policy);
        }

        @Test
        void addEventToPurchasePolicy_whenEventBelongsToCompany_shouldActivateEventAndSave() {
                PurchasePolicy policy = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Max 4 tickets",
                                PolicyScope.clearScope(),
                                new MaxTicketPolicy(4));

                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.addEventToPurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

                assertThat(policy.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(policy.isActiveForEvent(EVENT_ID)).isTrue();
                verify(purchasePolicyRepository).save(policy);
        }

        @Test
        void renamePurchasePolicy_shouldUpdateNameAndSave() {
                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.renamePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id(), "New name");

                assertThat(policy.policyName()).isEqualTo("New name");
                verify(purchasePolicyRepository).save(policy);
        }

        @Test
        void deletePurchasePolicy_shouldDeleteExistingCompanyPolicy() {
                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.deletePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id());

                verify(purchasePolicyRepository).deleteById(policy.id());
        }

        @Test
        void clearAllPurchasePoliciesOfCompany_shouldDeleteEveryCompanyPolicy() {
                PurchasePolicy first = activePurchasePolicy(EVENT_ID, 4);
                PurchasePolicy second = activePurchasePolicy(OTHER_EVENT_ID, 6);

                when(purchasePolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(first, second));

                service.clearAllPurchasePoliciesOfCompany(ACTOR_ID, COMPANY_ID);

                verify(purchasePolicyRepository).deleteById(first.id());
                verify(purchasePolicyRepository).deleteById(second.id());
        }

        @Test
        void deactivateAllCompanyPurchasePolicies_shouldClearOnlyActivePolicies() {
                PurchasePolicy active = activePurchasePolicy(EVENT_ID, 4);
                PurchasePolicy inactive = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Inactive",
                                PolicyScope.clearScope(),
                                new MaxTicketPolicy(4));

                when(purchasePolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(active, inactive));

                service.deactivateAllCompanyPurchasePolicies(ACTOR_ID, COMPANY_ID);

                assertThat(active.isActive()).isFalse();
                verify(purchasePolicyRepository).save(active);
                verify(purchasePolicyRepository, never()).save(inactive);
        }

        @Test
        void removeEventFromAllPurchasePolicyScopes_shouldSaveOnlyPoliciesContainingEvent() {
                PurchasePolicy first = activePurchasePolicy(EVENT_ID, 4);
                PurchasePolicy second = activePurchasePolicy(OTHER_EVENT_ID, 4);

                when(purchasePolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(first, second));

                service.removeEventFromAllPurchasePolicyScopes(ACTOR_ID, COMPANY_ID, EVENT_ID);

                assertThat(first.scope().eventIds()).doesNotContain(EVENT_ID);
                verify(purchasePolicyRepository).save(first);
                verify(purchasePolicyRepository, never()).save(second);
        }

        @Test
        void createNewAllowAllPurchasePolicy_shouldCreatePolicyForEvent() {
                service.createNewAllowAllPurchasePolicy(ACTOR_ID, COMPANY_ID, EVENT_ID);

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.policyName()).isEqualTo("AllowAll");
                assertThat(saved.isActiveForEvent(EVENT_ID)).isTrue();
        }

        @Test
        void setNotAllowedPurchasePolicy_shouldCreateNeverAllowPolicyForEvent() {
                service.setNotAllowedPurchasePolicy(ACTOR_ID, COMPANY_ID, EVENT_ID);

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.policyName()).isEqualTo("NotAllowed");
                assertThat(saved.isActiveForEvent(EVENT_ID)).isTrue();
        }

        @Test
        void deactivateDiscountPolicy_shouldDeactivateAndSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.deactivateDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

                assertThat(policy.isActive()).isFalse();
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void modifyDiscountPolicyScope_whenCompatible_shouldChangeScopeAndSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));
                PolicyScope newScope = PolicyScope.forSingleEvent(OTHER_EVENT_ID);

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.modifyDiscountPolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope);

                assertThat(policy.scope()).isEqualTo(newScope);
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void setDiscountPolicyCompanyWide_shouldSetCompanyWideAndSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.setDiscountPolicyCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

                assertThat(policy.scope().isCompanyWide()).isTrue();
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void setDiscountPolicyNotCompanyWide_shouldDeactivateCompanyWideAndSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));
                policy.setCompanyWide();

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.setDiscountPolicyNotCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

                assertThat(policy.scope().isCompanyWide()).isFalse();
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void addEventToDiscountPolicy_whenEventBelongsToCompany_shouldActivateEventAndSave() {
                DiscountPolicy policy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.clearScope());
                policy.addDiscount(new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.addEventToDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

                assertThat(policy.scope().eventIds()).containsExactly(EVENT_ID);
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void removeEventFromDiscountPolicy_shouldDeactivateEventAndSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.removeEventFromDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

                assertThat(policy.scope().eventIds()).doesNotContain(EVENT_ID);
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void deleteDiscountPolicy_shouldDeleteExistingCompanyPolicy() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.deleteDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

                verify(discountPolicyRepository).deleteById(policy.id());
        }

        @Test
        void deactivateAllCompanyDiscounts_shouldDeactivateOnlyActivePolicies() {
                DiscountPolicy active = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                DiscountPolicy inactive = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(OTHER_EVENT_ID));
                inactive.addDiscount(new Discount("Other", BigDecimal.valueOf(10), new MinTicketPolicy(1)));

                when(discountPolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(active, inactive));

                service.deactivateAllCompanyDiscounts(ACTOR_ID, COMPANY_ID);

                assertThat(active.isActive()).isFalse();
                verify(discountPolicyRepository).save(active);
                verify(discountPolicyRepository, never()).save(inactive);
        }

        @Test
        void clearAllDiscountsOfCompany_shouldDeleteEveryCompanyDiscountPolicy() {
                DiscountPolicy first = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));
                DiscountPolicy second = activeDiscountPolicy(
                                OTHER_EVENT_ID,
                                new Discount("Other", BigDecimal.valueOf(10), new MinTicketPolicy(1)));

                when(discountPolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(first, second));

                service.clearAllDiscountsOfCompany(ACTOR_ID, COMPANY_ID);

                verify(discountPolicyRepository).deleteById(first.id());
                verify(discountPolicyRepository).deleteById(second.id());
        }

        @Test
        void savePurchasePolicy_whenCompatible_shouldSave() {
                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);

                service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, policy);

                verify(purchasePolicyRepository).save(policy);
        }

        @Test
        void modifyPurchasePolicyScope_whenEventDoesNotBelongToCompany_shouldThrowSecurityException() {
                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                EventId foreignEvent = new EventId("foreign-event");

                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));
                when(eventOwnershipChecker.isEventByCompany(foreignEvent, COMPANY_ID)).thenReturn(false);

                assertThatThrownBy(() -> service.modifyPurchasePolicyScope(
                                ACTOR_ID,
                                COMPANY_ID,
                                policy.id(),
                                PolicyScope.forSingleEvent(foreignEvent)))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("event does not belong");

                verify(purchasePolicyRepository, never()).save(any());
        }

        @Test
        void modifyPurchasePolicyScope_whenPolicyNotFound_shouldThrowPolicyException() {
                PurchasePolicyId missingId = PurchasePolicyId.random();

                when(purchasePolicyRepository.findById(missingId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.modifyPurchasePolicyScope(
                                ACTOR_ID,
                                COMPANY_ID,
                                missingId,
                                PolicyScope.forSingleEvent(EVENT_ID)))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Purchase policy not found");

                verify(purchasePolicyRepository, never()).save(any());
        }

        @Test
        void clearEventsFromAllPurchasePolicies_shouldClearExplicitEventsAndSaveAll() {
                PurchasePolicy eventPolicy = activePurchasePolicy(EVENT_ID, 4);

                PurchasePolicy companyWideWithEvent = new PurchasePolicy(
                                PurchasePolicyId.random(),
                                COMPANY_ID,
                                "Company max",
                                new PolicyScope(true, Set.of(EVENT_ID)),
                                new MaxTicketPolicy(5));

                when(purchasePolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(eventPolicy, companyWideWithEvent));

                service.clearEventsFromAllPurchasePolicies(ACTOR_ID, COMPANY_ID);

                assertThat(eventPolicy.scope().eventIds()).isEmpty();
                assertThat(companyWideWithEvent.scope().eventIds()).isEmpty();
                assertThat(companyWideWithEvent.scope().isCompanyWide()).isTrue();

                verify(purchasePolicyRepository).save(eventPolicy);
                verify(purchasePolicyRepository).save(companyWideWithEvent);
        }

        @Test
        void doesHaveActivePurchasePolicy_whenCompanyHasActivePolicy_shouldReturnTrueWithoutEventLookup() {
                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(activePurchasePolicy(EVENT_ID, 4)));

                boolean result = service.doesHaveActivePurchasePolicy(EVENT_ID, COMPANY_ID);

                assertThat(result).isTrue();
                verify(purchasePolicyRepository, never()).findApplicableToEvent(any());
        }

        @Test
        void doesHaveActivePurchasePolicy_whenNoCompanyOrEventPolicies_shouldReturnFalse() {
                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of());
                when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of());

                boolean result = service.doesHaveActivePurchasePolicy(EVENT_ID, COMPANY_ID);

                assertThat(result).isFalse();
        }

        @Test
        void createDiscountPolicy_whenDiscountListIsNull_shouldThrowPolicyException() {
                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Bad discount",
                                eventScope(EVENT_ID),
                                null,
                                false,
                                true);

                assertThatThrownBy(() -> service.createDiscountPolicy(command))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("at least one discount");

                verify(discountPolicyRepository, never()).save(any());
        }

        @Test
        void createDiscountPolicy_whenDiscountListIsEmpty_shouldThrowPolicyException() {
                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Bad discount",
                                eventScope(EVENT_ID),
                                List.of(),
                                false,
                                true);

                assertThatThrownBy(() -> service.createDiscountPolicy(command))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("at least one discount");

                verify(discountPolicyRepository, never()).save(any());
        }

        @Test
        void saveDiscountPolicy_whenPolicyBelongsToOtherCompany_shouldThrowSecurityException() {
                DiscountPolicy foreignPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                OTHER_COMPANY_ID,
                                PolicyScope.companyWideScope());
                foreignPolicy.addDiscount(new Discount("Foreign", BigDecimal.TEN, new MinTicketPolicy(1)));

                assertThatThrownBy(() -> service.saveDiscountPolicy(ACTOR_ID, COMPANY_ID, foreignPolicy))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("another company");

                verify(discountPolicyRepository, never()).save(any());
        }

        @Test
        void saveDiscountPolicy_whenCompatible_shouldSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                service.saveDiscountPolicy(ACTOR_ID, COMPANY_ID, policy);

                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void activateDiscountPolicy_whenCompatible_shouldActivateAndSave() {
                DiscountPolicy policy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(EVENT_ID));
                policy.addDiscount(new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

                assertThat(policy.isActive()).isTrue();
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void activateDiscountPolicy_whenPolicyNotFound_shouldThrowPolicyException() {
                DiscountPolicyId missingId = DiscountPolicyId.random();

                when(discountPolicyRepository.findById(missingId)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, missingId))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Discount policy not found");

                verify(discountPolicyRepository, never()).save(any());
        }

        @Test
        void addDiscountToPolicy_withDomainDiscount_whenCompatible_shouldSave() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Existing", BigDecimal.TEN, new MinTicketPolicy(1)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                Discount added = new Discount("Added", BigDecimal.valueOf(15), new MinTicketPolicy(2));

                service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), added);

                assertThat(policy.discounts()).extracting(Discount::getDiscountName)
                                .contains("Existing", "Added");
                verify(discountPolicyRepository).save(policy);
        }

        @Test
        void removeEventFromAllDiscountScopes_shouldSaveOnlyPoliciesContainingEvent() {
                DiscountPolicy first = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Event discount", BigDecimal.TEN, new MinTicketPolicy(1)));

                DiscountPolicy second = activeDiscountPolicy(
                                OTHER_EVENT_ID,
                                new Discount("Other event discount", BigDecimal.TEN, new MinTicketPolicy(1)));

                when(discountPolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(first, second));

                service.removeEventFromAllDiscountScopes(ACTOR_ID, COMPANY_ID, EVENT_ID);

                assertThat(first.scope().eventIds()).doesNotContain(EVENT_ID);
                assertThat(second.scope().eventIds()).containsExactly(OTHER_EVENT_ID);

                verify(discountPolicyRepository).save(first);
                verify(discountPolicyRepository, never()).save(second);
        }

        @Test
        void clearEventsFromAllDiscounts_shouldClearExplicitEventsAndSaveAll() {
                DiscountPolicy eventPolicy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Event discount", BigDecimal.TEN, new MinTicketPolicy(1)));

                DiscountPolicy companyWideWithEvent = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                new PolicyScope(true, Set.of(EVENT_ID)));
                companyWideWithEvent
                                .addDiscount(new Discount("Company discount", BigDecimal.TEN, new MinTicketPolicy(1)));
                companyWideWithEvent.activate();

                when(discountPolicyRepository.findByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(eventPolicy, companyWideWithEvent));

                service.clearEventsFromAllDiscounts(ACTOR_ID, COMPANY_ID);

                assertThat(eventPolicy.scope().eventIds()).isEmpty();
                assertThat(companyWideWithEvent.scope().eventIds()).isEmpty();
                assertThat(companyWideWithEvent.scope().isCompanyWide()).isTrue();

                verify(discountPolicyRepository).save(eventPolicy);
                verify(discountPolicyRepository).save(companyWideWithEvent);
        }

        @Test
        void createDiscountPolicy_whenActorCannotManageDiscountPolicies_shouldThrowSecurityException() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID))
                                .thenReturn(false);

                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Discount policy",
                                eventScope(EVENT_ID),
                                List.of(discountCommand("Buy two discount", 20, valueRule("MIN_TICKETS", 2))),
                                false,
                                true);

                assertThatThrownBy(() -> service.createDiscountPolicy(command))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("discount policies");

                verify(discountPolicyRepository, never()).save(any());
        }

        @Test
        void setDiscountPolicyCompanyWide_whenOverlappingPurchasePolicyConflicts_shouldReject() {
                PurchasePolicy purchasePolicy = activePurchasePolicy(EVENT_ID, 4);

                DiscountPolicy discountPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(OTHER_EVENT_ID));
                discountPolicy.addDiscount(
                                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5)));
                discountPolicy.activate();

                when(discountPolicyRepository.findById(discountPolicy.id()))
                                .thenReturn(Optional.of(discountPolicy));
                when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                                .thenReturn(List.of(purchasePolicy));

                assertThatThrownBy(
                                () -> service.setDiscountPolicyCompanyWide(ACTOR_ID, COMPANY_ID, discountPolicy.id()))
                                .isInstanceOf(PolicyException.class)
                                .hasMessageContaining("Policy conflict");

                verify(discountPolicyRepository, never()).save(any());
        }

        // UC16 / UAT-45 / DP-08 / DP-14:
        // Owner creates discount policy through command; service should persist
        // visibility and endDate.
        @Test
        void createDiscountPolicy_whenCommandHasVisibilityAndEndDate_shouldSaveDiscountWithThoseFields_UAT45_DP08_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(7);

                DiscountCommand discountCommand = new DiscountCommand(
                                "Hidden early bird",
                                BigDecimal.valueOf(20),
                                valueRule("MIN_TICKETS", 2),
                                "HIDDEN",
                                endDate.toString());

                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Hidden timed discount policy",
                                eventScope(EVENT_ID),
                                List.of(discountCommand),
                                false,
                                true);

                DiscountPolicyId id = service.createDiscountPolicy(command);

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.discounts()).hasSize(1);

                Discount savedDiscount = saved.discounts().get(0);

                assertThat(savedDiscount.getDiscountName()).isEqualTo("Hidden early bird");
                assertThat(savedDiscount.isVisible()).isFalse();
                assertThat(savedDiscount.getEndDate()).isEqualTo(endDate);
                assertThat(savedDiscount.canExpire()).isTrue();
        }

        // UC16 / UAT-45 / DP-08 / DP-14:
        // Adding a discount through service command should preserve visibility and
        // endDate.
        @Test
        void addDiscountToPolicy_whenCommandHasVisibilityAndEndDate_shouldSaveAddedDiscount_UAT45_DP08_DP14() {
                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Existing", BigDecimal.TEN, new MinTicketPolicy(1)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                LocalDate endDate = LocalDate.now().plusDays(10);

                DiscountCommand command = new DiscountCommand(
                                "Hidden added discount",
                                BigDecimal.valueOf(15),
                                valueRule("MIN_TICKETS", 2),
                                "HIDDEN",
                                endDate.toString());

                service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), command);

                assertThat(policy.discounts())
                                .anySatisfy(discount -> {
                                        assertThat(discount.getDiscountName()).isEqualTo("Hidden added discount");
                                        assertThat(discount.isVisible()).isFalse();
                                        assertThat(discount.getEndDate()).isEqualTo(endDate);
                                });

                verify(discountPolicyRepository).save(policy);
        }

        // UC16 / DP-03 / DP-08 / DP-14:
        // Event-scoped helper should create a policy with hidden/visible setting and
        // endDate.
        @Test
        void createEventScopedDiscountPolicy_shouldSavePolicyWithHiddenDiscountAndEndDate_DP03_DP08_DP14() {
                LocalDate endDate = LocalDate.now().plusDays(5);

                when(eventOwnershipChecker.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);

                DiscountPolicyId id = service.createEventScopedDiscountPolicy(
                                ACTOR_ID,
                                EVENT_ID,
                                "Event hidden",
                                BigDecimal.valueOf(25),
                                AlwaysTruePolicy.INSTANCE,
                                false,
                                endDate,
                                true);

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(saved.isStackable()).isTrue();

                Discount discount = saved.discounts().get(0);
                assertThat(discount.getDiscountName()).isEqualTo("Event hidden");
                assertThat(discount.isVisible()).isFalse();
                assertThat(discount.getEndDate()).isEqualTo(endDate);
        }

        // DP-14 / UAT-45:
        // Management service should expose only event ids related to active visible
        // discounts.
        @Test
        void getEventIdsWithActiveVisibleDiscounts_shouldReturnScopedAndCompanyWideVisibleDiscountEvents_UAT45_DP14() {
                DiscountPolicy eventPolicy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount(
                                                "Event visible",
                                                BigDecimal.TEN,
                                                AlwaysTruePolicy.INSTANCE,
                                                true,
                                                LocalDate.now().plusDays(1)));

                DiscountPolicy companyPolicy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.companyWideScope());

                companyPolicy.addDiscount(new Discount(
                                "Company visible",
                                BigDecimal.TEN,
                                AlwaysTruePolicy.INSTANCE,
                                true,
                                LocalDate.now().plusDays(1)));
                companyPolicy.activate();

                when(discountPolicyRepository.findActiveWithVisibleDiscounts())
                                .thenReturn(List.of(eventPolicy, companyPolicy));
                when(eventOwnershipChecker.allEventsOfCompany(COMPANY_ID))
                                .thenReturn(List.of(EVENT_ID, OTHER_EVENT_ID));

                List<EventId> result = service.getEventIdsWithActiveVisibleDiscounts();

                assertThat(result).containsExactlyInAnyOrder(EVENT_ID, OTHER_EVENT_ID);
        }

        // PRD-03 / UC16:
        // Convenience helper creates company-wide purchase policy.
        @Test
        void createCompanyWidePurchasePolicy_shouldSaveCompanyWidePolicy() {
                PurchasePolicyId id = service.createCompanyWidePurchasePolicy(
                                ACTOR_ID,
                                COMPANY_ID,
                                "Max 4",
                                new MaxTicketPolicy(4));

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.policyName()).isEqualTo("Max 4");
                assertThat(saved.scope().isCompanyWide()).isTrue();
        }

        // PRD-03 / UC16:
        // Convenience helper derives company from event and creates event-scoped
        // policy.
        @Test
        void createEventScopedPurchasePolicy_shouldDeriveCompanyAndSaveEventScopedPolicy() {
                when(eventOwnershipChecker.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);

                PurchasePolicyId id = service.createEventScopedPurchasePolicy(
                                ACTOR_ID,
                                EVENT_ID,
                                "Max 4",
                                new MaxTicketPolicy(4));

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
        }

        // PP-05 / PRD-03 / UC16:
        // Convenience helper creates min-age purchase policy for event.
        @Test
        void createMinAgePurchasePolicyForEvent_shouldCreateEventScopedMinAgePolicy() {
                when(eventOwnershipChecker.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);

                service.createMinAgePurchasePolicyForEvent(
                                ACTOR_ID,
                                EVENT_ID,
                                18,
                                "18 plus");

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(saved.policyName()).isEqualTo("18 plus");
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
        }

        // PP-06 / UAT-44:
        // Convenience helper creates min/max ticket policy and rejects invalid ranges.
        @Test
        void createMinMaxTicketsPurchasePolicyForEvent_shouldCreatePolicyAndRejectInvalidRange_UAT44() {
                when(eventOwnershipChecker.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);

                service.createMinMaxTicketsPurchasePolicyForEvent(
                                ACTOR_ID,
                                EVENT_ID,
                                1,
                                4,
                                "1 to 4 tickets");

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(saved.policyName()).isEqualTo("1 to 4 tickets");
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);

                assertThatThrownBy(() -> service.createMinMaxTicketsPurchasePolicyForEvent(
                                ACTOR_ID,
                                EVENT_ID,
                                5,
                                4,
                                "Invalid"))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("maximum");
        }

        // DP-14 / UAT-45:
        // Convenience helper creates company-wide visible discount draft.
        @Test
        void createGeneralCompanyWideDiscount_shouldSaveCompanyWideVisibleDiscountDraft_UAT45() {
                DiscountPolicyId id = service.createGeneralCompanyWideDiscount(
                                ACTOR_ID,
                                COMPANY_ID,
                                "Early bird",
                                BigDecimal.valueOf(20),
                                LocalDate.now().plusDays(7),
                                false);

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.scope().isCompanyWide()).isTrue();
                assertThat(saved.isActive()).isFalse(); // current helper creates draft
                assertThat(saved.discounts()).hasSize(1);
                assertThat(saved.discounts().get(0).isVisible()).isTrue();
                assertThat(saved.discounts().get(0).getDiscountName()).isEqualTo("Early bird");
        }

        // DP-07 / DP-08 / UAT-46:
        // Convenience helper creates company-wide hidden coupon discount draft.
        @Test
        void createCouponCompanyWideDiscount_shouldSaveHiddenCouponDiscountDraft_UAT46() {
                DiscountPolicyId id = service.createCouponCompanyWideDiscount(
                                ACTOR_ID,
                                COMPANY_ID,
                                "Secret coupon",
                                BigDecimal.valueOf(15),
                                LocalDate.now().plusDays(7),
                                false,
                                "SAVE15");

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.scope().isCompanyWide()).isTrue();
                assertThat(saved.isActive()).isFalse(); // current helper creates draft
                assertThat(saved.discounts()).hasSize(1);
                assertThat(saved.discounts().get(0).isVisible()).isFalse();
        }

        // DP-10 / PRD-03:
        // Event-set discount helper should verify all events belong to same company.
        // This uses reflection because the helper is private.
        @Test
        void createNewDiscountPolicyForEvents_whenOneEventDoesNotBelongToCompany_shouldThrowSecurityException()
                        throws Exception {
                when(eventOwnershipChecker.companyOfEvent(any(EventId.class))).thenReturn(COMPANY_ID);

                when(eventOwnershipChecker.isEventByCompany(OTHER_EVENT_ID, COMPANY_ID)).thenReturn(false);

                var method = PolicyManagementService.class.getDeclaredMethod(
                                "createNewDiscountPolicyForEvents",
                                MemberId.class,
                                Set.class,
                                List.class,
                                boolean.class);
                method.setAccessible(true);

                assertThatThrownBy(() -> method.invoke(
                                service,
                                ACTOR_ID,
                                Set.of(EVENT_ID, OTHER_EVENT_ID),
                                List.of(Discount.GeneralDiscount("Visible", BigDecimal.TEN, null)),
                                false))
                                .hasCauseInstanceOf(SecurityException.class);

                verify(discountPolicyRepository, never()).save(any());
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Manager with event-management permission may create an event-scoped purchase
        // policy,
        // even without direct purchase-policy permission.
        @Test
        void createPurchasePolicy_eventScoped_whenActorCanManageEventsButNotPurchasePolicies_shouldSave() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                PurchasePolicyCommand command = new PurchasePolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Max 4 tickets",
                                eventScope(EVENT_ID),
                                valueRule("MAX_TICKETS", 4),
                                true);

                PurchasePolicyId id = service.createPurchasePolicy(command);

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.policyName()).isEqualTo("Max 4 tickets");
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(saved.isActiveForEvent(EVENT_ID)).isTrue();
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Manager with event-management permission may create an event-scoped discount
        // policy,
        // even without direct discount-policy permission.
        @Test
        void createDiscountPolicy_eventScoped_whenActorCanManageEventsButNotDiscountPolicies_shouldSave() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                DiscountPolicyCommand command = new DiscountPolicyCommand(
                                ACTOR_ID.value(),
                                COMPANY_ID.value(),
                                "Event discount policy",
                                eventScope(EVENT_ID),
                                List.of(discountCommand("Buy two discount", 20, valueRule("MIN_TICKETS", 2))),
                                false,
                                true);

                DiscountPolicyId id = service.createDiscountPolicy(command);

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(saved.isActive()).isTrue();
                assertThat(saved.discounts()).hasSize(1);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may use the direct event-scoped purchase helper.
        @Test
        void createEventScopedPurchasePolicy_whenActorCanManageEventsButNotPurchasePolicies_shouldSave() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);
                when(eventOwnershipChecker.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);

                PurchasePolicyId id = service.createEventScopedPurchasePolicy(
                                ACTOR_ID,
                                EVENT_ID,
                                "Event max tickets",
                                new MaxTicketPolicy(4));

                ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
                verify(purchasePolicyRepository).save(captor.capture());

                PurchasePolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.policyName()).isEqualTo("Event max tickets");
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(saved.isActiveForEvent(EVENT_ID)).isTrue();
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may use the direct event-scoped discount helper.
        @Test
        void createEventScopedDiscountPolicy_whenActorCanManageEventsButNotDiscountPolicies_shouldSave() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);
                when(eventOwnershipChecker.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);

                DiscountPolicyId id = service.createEventScopedDiscountPolicy(
                                ACTOR_ID,
                                EVENT_ID,
                                "Event discount",
                                BigDecimal.valueOf(20),
                                new MinTicketPolicy(2),
                                true,
                                LocalDate.now().plusDays(10),
                                false);

                ArgumentCaptor<DiscountPolicy> captor = ArgumentCaptor.forClass(DiscountPolicy.class);
                verify(discountPolicyRepository).save(captor.capture());

                DiscountPolicy saved = captor.getValue();

                assertThat(id).isEqualTo(saved.id());
                assertThat(saved.companyId()).isEqualTo(COMPANY_ID);
                assertThat(saved.scope().eventIds()).containsExactly(EVENT_ID);
                assertThat(saved.discounts()).hasSize(1);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may rename an event-scoped purchase policy.
        @Test
        void renamePurchasePolicy_eventScoped_whenActorCanManageEventsButNotPurchasePolicies_shouldSave() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.renamePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id(), "Updated event policy");

                assertThat(policy.policyName()).isEqualTo("Updated event policy");
                verify(purchasePolicyRepository).save(policy);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may delete an event-scoped purchase policy.
        @Test
        void deletePurchasePolicy_eventScoped_whenActorCanManageEventsButNotPurchasePolicies_shouldDelete() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.deletePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id());

                verify(purchasePolicyRepository).deleteById(policy.id());
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may activate/deactivate event-scoped discount policy.
        @Test
        void activateDiscountPolicy_eventScoped_whenActorCanManageEventsButNotDiscountPolicies_shouldSave() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                DiscountPolicy policy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(EVENT_ID));
                policy.addDiscount(new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, policy.id());

                assertThat(policy.isActive()).isTrue();
                verify(discountPolicyRepository).save(policy);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may add a discount to an event-scoped discount policy.
        @Test
        void addDiscountToPolicy_eventScoped_whenActorCanManageEventsButNotDiscountPolicies_shouldSave() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                DiscountPolicy policy = new DiscountPolicy(
                                DiscountPolicyId.random(),
                                COMPANY_ID,
                                PolicyScope.forSingleEvent(EVENT_ID));
                policy.addDiscount(new Discount("Existing", BigDecimal.TEN, new MinTicketPolicy(1)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                Discount added = new Discount("Added", BigDecimal.valueOf(15), new MinTicketPolicy(2));

                service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), added);

                assertThat(policy.discounts())
                                .extracting(Discount::getDiscountName)
                                .contains("Existing", "Added");

                verify(discountPolicyRepository).save(policy);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may move an event-scoped purchase policy from one event to
        // another event.
        @Test
        void modifyPurchasePolicyScope_eventToEvent_whenActorCanManageEventsButNotPurchasePolicies_shouldSave() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                PolicyScope newScope = PolicyScope.forSingleEvent(OTHER_EVENT_ID);

                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.modifyPurchasePolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope);

                assertThat(policy.scope()).isEqualTo(newScope);
                verify(purchasePolicyRepository).save(policy);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-61:
        // Event manager may move an event-scoped discount policy from one event to
        // another event.
        @Test
        void modifyDiscountPolicyScope_eventToEvent_whenActorCanManageEventsButNotDiscountPolicies_shouldSave() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
                when(permissionChecker.canManageEvents(ACTOR_ID, COMPANY_ID)).thenReturn(true);

                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));
                PolicyScope newScope = PolicyScope.forSingleEvent(OTHER_EVENT_ID);

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                service.modifyDiscountPolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope);

                assertThat(policy.scope()).isEqualTo(newScope);
                verify(discountPolicyRepository).save(policy);
        }

        // PRD-03 / PRD-15 / UC20 / UAT-62:
        // Event-management permission alone is not enough for company-wide purchase
        // policy creation.
        @Test
        void createCompanyWidePurchasePolicy_whenActorCanManageEventsButNotPurchasePolicies_shouldThrowSecurityException() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

                assertThatThrownBy(() -> service.createCompanyWidePurchasePolicy(
                                ACTOR_ID,
                                COMPANY_ID,
                                "Company max tickets",
                                new MaxTicketPolicy(4)))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("purchase policies");

                verify(purchasePolicyRepository, never()).save(any());
        }

        // PRD-03 / PRD-15 / UC20 / UAT-62:
        // Event-management permission alone is not enough for company-wide discount
        // policy creation.
        @Test
        void createCompanyWideDiscountPolicy_whenActorCanManageEventsButNotDiscountPolicies_shouldThrowSecurityException() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

                assertThatThrownBy(() -> service.createCompanyWideDiscountPolicy(
                                ACTOR_ID,
                                COMPANY_ID,
                                "Company discount",
                                false,
                                BigDecimal.valueOf(20),
                                new MinTicketPolicy(2),
                                true,
                                LocalDate.now().plusDays(10)))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("discount policies");

                verify(discountPolicyRepository, never()).save(any());
        }

        // PRD-03 / PRD-15 / UC20 / UAT-62:
        // Event manager cannot change an event-scoped purchase policy into
        // company-wide.
        @Test
        void modifyPurchasePolicyScope_eventToCompanyWide_whenActorCanManageEventsButNotPurchasePolicies_shouldThrowSecurityException() {
                when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

                PurchasePolicy policy = activePurchasePolicy(EVENT_ID, 4);
                when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                assertThatThrownBy(() -> service.modifyPurchasePolicyScope(
                                ACTOR_ID,
                                COMPANY_ID,
                                policy.id(),
                                PolicyScope.companyWideScope()))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("purchase policies");

                verify(purchasePolicyRepository, never()).save(any());
        }

        // PRD-03 / PRD-15 / UC20 / UAT-62:
        // Event manager cannot change an event-scoped discount policy into
        // company-wide.
        @Test
        void modifyDiscountPolicyScope_eventToCompanyWide_whenActorCanManageEventsButNotDiscountPolicies_shouldThrowSecurityException() {
                when(permissionChecker.canManageDiscountPolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

                DiscountPolicy policy = activeDiscountPolicy(
                                EVENT_ID,
                                new Discount("Buy two", BigDecimal.valueOf(20), new MinTicketPolicy(2)));

                when(discountPolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

                assertThatThrownBy(() -> service.modifyDiscountPolicyScope(
                                ACTOR_ID,
                                COMPANY_ID,
                                policy.id(),
                                PolicyScope.companyWideScope()))
                                .isInstanceOf(SecurityException.class)
                                .hasMessageContaining("discount policies");

                verify(discountPolicyRepository, never()).save(any());
        }

}