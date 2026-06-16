package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryPurchasePolicyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

class InMemoryPurchasePolicyRepositoryTest {

    private InMemoryPurchasePolicyRepository repository;
    private CompanyId companyId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchasePolicyRepository();
        companyId = CompanyId.random();
        eventId = EventId.random();
    }

    // TST-06 / PP-03: purchase policies may be company-wide or event-specific.
    @Test
    void save_thenFindById_andExistsById_work() {
        PurchasePolicy policy = eventScopedPolicy(companyId, eventId);

        repository.save(policy);

        assertThat(repository.findById(policy.id())).contains(policy);
        assertThat(repository.existsById(policy.id())).isTrue();
    }

    @Test
    void findById_unknownId_returnsEmptyAndExistsFalse() {
        PurchasePolicyId unknownId = PurchasePolicyId.random();

        assertThat(repository.findById(unknownId)).isEmpty();
        assertThat(repository.existsById(unknownId)).isFalse();
    }

    @Test
    void findByCompanyId_returnsOnlyPoliciesOfThatCompany() {
        PurchasePolicy companyEventPolicy = eventScopedPolicy(companyId, eventId);
        PurchasePolicy companyWidePolicy = companyWidePolicy(companyId);
        PurchasePolicy otherCompanyPolicy = eventScopedPolicy(CompanyId.random(), eventId);

        repository.save(companyEventPolicy);
        repository.save(companyWidePolicy);
        repository.save(otherCompanyPolicy);

        List<PurchasePolicy> found = repository.findByCompanyId(companyId);

        assertThat(found).containsExactlyInAnyOrder(companyEventPolicy, companyWidePolicy);
    }

    @Test
    void findActiveByCompanyId_returnsOnlyScopedPoliciesOfThatCompany() {
        PurchasePolicy active = eventScopedPolicy(companyId, eventId);
        PurchasePolicy inactive = inactivePolicy(companyId);
        PurchasePolicy otherCompanyActive = eventScopedPolicy(CompanyId.random(), eventId);

        repository.save(active);
        repository.save(inactive);
        repository.save(otherCompanyActive);

        List<PurchasePolicy> found = repository.findActiveByCompanyId(companyId);

        assertThat(found).containsExactly(active);
    }

    @Test
    void findApplicableToEvent_includesCompanyWideAndMatchingEventPoliciesOnly() {
        PurchasePolicy eventPolicy = eventScopedPolicy(companyId, eventId);
        PurchasePolicy companyWidePolicy = companyWidePolicy(companyId);
        PurchasePolicy wrongEventPolicy = eventScopedPolicy(companyId, EventId.random());
        PurchasePolicy inactive = inactivePolicy(companyId);

        repository.save(eventPolicy);
        repository.save(companyWidePolicy);
        repository.save(wrongEventPolicy);
        repository.save(inactive);

        List<PurchasePolicy> found = repository.findApplicableToEvent(eventId);

        assertThat(found).containsExactlyInAnyOrder(eventPolicy, companyWidePolicy);
    }

    @Test
    void findApplicableToPurchase_filtersByActiveScopeCompanyAndEvent() {
        PurchasePolicy match = eventScopedPolicy(companyId, eventId);
        PurchasePolicy companyWideMatch = companyWidePolicy(companyId);
        PurchasePolicy wrongEvent = eventScopedPolicy(companyId, EventId.random());
        PurchasePolicy wrongCompanySameEvent = eventScopedPolicy(CompanyId.random(), eventId);
        PurchasePolicy inactive = inactivePolicy(companyId);

        repository.save(match);
        repository.save(companyWideMatch);
        repository.save(wrongEvent);
        repository.save(wrongCompanySameEvent);
        repository.save(inactive);

        List<PurchasePolicy> found = repository.findApplicableToPurchase(companyId, eventId);

        assertThat(found).containsExactlyInAnyOrder(match, companyWideMatch);
    }

    @Test
    void deleteById_removesPolicy() {
        PurchasePolicy policy = eventScopedPolicy(companyId, eventId);
        repository.save(policy);

        repository.deleteById(policy.id());

        assertThat(repository.findById(policy.id())).isEmpty();
        assertThat(repository.existsById(policy.id())).isFalse();
    }

    // PP-02 / PRD-03 / UC16:
    // Repository can query policies scoped exactly to one event for a company,
    // excluding company-wide and multi-event policies.
    @Test
    void findSingleEventPolicies_returnsOnlySingleEventPoliciesOfCompany() {
        EventId secondEventId = EventId.random();

        PurchasePolicy singleEvent = eventScopedPolicy(companyId, eventId);
        PurchasePolicy anotherSingleEvent = eventScopedPolicy(companyId, secondEventId);
        PurchasePolicy multiEvent = multiEventPolicy(companyId, eventId, secondEventId);
        PurchasePolicy companyWide = companyWidePolicy(companyId);
        PurchasePolicy otherCompanySingleEvent = eventScopedPolicy(CompanyId.random(), eventId);
        PurchasePolicy inactive = inactivePolicy(companyId);

        repository.save(singleEvent);
        repository.save(anotherSingleEvent);
        repository.save(multiEvent);
        repository.save(companyWide);
        repository.save(otherCompanySingleEvent);
        repository.save(inactive);

        List<PurchasePolicy> found = repository.findSingleEventPolicies(companyId);

        assertThat(found)
                .containsExactlyInAnyOrder(singleEvent, anotherSingleEvent)
                .doesNotContain(multiEvent, companyWide, otherCompanySingleEvent, inactive);
    }

    // PP-02 / PRD-03 / UC16:
    // Repository can query policies scoped specifically and only to one event,
    // excluding company-wide and multi-event policies that also apply to the event.
    @Test
    void findSpecificForEvent_returnsOnlyPoliciesScopedExactlyToThatEvent() {
        EventId secondEventId = EventId.random();

        PurchasePolicy singleEvent = eventScopedPolicy(companyId, eventId);
        PurchasePolicy otherCompanySingleEvent = eventScopedPolicy(CompanyId.random(), eventId);
        PurchasePolicy multiEvent = multiEventPolicy(companyId, eventId, secondEventId);
        PurchasePolicy companyWide = companyWidePolicy(companyId);
        PurchasePolicy wrongSingleEvent = eventScopedPolicy(companyId, secondEventId);
        PurchasePolicy inactive = inactivePolicy(companyId);

        repository.save(singleEvent);
        repository.save(otherCompanySingleEvent);
        repository.save(multiEvent);
        repository.save(companyWide);
        repository.save(wrongSingleEvent);
        repository.save(inactive);

        List<PurchasePolicy> found = repository.findSpecificForEvent(eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(singleEvent, otherCompanySingleEvent)
                .doesNotContain(multiEvent, companyWide, wrongSingleEvent, inactive);
    }

    @Test
    void nullArguments_areRejected() {
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findActiveByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToEvent(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(null, eventId))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(companyId, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.existsById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findSingleEventPolicies(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findSpecificForEvent(null)).isInstanceOf(NullPointerException.class);
    }

    private static PurchasePolicy inactivePolicy(CompanyId companyId) {
        return PurchasePolicy.newAllowAllPolicy(companyId, "Allow all");
    }

    private static PurchasePolicy eventScopedPolicy(CompanyId companyId, EventId eventId) {
        PurchasePolicy policy = inactivePolicy(companyId);
        policy.activateForEvent(eventId);
        return policy;
    }

    private static PurchasePolicy companyWidePolicy(CompanyId companyId) {
        PurchasePolicy policy = inactivePolicy(companyId);
        policy.setCompanyWide();
        return policy;
    }

    private static PurchasePolicy multiEventPolicy(
            CompanyId companyId,
            EventId firstEventId,
            EventId secondEventId) {
        return new PurchasePolicy(
                PurchasePolicyId.random(),
                companyId,
                "Multi event max tickets",
                PolicyScope.forEvents(Set.of(firstEventId, secondEventId)),
                new MaxTicketPolicy(4));
    }

}
