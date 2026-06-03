package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void nullArguments_areRejected() {
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findActiveByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToEvent(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(null, eventId)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(companyId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.existsById(null)).isInstanceOf(NullPointerException.class);
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
}
