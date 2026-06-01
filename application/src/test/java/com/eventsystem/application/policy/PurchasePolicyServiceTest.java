package com.eventsystem.application.policy;

import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.application.policy.policybuilder.PolicyCommandAssembler;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.policy.PurchasePolicyId;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.MinAgePolicy;
import com.eventsystem.domain.policy.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.composite.OrPolicy;
import com.eventsystem.domain.policy.composite.ZoneSpecificPolicy;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchasePolicyServiceTest {

    @Mock
    private IPurchasePolicyRepository purchasePolicyRepository;

    @Mock
    private ICompanyPermissionServicePort permissionChecker;

    @Mock
    private IEventManagementPort eventServicePort;

    @Mock
    private IMemberInformationPort memberInfoPort;

    private PolicyCommandAssembler policyAssembler;
    private PurchasePolicyService service;

    private static final MemberId ACTOR_ID = new MemberId("actor-1");
    private static final CompanyId COMPANY_ID = new CompanyId("company-1");
    private static final CompanyId OTHER_COMPANY_ID = new CompanyId("company-2");

    private static final EventId EVENT_ID = new EventId("event-1");
    private static final EventId OTHER_EVENT_ID = new EventId("event-2");
    private static final EventId FOREIGN_EVENT_ID = new EventId("foreign-event");

    private static final ZoneId REGULAR_ZONE = new ZoneId("regular-zone");
    private static final ZoneId VIP_ZONE = new ZoneId("vip-zone");

    @BeforeEach
    void setUp() {
        policyAssembler = new PolicyCommandAssembler();
        service = new PurchasePolicyService(purchasePolicyRepository, permissionChecker, eventServicePort, memberInfoPort, policyAssembler);
    }

    private PurchaseContext purchaseContext(int ticketCount, LocalDate buyerBirthDate) {
        return new PurchaseContext(
                EVENT_ID,
                COMPANY_ID,
                repeatedZones(REGULAR_ZONE, ticketCount),
                buyerBirthDate,
                null
        );
    }

    private PurchaseContext purchaseContext(List<ZoneId> zones, LocalDate buyerBirthDate) {
        return new PurchaseContext(EVENT_ID, COMPANY_ID, zones, buyerBirthDate, null);
    }

    private List<ZoneId> repeatedZones(ZoneId zoneId, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> zoneId)
                .toList();
    }

    private LocalDate age(int years) {
        return LocalDate.now().minusYears(years);
    }

    private PurchasePolicy maxTicketPolicy(int maxTickets) {
        return PurchasePolicy.NewMaxTicketPolicy(COMPANY_ID, "Max " + maxTickets + " tickets", maxTickets);
    }

    private PurchasePolicy minTicketPolicy(int minTickets) {
        return PurchasePolicy.NewPurchasePolicy(
                COMPANY_ID,
                "Min " + minTickets + " tickets",
                new MinTicketPolicy(minTickets)
        );
    }

    private PurchasePolicy minAgePolicy(int minAge) {
        return PurchasePolicy.NewPurchasePolicy(
                COMPANY_ID,
                "Minimum age " + minAge,
                new MinAgePolicy(minAge)
        );
    }

    private PurchasePolicy companyWidePolicy(String name) {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, name);
        policy.setCompanyWide();
        return policy;
    }

    private PurchasePolicy eventScopedPolicy(String name, EventId eventId) {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, name);
        policy.activateForEvent(eventId);
        return policy;
    }

    private void allowManagePurchasePolicies() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
    }

    private void denyManagePurchasePolicies() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);
    }

    private void stubPolicyExists(PurchasePolicy policy) {
        when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));
    }


    private BuyerReference memberBuyer() {
        return new BuyerReference(BuyerType.MEMBER, null, "member-1");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Construction and basic repository queries
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void constructorRejectsNullDependencies() {
        assertThatThrownBy(() -> new PurchasePolicyService(null, permissionChecker, eventServicePort, memberInfoPort, policyAssembler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("purchasePolicyRepository");

        assertThatThrownBy(() -> new PurchasePolicyService(purchasePolicyRepository, null, eventServicePort, memberInfoPort, policyAssembler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("permissionChecker");

        assertThatThrownBy(() -> new PurchasePolicyService(purchasePolicyRepository, permissionChecker, null, memberInfoPort, policyAssembler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventOwnershipChecker");

        assertThatThrownBy(() -> new PurchasePolicyService(purchasePolicyRepository, permissionChecker, eventServicePort, null, policyAssembler))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("memberInfoPort");
        assertThatThrownBy(() -> new PurchasePolicyService(purchasePolicyRepository, permissionChecker, eventServicePort, memberInfoPort, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("memberInfoPort");
    }

    @Test
    void findById_returnsRepositoryResult() {
        PurchasePolicy policy = companyWidePolicy("Allow all");
        when(purchasePolicyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

        Optional<PurchasePolicy> result = service.findById(policy.id());

        assertThat(result).contains(policy);
    }

    @Test
    void getByIdOrThrow_whenPolicyExists_returnsPolicy() {
        PurchasePolicy policy = companyWidePolicy("Allow all");
        stubPolicyExists(policy);

        PurchasePolicy result = service.getByIdOrThrow(policy.id());

        assertThat(result).isSameAs(policy);
    }

    @Test
    void getByIdOrThrow_whenPolicyMissing_throwsPolicyException() {
        PurchasePolicyId policyId = PurchasePolicyId.random();
        when(purchasePolicyRepository.findById(policyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIdOrThrow(policyId))
                .isInstanceOf(PolicyException.class)
                .hasMessageContaining("Purchase policy not found");
    }

    @Test
    void findByCompanyId_delegatesToRepository() {
        PurchasePolicy policy = companyWidePolicy("Company policy");
        when(purchasePolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

        List<PurchasePolicy> result = service.findByCompanyId(COMPANY_ID);

        assertThat(result).containsExactly(policy);
    }

    @Test
    void findActiveByCompanyId_delegatesToRepository() {
        PurchasePolicy policy = companyWidePolicy("Active policy");
        when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

        List<PurchasePolicy> result = service.findActiveByCompanyId(COMPANY_ID);

        assertThat(result).containsExactly(policy);
    }

    @Test
    void findApplicableToEvent_delegatesToRepository() {
        PurchasePolicy policy = eventScopedPolicy("Event policy", EVENT_ID);
        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of(policy));

        List<PurchasePolicy> result = service.findApplicableToEvent(EVENT_ID);

        assertThat(result).containsExactly(policy);
    }

    @Test
    void findApplicableToPurchase_delegatesToRepository() {
        PurchasePolicy policy = eventScopedPolicy("Event policy", EVENT_ID);
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        List<PurchasePolicy> result = service.findApplicableToPurchase(COMPANY_ID, EVENT_ID);

        assertThat(result).containsExactly(policy);
    }

    @Test
    void existsById_delegatesToRepository() {
        PurchasePolicyId policyId = PurchasePolicyId.random();
        when(purchasePolicyRepository.existsById(policyId)).thenReturn(true);

        assertThat(service.existsById(policyId)).isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────
    // UC16 / UAT-44: Define purchase policy
    // ─────────────────────────────────────────────────────────────────────

    // UAT-44: Set Purchase Policy - define max 4 tickets per buyer for an event.
    @Test
    void savePurchasePolicy_whenAuthorizedAndEventBelongsToCompany_savesPolicy_UAT44() {
        PurchasePolicy policy = maxTicketPolicy(4);
        policy.activateForEvent(EVENT_ID);

        allowManagePurchasePolicies();
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, policy);

        verify(purchasePolicyRepository).save(policy);
    }

    // UC16: only authorized owner/manager can define or update purchase policies.
    @Test
    void savePurchasePolicy_whenUnauthorized_throwsAndDoesNotSave_UC16() {
        PurchasePolicy policy = maxTicketPolicy(4);

        denyManagePurchasePolicies();

        assertThatThrownBy(() -> service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, policy))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not allowed");

        verify(purchasePolicyRepository, never()).save(any());
        verify(eventServicePort, never()).isEventByCompany(any(), any());
    }

    // UC16: company id in the request must match the policy aggregate owner.
    @Test
    void savePurchasePolicy_whenCompanyArgumentDoesNotMatchPolicyCompany_throwsAndDoesNotSave_UC16() {
        PurchasePolicy policy = maxTicketPolicy(4);

        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, OTHER_COMPANY_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.savePurchasePolicy(ACTOR_ID, OTHER_COMPANY_ID, policy))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another company");

        verify(purchasePolicyRepository, never()).save(any());
    }

    // UC16: event-scoped policies may only be linked to events owned by the same company.
    @Test
    void savePurchasePolicy_whenScopedEventDoesNotBelongToCompany_throwsAndDoesNotSave_UC16() {
        PurchasePolicy policy = maxTicketPolicy(4);
        policy.activateForEvent(FOREIGN_EVENT_ID);

        allowManagePurchasePolicies();
        when(eventServicePort.isEventByCompany(FOREIGN_EVENT_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, policy))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("event does not belong to company");

        verify(purchasePolicyRepository, never()).save(any());
    }

    @Test
    void savePurchasePolicy_whenCompanyWide_hasNoEventOwnershipCheckAndSaves() {
        PurchasePolicy policy = companyWidePolicy("Company max tickets");

        allowManagePurchasePolicies();

        service.savePurchasePolicy(ACTOR_ID, COMPANY_ID, policy);

        verify(eventServicePort, never()).isEventByCompany(any(), any());
        verify(purchasePolicyRepository).save(policy);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scope editing operations
    // ─────────────────────────────────────────────────────────────────────

    // UC16 / UAT-44: link an existing purchase policy to an event.
    @Test
    void addEventToPolicy_whenAuthorizedAndCompanyOwnsEvent_addsEventAndSaves_UAT44() {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, "Existing policy");

        allowManagePurchasePolicies();
        stubPolicyExists(policy);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.addEventToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

        assertThat(policy.scope().eventIds()).containsExactly(EVENT_ID);
        verify(purchasePolicyRepository).save(policy);
    }

    @Test
    void addEventToPolicy_whenEventDoesNotBelongToCompany_throwsAndDoesNotSave() {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, "Existing policy");

        allowManagePurchasePolicies();
        stubPolicyExists(policy);
        when(eventServicePort.isEventByCompany(FOREIGN_EVENT_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.addEventToPolicy(ACTOR_ID, COMPANY_ID, policy.id(), FOREIGN_EVENT_ID))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("event does not belong to company");

        assertThat(policy.scope().eventIds()).isEmpty();
        verify(purchasePolicyRepository, never()).save(any());
    }

    @Test
    void removeEventFromPolicy_whenAuthorizedAndCompanyOwnsEvent_removesEventAndSaves() {
        PurchasePolicy policy = eventScopedPolicy("Existing policy", EVENT_ID);

        allowManagePurchasePolicies();
        stubPolicyExists(policy);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.removeEventFromPolicy(ACTOR_ID, COMPANY_ID, policy.id(), EVENT_ID);

        assertThat(policy.scope().eventIds()).doesNotContain(EVENT_ID);
        assertThat(policy.isActive()).isFalse();
        verify(purchasePolicyRepository).save(policy);
    }

    @Test
    void modifyPurchasePolicyScope_whenNewScopeContainsOwnedEvents_setsScopeAndSaves_UC16() {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, "Scoped policy");
        PolicyScope newScope = PolicyScope.forEvents(Set.of(EVENT_ID, OTHER_EVENT_ID));

        allowManagePurchasePolicies();
        stubPolicyExists(policy);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(OTHER_EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.modifyPurchasePolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope);

        assertThat(policy.scope()).isEqualTo(newScope);
        assertThat(policy.isActive()).isTrue();
        verify(purchasePolicyRepository).save(policy);
    }
    
    @Test
    void modifyPurchasePolicyScope_whenAnyEventIsForeign_throwsAndDoesNotSave() {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, "Scoped policy");
        PolicyScope newScope = PolicyScope.forEvents(Set.of(EVENT_ID, FOREIGN_EVENT_ID));

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        when(eventServicePort.isEventByCompany(any(EventId.class), eq(COMPANY_ID)))
                .thenAnswer(invocation -> {
                    EventId checkedEventId = invocation.getArgument(0);
                    return !checkedEventId.equals(FOREIGN_EVENT_ID);
                });

        assertThatThrownBy(() -> service.modifyPurchasePolicyScope(ACTOR_ID, COMPANY_ID, policy.id(), newScope))
                .isInstanceOf(SecurityException.class);

        assertThat(policy.scope().eventIds()).isEmpty();
        verify(purchasePolicyRepository, never()).save(any());
    }

    @Test
    void setToCompanyWide_whenAuthorized_setsCompanyWideAndSaves() {
        PurchasePolicy policy = eventScopedPolicy("Policy", EVENT_ID);

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        service.setToCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

        assertThat(policy.scope().isCompanyWide()).isTrue();
        assertThat(policy.scope().eventIds()).containsExactly(EVENT_ID);
        verify(purchasePolicyRepository).save(policy);
    }

    @Test
    void setToNotCompanyWide_whenAuthorized_removesCompanyWideButKeepsExplicitEvents() {
        PurchasePolicy policy = eventScopedPolicy("Policy", EVENT_ID);
        policy.setCompanyWide();

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        service.setToNotCompanyWide(ACTOR_ID, COMPANY_ID, policy.id());

        assertThat(policy.scope().isCompanyWide()).isFalse();
        assertThat(policy.scope().eventIds()).containsExactly(EVENT_ID);
        assertThat(policy.isActive()).isTrue();
        verify(purchasePolicyRepository).save(policy);
    }

    @Test
    void renamePurchasePolicy_whenAuthorized_renamesAndSaves() {
        PurchasePolicy policy = companyWidePolicy("Old name");

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        service.renamePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id(), "New name");

        assertThat(policy.policyName()).isEqualTo("New name");
        verify(purchasePolicyRepository).save(policy);
    }

    @Test
    void renamePurchasePolicy_whenPolicyBelongsToOtherCompany_throwsAndDoesNotSave() {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(OTHER_COMPANY_ID, "Other company policy");
        policy.setCompanyWide();

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        assertThatThrownBy(() -> service.renamePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id(), "New name"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("other companies");

        verify(purchasePolicyRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Company-wide bulk operations
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void clearAllPurchasePoliciesOfCompany_whenAuthorized_deletesEveryCompanyPolicy() {
        PurchasePolicy first = companyWidePolicy("First");
        PurchasePolicy second = eventScopedPolicy("Second", EVENT_ID);

        allowManagePurchasePolicies();
        when(purchasePolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(first, second));

        service.clearAllPurchasePoliciesOfCompany(ACTOR_ID, COMPANY_ID);

        verify(purchasePolicyRepository).deleteById(first.id());
        verify(purchasePolicyRepository).deleteById(second.id());
    }

    @Test
    void clearAllPurchasePoliciesOfCompany_whenUnauthorized_throwsAndDoesNotDelete() {
        denyManagePurchasePolicies();

        assertThatThrownBy(() -> service.clearAllPurchasePoliciesOfCompany(ACTOR_ID, COMPANY_ID))
                .isInstanceOf(SecurityException.class);

        verify(purchasePolicyRepository, never()).findByCompanyId(any());
        verify(purchasePolicyRepository, never()).deleteById(any());
    }

    @Test
    void deactivateAllCompanyPurchasePolicies_deactivatesOnlyActivePoliciesAndSavesThem() {
        PurchasePolicy active = companyWidePolicy("Active");
        PurchasePolicy inactive = PurchasePolicy.NewAllowAllPolicy(COMPANY_ID, "Inactive");

        allowManagePurchasePolicies();
        when(purchasePolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(active, inactive));

        service.deactivateAllCompanyPurchasePolicies(ACTOR_ID, COMPANY_ID);

        assertThat(active.isActive()).isFalse();
        assertThat(inactive.isActive()).isFalse();
        verify(purchasePolicyRepository).save(active);
        verify(purchasePolicyRepository, never()).save(inactive);
    }

    @Test
    void removeEventFromAllPurchasePolicyScopes_removesEventOnlyFromPoliciesThatContainIt() {
        PurchasePolicy affected = eventScopedPolicy("Affected", EVENT_ID);
        PurchasePolicy unaffected = eventScopedPolicy("Unaffected", OTHER_EVENT_ID);

        allowManagePurchasePolicies();
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);
        when(purchasePolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(affected, unaffected));

        service.removeEventFromAllPurchasePolicyScopes(ACTOR_ID, COMPANY_ID, EVENT_ID);

        assertThat(affected.scope().eventIds()).doesNotContain(EVENT_ID);
        assertThat(unaffected.scope().eventIds()).containsExactly(OTHER_EVENT_ID);
        verify(purchasePolicyRepository).save(affected);
        verify(purchasePolicyRepository, never()).save(unaffected);
    }

    @Test
    void clearEventsFromAllPurchasePolicies_clearsExplicitEventsButPreservesCompanyWideFlag() {
        PurchasePolicy companyWideWithEvent = eventScopedPolicy("Company wide", EVENT_ID);
        companyWideWithEvent.setCompanyWide();
        PurchasePolicy eventOnly = eventScopedPolicy("Event only", OTHER_EVENT_ID);

        allowManagePurchasePolicies();
        when(purchasePolicyRepository.findByCompanyId(COMPANY_ID)).thenReturn(List.of(companyWideWithEvent, eventOnly));

        service.clearEventsFromAllPurchasePolicies(ACTOR_ID, COMPANY_ID);

        assertThat(companyWideWithEvent.scope().isCompanyWide()).isTrue();
        assertThat(companyWideWithEvent.scope().eventIds()).isEmpty();
        assertThat(companyWideWithEvent.isActive()).isTrue();

        assertThat(eventOnly.scope().isCompanyWide()).isFalse();
        assertThat(eventOnly.scope().eventIds()).isEmpty();
        assertThat(eventOnly.isActive()).isFalse();

        verify(purchasePolicyRepository).save(companyWideWithEvent);
        verify(purchasePolicyRepository).save(eventOnly);
    }

    @Test
    void deletePurchasePolicy_whenAuthorizedAndCompanyOwnsPolicy_deletesPolicy() {
        PurchasePolicy policy = companyWidePolicy("Policy");

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        service.deletePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id());

        verify(purchasePolicyRepository).deleteById(policy.id());
    }

    @Test
    void deletePurchasePolicy_whenCompanyDoesNotOwnPolicy_throwsAndDoesNotDelete() {
        PurchasePolicy policy = PurchasePolicy.NewAllowAllPolicy(OTHER_COMPANY_ID, "Other company policy");
        policy.setCompanyWide();

        allowManagePurchasePolicies();
        stubPolicyExists(policy);

        assertThatThrownBy(() -> service.deletePurchasePolicy(ACTOR_ID, COMPANY_ID, policy.id()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("other companies");

        verify(purchasePolicyRepository, never()).deleteById(any());
    }

    // ─────────────────────────────────────────────────────────────────────
    // UC9 / UAT-27: checkout purchase-policy validation
    // ─────────────────────────────────────────────────────────────────────

    // UC9: if no purchase policy applies, checkout is allowed by default.
    @Test
    void evaluatePurchasePolicyFor_whenNoApplicablePolicies_allowsPurchase_UC9() {
        PurchaseContext context = purchaseContext(2, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of());

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.failureReason()).isEmpty();
    }

    // UAT-44 + UC9: max 4 tickets policy allows checkout with 4 tickets.
    @Test
    void evaluatePurchasePolicyFor_whenMaxTicketPolicyPasses_allowsPurchase_UAT44_UC9() {
        PurchasePolicy policy = maxTicketPolicy(4);
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(4, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isTrue();
    }

    // UAT-27: Checkout Policy Violation - ticket quantity exceeds purchase policy limit.
    @Test
    void evaluatePurchasePolicyFor_whenMaxTicketPolicyFails_returnsFailure_UAT27() {
        PurchasePolicy policy = maxTicketPolicy(4);
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(5, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason())
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("Max 4 tickets")
                        .contains("Cannot Purchase more than 4 tickets"));
    }

    // UAT-27: checkout must halt by throwing an application exception when policy validation fails.
    @Test
    void requirePurchasePolicyFor_whenPolicyFails_throwsOrderViolatesPolicyException_UAT27() {
        PurchasePolicy policy = maxTicketPolicy(4);
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(5, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        assertThatThrownBy(() -> service.requirePurchasePolicyFor(context))
                .isInstanceOf(OrderViolatesPolicyException.class)
                .hasMessageContaining("Max 4 tickets")
                .hasMessageContaining("Cannot Purchase more than 4 tickets");
    }

    @Test
    void validatePurchasePolicyFor_returnsTrueWhenAllPoliciesPass() {
        PurchasePolicy maxPolicy = maxTicketPolicy(4);
        PurchasePolicy agePolicy = minAgePolicy(18);
        maxPolicy.activateForEvent(EVENT_ID);
        agePolicy.setCompanyWide();

        PurchaseContext context = purchaseContext(2, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(maxPolicy, agePolicy));

        assertThat(service.validatePurchasePolicyFor(context)).isTrue();
    }

    @Test
    void validatePurchasePolicyFor_returnsFalseWhenAnyApplicablePolicyFails() {
        PurchasePolicy maxPolicy = maxTicketPolicy(4);
        PurchasePolicy agePolicy = minAgePolicy(18);
        maxPolicy.activateForEvent(EVENT_ID);
        agePolicy.setCompanyWide();

        PurchaseContext context = purchaseContext(2, age(16));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(maxPolicy, agePolicy));

        assertThat(service.validatePurchasePolicyFor(context)).isFalse();
    }

    // Requirement 4.3II / UC16: purchase policies support age restrictions.
    @Test
    void evaluatePurchasePolicyFor_whenMinAgePolicyFails_returnsFailure_UC16() {
        PurchasePolicy policy = minAgePolicy(18);
        policy.setCompanyWide();

        PurchaseContext context = purchaseContext(1, age(17));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason())
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("Minimum age 18")
                        .contains("Buyer must be over age 18"));
    }

    // Requirement 4.3II / UC16: purchase policies support min-ticket restrictions.
    @Test
    void evaluatePurchasePolicyFor_whenMinTicketPolicyFails_returnsFailure_UC16() {
        PurchasePolicy policy = minTicketPolicy(2);
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(1, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason())
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("Min 2 tickets")
                        .contains("Cannot Purchase less than 2 tickets"));
    }

    // Requirement 4.3II / UC16: purchase policies support AND composition.
    @Test
    void evaluatePurchasePolicyFor_whenAndPolicyPasses_allowsPurchase_UC16() {
        PurchasePolicy policy = PurchasePolicy.NewPurchasePolicy(
                COMPANY_ID,
                "Age 18 and max 4",
                new AndPolicy(List.of(new MinAgePolicy(18), new MaxTicketPolicy(4)))
        );
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(4, age(18));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isTrue();
    }

    // Requirement 4.3II / UC16: purchase policies support OR composition.
    @Test
    void evaluatePurchasePolicyFor_whenOrPolicyHasOnePassingBranch_allowsPurchase_UC16() {
        PurchasePolicy policy = PurchasePolicy.NewPurchasePolicy(
                COMPANY_ID,
                "Max 2 or min 100",
                new OrPolicy(List.of(new MaxTicketPolicy(2), new MinTicketPolicy(100)))
        );
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(2, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void evaluatePurchasePolicyFor_whenOrPolicyFailsAllBranches_returnsFailure_UC16() {
        PurchasePolicy policy = PurchasePolicy.NewPurchasePolicy(
                COMPANY_ID,
                "Max 2 or min 100",
                new OrPolicy(List.of(new MaxTicketPolicy(2), new MinTicketPolicy(100)))
        );
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(3, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason())
                .hasValueSatisfying(reason -> assertThat(reason).contains("Max 2 or min 100"));
    }

    @Test
    void evaluatePurchasePolicyFor_whenZoneSpecificPolicyAppliesOnlyToVipTickets() {
        PurchasePolicy policy = PurchasePolicy.NewPurchasePolicy(
                COMPANY_ID,
                "Max 1 VIP ticket",
                new ZoneSpecificPolicy(Set.of(VIP_ZONE), new MaxTicketPolicy(1), true)
        );
        policy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(List.of(VIP_ZONE, VIP_ZONE, REGULAR_ZONE), age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID)).thenReturn(List.of(policy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason())
                .hasValueSatisfying(reason -> assertThat(reason).contains("Max 1 VIP ticket"));
    }

    @Test
    void evaluatePurchasePolicyFor_whenFirstPolicyFails_doesNotEvaluateFurtherPolicies() {
        PurchasePolicy failingPolicy = maxTicketPolicy(1);
        PurchasePolicy passingPolicy = minAgePolicy(18);
        failingPolicy.activateForEvent(EVENT_ID);
        passingPolicy.activateForEvent(EVENT_ID);

        PurchaseContext context = purchaseContext(2, age(25));
        when(purchasePolicyRepository.findApplicableToPurchase(COMPANY_ID, EVENT_ID))
                .thenReturn(List.of(failingPolicy, passingPolicy));

        PolicyValidationResult result = service.evaluatePurchasePolicyFor(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason())
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains("Max 1 tickets")
                        .doesNotContain("Minimum age 18"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Legacy validation port compatibility
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void validatePurchasePolicy_legacyMethod_whenNoApplicablePoliciesForEvent_returnsTrue() {
        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of());

        boolean result = service.validatePurchasePolicy(
                EVENT_ID.value(),
                new BuyerReference(BuyerType.MEMBER, null, "member-1"),
                List.of(new OrderItem(REGULAR_ZONE.value(), null, 1, Money.of(BigDecimal.TEN, "ILS")))
        );

        assertThat(result).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void validatePurchasePolicy_legacyMethod_whenApplicablePoliciesExist_returnsFalseFailClosed() {
        PurchasePolicy policy = maxTicketPolicy(4);
        policy.activateForEvent(EVENT_ID);
        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of(policy));

        boolean result = service.validatePurchasePolicy(
                EVENT_ID.value(),
                new BuyerReference(BuyerType.MEMBER, null, "member-1"),
                List.of(new OrderItem(REGULAR_ZONE.value(), null, 1, Money.of(BigDecimal.TEN, "ILS")))
        );

        assertThat(result).isFalse();
    }

    @Test
    void createNewAllowAllPurchasePolicy_whenAuthorizedAndEventBelongsToCompany_savesEventScopedAllowAllPolicy_UAT44() {
        allowManagePurchasePolicies();
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.createNewAllowAllPurchasePolicy(ACTOR_ID, COMPANY_ID, EVENT_ID);

        org.mockito.ArgumentCaptor<PurchasePolicy> policyCaptor = org.mockito.ArgumentCaptor.forClass(PurchasePolicy.class);
        verify(purchasePolicyRepository).save(policyCaptor.capture());

        PurchasePolicy savedPolicy = policyCaptor.getValue();
        assertThat(savedPolicy.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedPolicy.policyName()).isEqualTo("AllowAll");
        assertThat(savedPolicy.isActiveForEvent(EVENT_ID)).isTrue();
    }

    // UAT-44: Company cannot create an event-scoped purchase policy for an event it does not own.
    @Test
    void createNewAllowAllPurchasePolicy_whenEventDoesNotBelongToCompany_throwsAndDoesNotSave_UAT44() {
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createNewAllowAllPurchasePolicy(ACTOR_ID, COMPANY_ID, EVENT_ID))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("event does not belong to company");

        verify(purchasePolicyRepository, never()).save(any());
    }

    // UAT-44: Policy module can explicitly block purchases for an event.
    @Test
    void setNotAllowedPurchasePolicy_whenAuthorizedAndEventBelongsToCompany_savesEventScopedBlockingPolicy_UAT44() {
        allowManagePurchasePolicies();
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        service.setNotAllowedPurchasePolicy(ACTOR_ID, COMPANY_ID, EVENT_ID);

        org.mockito.ArgumentCaptor<PurchasePolicy> policyCaptor = org.mockito.ArgumentCaptor.forClass(PurchasePolicy.class);
        verify(purchasePolicyRepository).save(policyCaptor.capture());

        PurchasePolicy savedPolicy = policyCaptor.getValue();
        assertThat(savedPolicy.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedPolicy.policyName()).isEqualTo("NotAllowed");
        assertThat(savedPolicy.isActiveForEvent(EVENT_ID)).isTrue();

        PurchaseContext context = purchaseContext(1, age(25));
        assertThat(savedPolicy.evaluate(context).isSuccess()).isFalse();
    }

    // UAT-44: Active purchase-policy existence can come from company-wide active policies.
    @Test
    void doesHaveActivePurchasePolicy_whenCompanyHasActivePolicy_returnsTrue_UAT44() {
        PurchasePolicy policy = companyWidePolicy("Company-wide allow all");
        when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of(policy));

        boolean result = service.doesHaveActivePurchasePolicy(EVENT_ID, COMPANY_ID);

        assertThat(result).isTrue();
    }

    // UAT-44: Active purchase-policy existence can come from event-applicable policies.
    @Test
    void doesHaveActivePurchasePolicy_whenEventHasApplicablePolicy_returnsTrue_UAT44() {
        PurchasePolicy policy = eventScopedPolicy("Event allow all", EVENT_ID);
        when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of());
        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of(policy));

        boolean result = service.doesHaveActivePurchasePolicy(EVENT_ID, COMPANY_ID);

        assertThat(result).isTrue();
    }

    @Test
    void doesHaveActivePurchasePolicy_whenNoCompanyOrEventPolicies_returnsFalse() {
        when(purchasePolicyRepository.findActiveByCompanyId(COMPANY_ID)).thenReturn(List.of());
        when(purchasePolicyRepository.findApplicableToEvent(EVENT_ID)).thenReturn(List.of());

        boolean result = service.doesHaveActivePurchasePolicy(EVENT_ID, COMPANY_ID);

        assertThat(result).isFalse();
    }

    // UC7 / UAT-16: Build a complete purchase context for checkout validation.
    @Test
    void createPurchaseContext_usesEventCompanyZonesAndBuyerBirthdate_UAT16() {
        BuyerReference buyer = new BuyerReference(BuyerType.MEMBER, null, "member-1");
        List<OrderItem> items = List.of(
                new OrderItem(REGULAR_ZONE.value(), null, 2, Money.of(BigDecimal.valueOf(100), "ILS"))
        );
        LocalDate birthDate = LocalDate.of(2000, 1, 1);

        when(eventServicePort.companyOfEvent(EVENT_ID)).thenReturn(COMPANY_ID);
        when(eventServicePort.getZonesOfTicketsForEvent(EVENT_ID, items))
                .thenReturn(List.of(REGULAR_ZONE, REGULAR_ZONE));
        when(memberInfoPort.getMemberBirthdate(new MemberId("member-1"))).thenReturn(birthDate);

        PurchaseContext context = service.createPurchaseContext(EVENT_ID, buyer, items);

        assertThat(context.eventId()).isEqualTo(EVENT_ID);
        assertThat(context.companyId()).isEqualTo(COMPANY_ID);
        assertThat(context.zonesOfEachEventTicket()).containsExactly(REGULAR_ZONE, REGULAR_ZONE);
        assertThat(context.buyerBirthDate()).isEqualTo(birthDate);
        assertThat(context.discountCode()).isNull();
    }

    @Test
    void fromPurchaseInfo_nullArguments_throw() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.createPurchaseContext((EventId) null, memberBuyer(), List.of()));

        assertThatNullPointerException()
                .isThrownBy(() -> service.createPurchaseContext(EVENT_ID, null, List.of()));

        assertThatNullPointerException()
                .isThrownBy(() -> service.createPurchaseContext(EVENT_ID, memberBuyer(), null));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Command-based policy creation for UI/API
    // ─────────────────────────────────────────────────────────────────────

    // PRD-03 / PP-03 / PP-05 / PP-06 / PP-07 / TST-06:
    // Owner/manager defines purchase policy through application layer.
    // Policy has event scope, age restriction, ticket quantity restriction, and AND composition.
    @Test
    void createPurchasePolicy_fromCommand_shouldAssemblePolicyAndSaveIt() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Adults max 4 tickets",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                new PolicyRuleCommand(
                        "AND",
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new PolicyRuleCommand("MIN_AGE", 18, null, null, null, null),
                                new PolicyRuleCommand("MAX_TICKETS", 4, null, null, null, null)
                        )
                ),
                true
        );

        PurchasePolicyId policyId = service.createPurchasePolicy(command);

        ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
        verify(purchasePolicyRepository).save(captor.capture());

        PurchasePolicy savedPolicy = captor.getValue();

        assertThat(policyId).isEqualTo(savedPolicy.id());
        assertThat(savedPolicy.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedPolicy.policyName()).isEqualTo("Adults max 4 tickets");
        assertThat(savedPolicy.scope().isCompanyWide()).isFalse();
        assertThat(savedPolicy.scope().eventIds()).containsExactly(EVENT_ID);
        assertThat(savedPolicy.isActiveForEvent(EVENT_ID)).isTrue();

        assertThat(savedPolicy.isPurchaseAllowedInContext(purchaseContext(4, age(20)))).isTrue();
        assertThat(savedPolicy.isPurchaseAllowedInContext(purchaseContext(5, age(20)))).isFalse();
        assertThat(savedPolicy.isPurchaseAllowedInContext(purchaseContext(4, age(17)))).isFalse();
    }

    // PRD-03 / PP-03 / PP-07 / TST-06:
    // Owner/manager defines company-wide purchase policy through application layer.
    @Test
    void createPurchasePolicy_companyWideCommand_shouldSaveCompanyWidePolicy() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Company allow all",
                new PolicyScopeCommand(true, Set.of()),
                new PolicyRuleCommand("ALLOW_ALL", null, null, null, null, null),
                true
        );

        service.createPurchasePolicy(command);

        ArgumentCaptor<PurchasePolicy> captor = ArgumentCaptor.forClass(PurchasePolicy.class);
        verify(purchasePolicyRepository).save(captor.capture());

        PurchasePolicy savedPolicy = captor.getValue();

        assertThat(savedPolicy.companyId()).isEqualTo(COMPANY_ID);
        assertThat(savedPolicy.scope().isCompanyWide()).isTrue();
        assertThat(savedPolicy.isActiveForEvent(EVENT_ID)).isTrue();
        assertThat(savedPolicy.isPurchaseAllowedInContext(purchaseContext(10, age(12)))).isTrue();

        verify(eventServicePort, never()).isEventByCompany(any(), any());
    }

    // TST-13:
    // Authorization failure is enforced in the Application layer, not only UI.
    @Test
    void createPurchasePolicy_whenUnauthorized_shouldThrowAndNotSave() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(false);

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Unauthorized policy",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                new PolicyRuleCommand("MAX_TICKETS", 2, null, null, null, null),
                true
        );

        assertThatThrownBy(() -> service.createPurchasePolicy(command))
                .isInstanceOf(SecurityException.class);

        verify(purchasePolicyRepository, never()).save(any());
        verify(eventServicePort, never()).isEventByCompany(any(), any());
    }

    // TST-13 / PP-03:
    // Application layer rejects event-scoped policy when event does not belong to company.
    @Test
    void createPurchasePolicy_whenEventDoesNotBelongToCompany_shouldThrowAndNotSave() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(FOREIGN_EVENT_ID, COMPANY_ID)).thenReturn(false);

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Foreign event policy",
                new PolicyScopeCommand(false, Set.of(FOREIGN_EVENT_ID.value())),
                new PolicyRuleCommand("MAX_TICKETS", 2, null, null, null, null),
                true
        );

        assertThatThrownBy(() -> service.createPurchasePolicy(command))
                .isInstanceOf(SecurityException.class);

        verify(purchasePolicyRepository, never()).save(any());
    }

    // PP-09 / TST-03:
    // Invalid command-based composite policy should be rejected and not saved.
    @Test
    void createPurchasePolicy_whenCommandContainsEmptyComposite_shouldThrowAndNotSave() {
        when(permissionChecker.canManagePurchasePolicies(ACTOR_ID, COMPANY_ID)).thenReturn(true);
        when(eventServicePort.isEventByCompany(EVENT_ID, COMPANY_ID)).thenReturn(true);

        PurchasePolicyCommand command = new PurchasePolicyCommand(
                ACTOR_ID.value(),
                COMPANY_ID.value(),
                "Invalid empty AND",
                new PolicyScopeCommand(false, Set.of(EVENT_ID.value())),
                new PolicyRuleCommand("AND", null, null, null, null, List.of()),
                true
        );

        assertThatThrownBy(() -> service.createPurchasePolicy(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AND policy requires children");

        verify(purchasePolicyRepository, never()).save(any());
    }

    // TST-03:
    // Null command should be rejected.
    @Test
    void createPurchasePolicy_whenCommandIsNull_shouldThrow() {
        assertThatThrownBy(() -> service.createPurchasePolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("command");
    }
}
