package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresPurchasePolicyRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresPurchasePolicyRepository.class)
class PostgresPurchasePolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresPurchasePolicyRepository repository;

    @Autowired
    private EntityManager em;

    private CompanyId companyId;
    private EventId eventId;
    private EventId otherEventId;

    @BeforeEach
    void setUp() {
        companyId = new CompanyId("COMP-1");
        eventId = new EventId("EV-1");
        otherEventId = new EventId("EV-2");

        PurchasePolicy inactiveCompanyOwned = PurchasePolicy.companyPolicy(
                companyId,
                "Inactive company-owned",
                PolicyScope.clearScope(),
                new MaxTicketPolicy(5));

        PurchasePolicy activeCompanyWide = PurchasePolicy.companyPolicy(
                companyId,
                "Active company-wide",
                PolicyScope.companyWideScope(),
                new MaxTicketPolicy(5));

        PurchasePolicy activeCompanyOwnedEvent = PurchasePolicy.companyPolicy(
                companyId,
                "Active company-owned event",
                PolicyScope.forSingleEvent(eventId),
                new MaxTicketPolicy(5));

        PurchasePolicy activeEventOwned = PurchasePolicy.eventPolicy(
                companyId,
                eventId,
                "Active event-owned",
                new MaxTicketPolicy(5));

        PurchasePolicy otherEventOwned = PurchasePolicy.eventPolicy(
                companyId,
                otherEventId,
                "Other event-owned",
                new MaxTicketPolicy(5));

        repository.save(inactiveCompanyOwned);
        repository.save(activeCompanyWide);
        repository.save(activeCompanyOwnedEvent);
        repository.save(activeEventOwned);
        repository.save(otherEventOwned);

        em.flush();
        em.clear();
    }

    @Test
    void findMethods_filterByCompanyScopeActivityAndOwnershipCorrectly() {
        List<PurchasePolicy> allCompanyPolicies = repository.findByCompanyId(companyId);

        assertThat(allCompanyPolicies).hasSize(5);

        assertThat(repository.findActiveByCompanyId(companyId))
                .hasSize(4)
                .allMatch(PurchasePolicy::isActive);

        assertThat(repository.findApplicableToPurchase(companyId, eventId))
                .hasSize(3)
                .allMatch(PurchasePolicy::isActive)
                .allSatisfy(policy -> assertThat(policy.scope().appliesTo(eventId)).isTrue());

        assertThat(repository.findApplicableToPurchase(companyId, otherEventId))
                .hasSize(2)
                .allMatch(PurchasePolicy::isActive)
                .allSatisfy(policy -> assertThat(policy.scope().appliesTo(otherEventId)).isTrue());

        assertThat(repository.findCompanyOwnedPolicies(companyId))
                .hasSize(3)
                .allMatch(PurchasePolicy::isCompanyPolicy);

        assertThat(repository.findEventOwnedPolicy(eventId))
                .hasSize(1)
                .allMatch(PurchasePolicy::isEventPolicy)
                .allSatisfy(policy -> assertThat(policy.scope().isListedIn(eventId)).isTrue());

        assertThat(repository.findByEventId(eventId))
                .hasSize(2)
                .allSatisfy(policy -> assertThat(policy.scope().isListedIn(eventId)).isTrue());
    }

    @Test
    void findApplicableToPurchase_shouldNotReturnInactivePolicies() {
        List<PurchasePolicy> applicable = repository.findApplicableToPurchase(companyId, eventId);

        assertThat(applicable)
                .extracting(PurchasePolicy::policyName)
                .containsExactlyInAnyOrder(
                        "Active company-wide",
                        "Active company-owned event",
                        "Active event-owned")
                .doesNotContain("Inactive company-owned");
    }

    @Test
    void findCompanyOwnedPolicies_shouldIncludeInactiveCompanyOwnedAndExcludeEventOwned() {
        List<PurchasePolicy> companyOwned = repository.findCompanyOwnedPolicies(companyId);

        assertThat(companyOwned)
                .extracting(PurchasePolicy::policyName)
                .containsExactlyInAnyOrder(
                        "Inactive company-owned",
                        "Active company-wide",
                        "Active company-owned event")
                .doesNotContain(
                        "Active event-owned",
                        "Other event-owned");

        assertThat(companyOwned).allMatch(PurchasePolicy::isCompanyPolicy);
    }

    @Test
    void findEventOwnedPolicy_shouldReturnOnlyEventOwnedPoliciesForThatEvent() {
        List<PurchasePolicy> eventOwned = repository.findEventOwnedPolicy(eventId);

        assertThat(eventOwned)
                .extracting(PurchasePolicy::policyName)
                .containsExactly("Active event-owned");

        assertThat(eventOwned).allMatch(PurchasePolicy::isEventPolicy);
        assertThat(eventOwned).allSatisfy(policy ->
                assertThat(policy.scope().isListedIn(eventId)).isTrue());
    }

    @Test
    void findByEventId_shouldReturnAllPoliciesExplicitlyListedForEventRegardlessOfOwnership() {
        List<PurchasePolicy> eventPolicies = repository.findByEventId(eventId);

        assertThat(eventPolicies)
                .extracting(PurchasePolicy::policyName)
                .containsExactlyInAnyOrder(
                        "Active company-owned event",
                        "Active event-owned")
                .doesNotContain(
                        "Active company-wide",
                        "Inactive company-owned",
                        "Other event-owned");
    }

    @Test
    void crudOperations_workCorrectly() {
        PurchasePolicyId id = repository.findByCompanyId(companyId).get(0).id();

        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.existsById(id)).isTrue();

        repository.deleteById(id);

        em.flush();
        em.clear();

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsById(id)).isFalse();
    }

    @Test
    void save_null_throwsException() {
        assertThatThrownBy(() -> repository.save(null))
                .isInstanceOf(NullPointerException.class);
    }
}