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
import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountPolicyId;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.policy.PurchasePolicyId;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
                new PolicyCommandAssembler()
        );

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

//     private PolicyScopeCommand companyWideScope() {
//         return new PolicyScopeCommand(true, Set.of());
//     }

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
                PolicyScope.forSingleEvent(eventId)
        );
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
                new MaxTicketPolicy(maxTickets)
        );
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
                true
        );

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
                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5))
        );

        when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID))
                .thenReturn(List.of(existingDiscount));

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Max 4 tickets",
                eventScope(EVENT_ID),
                valueRule("MAX_TICKETS", 4),
                true
        );

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
                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5))
        );

        when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID))
                .thenReturn(List.of(otherEventDiscount));

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Max 4 tickets",
                eventScope(EVENT_ID),
                valueRule("MAX_TICKETS", 4),
                true
        );

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
                true
        );

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
                true
        );

        assertThatThrownBy(() -> service.createDiscountPolicy(command))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Policy conflict")
                .hasMessageContaining("Buy five discount");

        verify(discountPolicyRepository, never()).save(any());
    }

        // UC16:
        // Inactive discount drafts may be saved even if they would conflict when active.
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
                false
        );

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
                PolicyScope.forSingleEvent(EVENT_ID)
        );
        discountPolicy.addDiscount(
                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5))
        );

        when(discountPolicyRepository.findById(discountPolicy.id()))
                .thenReturn(Optional.of(discountPolicy));
        when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                .thenReturn(List.of(existingPurchasePolicy));

        assertThatThrownBy(() ->
                service.activateDiscountPolicy(ACTOR_ID, COMPANY_ID, discountPolicy.id())
        )
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
                PolicyScope.forSingleEvent(EVENT_ID)
        );
        discountPolicy.addDiscount(
                new Discount("Safe discount", BigDecimal.valueOf(10), new MinTicketPolicy(2))
        );
        discountPolicy.activate();

        when(discountPolicyRepository.findById(discountPolicy.id()))
                .thenReturn(Optional.of(discountPolicy));
        when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID))
                .thenReturn(List.of(existingPurchasePolicy));

        DiscountCommand command = discountCommand(
                "Buy five discount",
                20,
                valueRule("MIN_TICKETS", 5)
        );

        assertThatThrownBy(() ->
                service.addDiscountToPolicy(ACTOR_ID, COMPANY_ID, discountPolicy.id(), command)
        )
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
                new MaxTicketPolicy(4)
        );

        DiscountPolicy eventDiscount = activeDiscountPolicy(
                EVENT_ID,
                new Discount("Buy five discount", BigDecimal.valueOf(20), new MinTicketPolicy(5))
        );

        when(purchasePolicyRepository.findById(purchasePolicy.id()))
                .thenReturn(Optional.of(purchasePolicy));
        when(discountPolicyRepository.findActiveByCompanyId(COMPANY_ID))
                .thenReturn(List.of(eventDiscount));

        assertThatThrownBy(() ->
                service.setPurchasePolicyCompanyWide(ACTOR_ID, COMPANY_ID, purchasePolicy.id())
        )
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Policy conflict");

        verify(purchasePolicyRepository, never()).save(any());
    }

    // PRD-03 / UC16:
    // Narrowing a policy scope should be allowed and does not need compatibility checks.
    @Test
    void removeEventFromPurchasePolicy_shouldSaveWithoutCheckingDiscountConflicts() {
        PurchasePolicy purchasePolicy = new PurchasePolicy(
                PurchasePolicyId.random(),
                COMPANY_ID,
                "Max 4 tickets",
                PolicyScope.forSingleEvent(EVENT_ID),
                new MaxTicketPolicy(4)
        );

        when(purchasePolicyRepository.findById(purchasePolicy.id()))
                .thenReturn(Optional.of(purchasePolicy));

        service.removeEventFromPurchasePolicy(
                ACTOR_ID,
                COMPANY_ID,
                purchasePolicy.id(),
                EVENT_ID
        );

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
                true
        );

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
                true
        );

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
                new MaxTicketPolicy(4)
        );

        assertThatThrownBy(() ->
                service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, foreignPolicy)
        )
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
}