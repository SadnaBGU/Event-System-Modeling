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
        PurchasePolicy policy = eventOwnedPolicy(companyId, eventId);

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
        PurchasePolicy companyOwnedEvent = companyOwnedEventPolicy(companyId, eventId);
        PurchasePolicy companyWide = companyOwnedCompanyWidePolicy(companyId);
        PurchasePolicy eventOwned = eventOwnedPolicy(companyId, eventId);
        PurchasePolicy otherCompany = eventOwnedPolicy(CompanyId.random(), eventId);

        repository.save(companyOwnedEvent);
        repository.save(companyWide);
        repository.save(eventOwned);
        repository.save(otherCompany);

        List<PurchasePolicy> found = repository.findByCompanyId(companyId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedEvent, companyWide, eventOwned)
                .doesNotContain(otherCompany);
    }

    @Test
    void findActiveByCompanyId_returnsOnlyScopedPoliciesOfThatCompany() {
        PurchasePolicy companyOwnedActive = companyOwnedEventPolicy(companyId, eventId);
        PurchasePolicy eventOwnedActive = eventOwnedPolicy(companyId, eventId);
        PurchasePolicy inactive = inactiveCompanyOwnedPolicy(companyId);
        PurchasePolicy otherCompanyActive = eventOwnedPolicy(CompanyId.random(), eventId);

        repository.save(companyOwnedActive);
        repository.save(eventOwnedActive);
        repository.save(inactive);
        repository.save(otherCompanyActive);

        List<PurchasePolicy> found = repository.findActiveByCompanyId(companyId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedActive, eventOwnedActive)
                .doesNotContain(inactive, otherCompanyActive);
    }

    @Test
    void findByEventId_returnsPoliciesListedForEventRegardlessOfOwnerOrActiveState() {
        PurchasePolicy companyOwnedEvent = companyOwnedEventPolicy(companyId, eventId);
        PurchasePolicy eventOwned = eventOwnedPolicy(companyId, eventId);
        PurchasePolicy inactiveListed = PurchasePolicy.companyPolicy(
                companyId,
                "Inactive listed",
                PolicyScope.forSingleEvent(eventId),
                new MaxTicketPolicy(4));
        PurchasePolicy companyWide = companyOwnedCompanyWidePolicy(companyId);
        PurchasePolicy wrongEvent = companyOwnedEventPolicy(companyId, EventId.random());

        repository.save(companyOwnedEvent);
        repository.save(eventOwned);
        repository.save(inactiveListed);
        repository.save(companyWide);
        repository.save(wrongEvent);

        List<PurchasePolicy> found = repository.findByEventId(eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedEvent, eventOwned, inactiveListed)
                .doesNotContain(companyWide, wrongEvent);
    }

    @Test
    void findApplicableToPurchase_filtersByActiveScopeCompanyAndEvent() {
        PurchasePolicy companyOwnedEventMatch = companyOwnedEventPolicy(companyId, eventId);
        PurchasePolicy companyWideMatch = companyOwnedCompanyWidePolicy(companyId);
        PurchasePolicy eventOwnedMatch = eventOwnedPolicy(companyId, eventId);

        PurchasePolicy wrongEvent = companyOwnedEventPolicy(companyId, EventId.random());
        PurchasePolicy wrongCompanySameEvent = eventOwnedPolicy(CompanyId.random(), eventId);
        PurchasePolicy inactive = inactiveCompanyOwnedPolicy(companyId);

        repository.save(companyOwnedEventMatch);
        repository.save(companyWideMatch);
        repository.save(eventOwnedMatch);
        repository.save(wrongEvent);
        repository.save(wrongCompanySameEvent);
        repository.save(inactive);

        List<PurchasePolicy> found = repository.findApplicableToPurchase(companyId, eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedEventMatch, companyWideMatch, eventOwnedMatch)
                .doesNotContain(wrongEvent, wrongCompanySameEvent, inactive);
    }

    @Test
    void deleteById_removesPolicy() {
        PurchasePolicy policy = eventOwnedPolicy(companyId, eventId);
        repository.save(policy);

        repository.deleteById(policy.id());

        assertThat(repository.findById(policy.id())).isEmpty();
        assertThat(repository.existsById(policy.id())).isFalse();
    }

    @Test
    void findCompanyOwnedPolicies_returnsOnlyCompanyOwnedPoliciesOfCompany() {
        PurchasePolicy companyOwnedEvent = companyOwnedEventPolicy(companyId, eventId);
        PurchasePolicy companyWide = companyOwnedCompanyWidePolicy(companyId);
        PurchasePolicy inactiveCompanyOwned = inactiveCompanyOwnedPolicy(companyId);

        PurchasePolicy eventOwned = eventOwnedPolicy(companyId, eventId);
        PurchasePolicy otherCompanyOwned = companyOwnedEventPolicy(CompanyId.random(), eventId);

        repository.save(companyOwnedEvent);
        repository.save(companyWide);
        repository.save(inactiveCompanyOwned);
        repository.save(eventOwned);
        repository.save(otherCompanyOwned);

        List<PurchasePolicy> found = repository.findCompanyOwnedPolicies(companyId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedEvent, companyWide, inactiveCompanyOwned)
                .doesNotContain(eventOwned, otherCompanyOwned);
    }

    @Test
    void findEventOwnedPolicy_returnsOnlyEventOwnedPoliciesForEvent() {
        EventId otherEventId = EventId.random();

        PurchasePolicy eventOwned = eventOwnedPolicy(companyId, eventId);
        PurchasePolicy eventOwnedOtherCompany = eventOwnedPolicy(CompanyId.random(), eventId);
        PurchasePolicy eventOwnedOtherEvent = eventOwnedPolicy(companyId, otherEventId);

        PurchasePolicy companyOwnedSingleEvent = companyOwnedEventPolicy(companyId, eventId);
        PurchasePolicy companyWide = companyOwnedCompanyWidePolicy(companyId);

        repository.save(eventOwned);
        repository.save(eventOwnedOtherCompany);
        repository.save(eventOwnedOtherEvent);
        repository.save(companyOwnedSingleEvent);
        repository.save(companyWide);

        List<PurchasePolicy> found = repository.findEventOwnedPolicy(eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(eventOwned, eventOwnedOtherCompany)
                .doesNotContain(eventOwnedOtherEvent, companyOwnedSingleEvent, companyWide);
    }

    @Test
    void nullArguments_areRejected() {
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findActiveByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(null, eventId)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(companyId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findCompanyOwnedPolicies(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findEventOwnedPolicy(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByEventId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.existsById(null)).isInstanceOf(NullPointerException.class);
    }

    private static PurchasePolicy inactiveCompanyOwnedPolicy(CompanyId companyId) {
        return PurchasePolicy.companyPolicy(
                companyId,
                "Inactive company policy",
                PolicyScope.clearScope(),
                new MaxTicketPolicy(4));
    }

    private static PurchasePolicy companyOwnedEventPolicy(CompanyId companyId, EventId eventId) {
        return PurchasePolicy.companyPolicy(
                companyId,
                "Company-owned event policy",
                PolicyScope.forSingleEvent(eventId),
                new MaxTicketPolicy(4));
    }

    private static PurchasePolicy companyOwnedCompanyWidePolicy(CompanyId companyId) {
        return PurchasePolicy.companyPolicy(
                companyId,
                "Company-wide policy",
                PolicyScope.companyWideScope(),
                new MaxTicketPolicy(4));
    }

    private static PurchasePolicy eventOwnedPolicy(CompanyId companyId, EventId eventId) {
        return PurchasePolicy.eventPolicy(
                companyId,
                eventId,
                "Event-owned policy",
                new MaxTicketPolicy(4));
    }

    private static PurchasePolicy companyOwnedMultiEventPolicy(
            CompanyId companyId,
            EventId firstEventId,
            EventId secondEventId) {
        return PurchasePolicy.companyPolicy(
                companyId,
                "Multi-event company policy",
                PolicyScope.forEvents(Set.of(firstEventId, secondEventId)),
                new MaxTicketPolicy(4));
    }
}
