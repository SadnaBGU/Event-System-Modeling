package com.eventsystem.infrastructure.persistence.springrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresDiscountPolicyRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.eventsystem.domain")
@Import(PostgresDiscountPolicyRepository.class)
class PostgresDiscountPolicyRepositoryTest extends BasePostgresTest {

    @Autowired
    private PostgresDiscountPolicyRepository repository;

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

        DiscountPolicy inactiveCompanyOwned = DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.companyWideScope(),
                List.of(Discount.GeneralDiscount("Inactive company-owned", BigDecimal.valueOf(5), null)),
                false,
                false);

        DiscountPolicy activeCompanyWide = DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.companyWideScope(),
                List.of(Discount.GeneralDiscount("Active company-wide", BigDecimal.valueOf(5), null)),
                false,
                true);

        DiscountPolicy activeCompanyOwnedEvent = DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.forSingleEvent(eventId),
                List.of(Discount.GeneralDiscount("Active company-owned event", BigDecimal.TEN, null)),
                false,
                true);

        DiscountPolicy activeEventOwned = DiscountPolicy.eventPolicy(
                companyId,
                eventId,
                List.of(Discount.GeneralDiscount("Active event-owned", BigDecimal.valueOf(15), null)),
                false,
                true);

        DiscountPolicy inactiveEventOwned = DiscountPolicy.eventPolicy(
                companyId,
                eventId,
                List.of(Discount.GeneralDiscount("Inactive event-owned", BigDecimal.valueOf(20), null)),
                false,
                false);

        DiscountPolicy otherEventOwned = DiscountPolicy.eventPolicy(
                companyId,
                otherEventId,
                List.of(Discount.GeneralDiscount("Other event-owned", BigDecimal.valueOf(25), null)),
                false,
                true);

        repository.save(inactiveCompanyOwned);
        repository.save(activeCompanyWide);
        repository.save(activeCompanyOwnedEvent);
        repository.save(activeEventOwned);
        repository.save(inactiveEventOwned);
        repository.save(otherEventOwned);

        em.flush();
        em.clear();
    }

    @Test
    void findMethods_filterByCompanyScopeActivityVisibilityAndOwnershipCorrectly() {
        assertThat(repository.findByCompanyId(companyId))
                .hasSize(6);

        assertThat(repository.findAllActive())
                .hasSize(4)
                .allMatch(DiscountPolicy::isActive);

        assertThat(repository.findActiveWithVisibleDiscounts())
                .hasSize(4)
                .allMatch(DiscountPolicy::isActive)
                .allMatch(DiscountPolicy::doesHaveVisibleDiscounts);

        assertThat(repository.findActiveByCompanyId(companyId))
                .hasSize(4)
                .allMatch(DiscountPolicy::isActive);

        assertThat(repository.findApplicableToPurchase(companyId, eventId))
                .hasSize(3)
                .allMatch(DiscountPolicy::isActive)
                .allSatisfy(policy -> assertThat(policy.scope().appliesTo(eventId)).isTrue());

        assertThat(repository.findApplicableToPurchase(companyId, otherEventId))
                .hasSize(2)
                .allMatch(DiscountPolicy::isActive)
                .allSatisfy(policy -> assertThat(policy.scope().appliesTo(otherEventId)).isTrue());

        assertThat(repository.findCompanyOwnedPolicies(companyId))
                .hasSize(3)
                .allMatch(DiscountPolicy::isCompanyPolicy);

        assertThat(repository.findEventOwnedPolicy(eventId))
                .hasSize(2)
                .allMatch(DiscountPolicy::isEventPolicy)
                .allSatisfy(policy -> assertThat(policy.scope().isListedIn(eventId)).isTrue());

        assertThat(repository.findByEventId(eventId))
                .hasSize(3)
                .allSatisfy(policy -> assertThat(policy.scope().isListedIn(eventId)).isTrue());
    }

    @Test
    void findApplicableToPurchase_shouldReturnOnlyActivePoliciesApplicableToThatCompanyAndEvent() {
        List<DiscountPolicy> applicable = repository.findApplicableToPurchase(companyId, eventId);

        assertThat(applicable)
                .flatExtracting(policy -> policy.discounts()
                        .stream()
                        .map(Discount::getDiscountName)
                        .toList())
                .containsExactlyInAnyOrder(
                        "Active company-wide",
                        "Active company-owned event",
                        "Active event-owned")
                .doesNotContain(
                        "Inactive company-owned",
                        "Inactive event-owned",
                        "Other event-owned");
    }

    @Test
    void findCompanyOwnedPolicies_shouldIncludeInactiveCompanyOwnedAndExcludeEventOwned() {
        List<DiscountPolicy> companyOwned = repository.findCompanyOwnedPolicies(companyId);

        assertThat(companyOwned)
                .flatExtracting(policy -> policy.discounts()
                        .stream()
                        .map(Discount::getDiscountName)
                        .toList())
                .containsExactlyInAnyOrder(
                        "Inactive company-owned",
                        "Active company-wide",
                        "Active company-owned event")
                .doesNotContain(
                        "Active event-owned",
                        "Inactive event-owned",
                        "Other event-owned");

        assertThat(companyOwned).allMatch(DiscountPolicy::isCompanyPolicy);
    }

    @Test
    void findEventOwnedPolicy_shouldReturnOnlyEventOwnedPoliciesForThatEventIncludingInactiveOnes() {
        List<DiscountPolicy> eventOwned = repository.findEventOwnedPolicy(eventId);

        assertThat(eventOwned)
                .flatExtracting(policy -> policy.discounts()
                        .stream()
                        .map(Discount::getDiscountName)
                        .toList())
                .containsExactlyInAnyOrder(
                        "Active event-owned",
                        "Inactive event-owned")
                .doesNotContain(
                        "Active company-owned event",
                        "Active company-wide",
                        "Other event-owned");

        assertThat(eventOwned).allMatch(DiscountPolicy::isEventPolicy);
        assertThat(eventOwned).allSatisfy(policy ->
                assertThat(policy.scope().isListedIn(eventId)).isTrue());
    }

    @Test
    void findByEventId_shouldReturnAllPoliciesExplicitlyListedForEventRegardlessOfOwnershipOrActiveState() {
        List<DiscountPolicy> eventPolicies = repository.findByEventId(eventId);

        assertThat(eventPolicies)
                .flatExtracting(policy -> policy.discounts()
                        .stream()
                        .map(Discount::getDiscountName)
                        .toList())
                .containsExactlyInAnyOrder(
                        "Active company-owned event",
                        "Active event-owned",
                        "Inactive event-owned")
                .doesNotContain(
                        "Active company-wide",
                        "Inactive company-owned",
                        "Other event-owned");
    }

    @Test
    void crudOperations_workCorrectly() {
        DiscountPolicyId id = repository.findByCompanyId(companyId).get(0).id();

        assertThat(repository.findById(id)).isPresent();
        assertThat(repository.existsById(id)).isTrue();

        repository.deleteById(id);

        em.flush();
        em.clear();

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsById(id)).isFalse();
    }
}