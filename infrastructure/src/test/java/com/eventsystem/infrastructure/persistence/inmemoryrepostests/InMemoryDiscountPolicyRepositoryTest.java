package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryDiscountPolicyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InMemoryDiscountPolicyRepositoryTest {

    private InMemoryDiscountPolicyRepository repository;
    private CompanyId companyId;
    private EventId eventId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDiscountPolicyRepository();
        companyId = CompanyId.random();
        eventId = EventId.random();
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertThat(repository.findById(DiscountPolicyId.random())).isEmpty();
    }

    @Test
    void save_thenFindById_andExistsById_work() {
        DiscountPolicy policy = inactiveEventOwnedPolicy(companyId, eventId);

        repository.save(policy);

        assertThat(repository.findById(policy.id())).contains(policy);
        assertThat(repository.existsById(policy.id())).isTrue();
    }

    @Test
    void findByCompanyId_returnsOnlyMatchingCompanyPolicies() {
        DiscountPolicy p1 = inactiveEventOwnedPolicy(companyId, eventId);
        DiscountPolicy p2 = activeEventOwnedPolicy(companyId, EventId.random());
        DiscountPolicy other = activeEventOwnedPolicy(CompanyId.random(), eventId);

        repository.save(p1);
        repository.save(p2);
        repository.save(other);

        List<DiscountPolicy> found = repository.findByCompanyId(companyId);
        assertThat(found).containsExactlyInAnyOrder(p1, p2);
    }

    @Test
    void findActiveByCompanyId_returnsOnlyActivePoliciesForCompany() {
        DiscountPolicy active = activeEventOwnedPolicy(companyId, eventId);
        DiscountPolicy inactive = inactiveEventOwnedPolicy(companyId, EventId.random());
        DiscountPolicy otherCompanyActive = activeEventOwnedPolicy(CompanyId.random(), eventId);

        repository.save(active);
        repository.save(inactive);
        repository.save(otherCompanyActive);

        List<DiscountPolicy> found = repository.findActiveByCompanyId(companyId);
        assertThat(found).containsExactly(active);
    }

    @Test
    void findApplicableToPurchase_filtersByActiveCompanyAndEvent() {
        DiscountPolicy companyOwnedEventMatch = activeCompanyOwnedPolicyForEvent(companyId, eventId);
        DiscountPolicy companyWideMatch = activeCompanyWidePolicy(companyId);
        DiscountPolicy eventOwnedMatch = activeEventOwnedPolicy(companyId, eventId);

        DiscountPolicy wrongEvent = activeCompanyOwnedPolicyForEvent(companyId, EventId.random());
        DiscountPolicy inactive = inactiveCompanyOwnedPolicyForEvent(companyId, eventId);
        DiscountPolicy wrongCompany = activeEventOwnedPolicy(CompanyId.random(), eventId);

        repository.save(companyOwnedEventMatch);
        repository.save(companyWideMatch);
        repository.save(eventOwnedMatch);
        repository.save(wrongEvent);
        repository.save(inactive);
        repository.save(wrongCompany);

        List<DiscountPolicy> found = repository.findApplicableToPurchase(companyId, eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedEventMatch, companyWideMatch, eventOwnedMatch)
                .doesNotContain(wrongEvent, inactive, wrongCompany);
    }

    @Test
    void deleteById_removesPolicy() {
        DiscountPolicy policy = activeEventOwnedPolicy(companyId, eventId);
        repository.save(policy);

        repository.deleteById(policy.id());

        assertThat(repository.findById(policy.id())).isEmpty();
        assertThat(repository.existsById(policy.id())).isFalse();
    }

    @Test
    void findActive_returnsOnlyActivePolicies() {
        DiscountPolicy activeForCompany = activeEventOwnedPolicy(companyId, eventId);
        DiscountPolicy activeForOtherCompany = activeEventOwnedPolicy(CompanyId.random(), EventId.random());
        DiscountPolicy inactive = inactiveEventOwnedPolicy(companyId, EventId.random());

        repository.save(activeForCompany);
        repository.save(activeForOtherCompany);
        repository.save(inactive);

        List<DiscountPolicy> found = repository.findAllActive();

        assertThat(found)
                .containsExactlyInAnyOrder(activeForCompany, activeForOtherCompany)
                .doesNotContain(inactive);
    }

    @Test
    void findActive_returnsEmptyListWhenNoActivePoliciesExist() {
        DiscountPolicy inactive1 = inactiveEventOwnedPolicy(companyId, eventId);
        DiscountPolicy inactive2 = inactiveEventOwnedPolicy(CompanyId.random(), EventId.random());

        repository.save(inactive1);
        repository.save(inactive2);

        List<DiscountPolicy> found = repository.findAllActive();

        assertThat(found).isEmpty();
    }

    @Test
    void findByEventId_returnsPoliciesListedForEventRegardlessOfOwnerOrActiveState() {
        DiscountPolicy companyOwnedEvent = activeCompanyOwnedPolicyForEvent(companyId, eventId);
        DiscountPolicy eventOwned = activeEventOwnedPolicy(companyId, eventId);
        DiscountPolicy inactiveEventOwned = inactiveEventOwnedPolicy(companyId, eventId);
        DiscountPolicy companyWide = activeCompanyWidePolicy(companyId);
        DiscountPolicy wrongEvent = activeCompanyOwnedPolicyForEvent(companyId, EventId.random());

        repository.save(companyOwnedEvent);
        repository.save(eventOwned);
        repository.save(inactiveEventOwned);
        repository.save(companyWide);
        repository.save(wrongEvent);

        List<DiscountPolicy> found = repository.findByEventId(eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(companyOwnedEvent, eventOwned, inactiveEventOwned)
                .doesNotContain(companyWide, wrongEvent);
    }

    @Test
    void findActiveWithVisibleDiscounts_shouldReturnOnlyActivePoliciesWithVisibleNonExpiredDiscounts() {
        DiscountPolicy activeVisible = DiscountPolicy.inactiveCompanyWide(companyId);
        activeVisible.addDiscount(new Discount(
                "Visible",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                true,
                LocalDate.now().plusDays(1)));
        activeVisible.activate();

        DiscountPolicy inactiveVisible = DiscountPolicy.inactiveCompanyWide(companyId);
        inactiveVisible.addDiscount(new Discount(
                "Inactive visible",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                true,
                LocalDate.now().plusDays(1)));

        DiscountPolicy activeHidden = DiscountPolicy.inactiveCompanyWide(companyId);
        activeHidden.addDiscount(new Discount(
                "Hidden",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                false,
                LocalDate.now().plusDays(1)));
        activeHidden.activate();

        DiscountPolicy activeExpiredVisible = DiscountPolicy.inactiveCompanyWide(companyId);
        activeExpiredVisible.addDiscount(new Discount(
                "Expired visible",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                true,
                LocalDate.now().minusDays(1)));
        activeExpiredVisible.activate();

        repository.save(activeVisible);
        repository.save(inactiveVisible);
        repository.save(activeHidden);
        repository.save(activeExpiredVisible);

        assertThat(repository.findActiveWithVisibleDiscounts())
                .containsExactly(activeVisible);
    }
@Test
void findCompanyOwnedPolicies_returnsOnlyCompanyOwnedPoliciesOfCompany() {
    DiscountPolicy companyOwnedCompanyWide = activeCompanyWidePolicy(companyId);
    DiscountPolicy companyOwnedEventScoped = activeCompanyOwnedPolicyForEvent(companyId, eventId);
    DiscountPolicy inactiveCompanyOwned = DiscountPolicy.clearScope(companyId);

    DiscountPolicy eventOwnedSameCompany = activeEventOwnedPolicy(companyId, eventId);
    DiscountPolicy otherCompanyCompanyOwned = activeCompanyOwnedPolicyForEvent(CompanyId.random(), eventId);

    repository.save(companyOwnedCompanyWide);
    repository.save(companyOwnedEventScoped);
    repository.save(inactiveCompanyOwned);
    repository.save(eventOwnedSameCompany);
    repository.save(otherCompanyCompanyOwned);

    List<DiscountPolicy> found = repository.findCompanyOwnedPolicies(companyId);

    assertThat(found)
            .containsExactlyInAnyOrder(
                    companyOwnedCompanyWide,
                    companyOwnedEventScoped,
                    inactiveCompanyOwned)
            .doesNotContain(eventOwnedSameCompany, otherCompanyCompanyOwned);
}

@Test
void findEventOwnedPolicy_returnsOnlyEventOwnedPoliciesForEvent() {
    EventId otherEventId = EventId.random();

    DiscountPolicy eventOwnedActive = activeEventOwnedPolicy(companyId, eventId);
    DiscountPolicy eventOwnedInactive = inactiveEventOwnedPolicy(companyId, eventId);
    DiscountPolicy eventOwnedOtherEvent = activeEventOwnedPolicy(companyId, otherEventId);

    DiscountPolicy companyOwnedSingleEvent = activeCompanyOwnedPolicyForEvent(companyId, eventId);
    DiscountPolicy companyWide = activeCompanyWidePolicy(companyId);

    repository.save(eventOwnedActive);
    repository.save(eventOwnedInactive);
    repository.save(eventOwnedOtherEvent);
    repository.save(companyOwnedSingleEvent);
    repository.save(companyWide);

    List<DiscountPolicy> found = repository.findEventOwnedPolicy(eventId);

    assertThat(found)
            .containsExactlyInAnyOrder(eventOwnedActive, eventOwnedInactive)
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

    private static DiscountPolicy inactiveCompanyOwnedPolicyForEvent(CompanyId companyId, EventId eventId) {
        return DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.forSingleEvent(eventId),
                List.of(mock(Discount.class)),
                false,
                false);
    }

    private static DiscountPolicy activeCompanyOwnedPolicyForEvent(CompanyId companyId, EventId eventId) {
        return DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.forSingleEvent(eventId),
                List.of(mock(Discount.class)),
                false,
                true);
    }

    private static DiscountPolicy activeEventOwnedPolicy(CompanyId companyId, EventId eventId) {
        return DiscountPolicy.eventPolicy(
                companyId,
                eventId,
                List.of(mock(Discount.class)),
                false,
                true);
    }

    private static DiscountPolicy inactiveEventOwnedPolicy(CompanyId companyId, EventId eventId) {
        return DiscountPolicy.eventPolicy(
                companyId,
                eventId,
                List.of(mock(Discount.class)),
                false,
                false);
    }

    private static DiscountPolicy activeCompanyWidePolicy(CompanyId companyId) {
        return DiscountPolicy.companyPolicy(
                companyId,
                PolicyScope.companyWideScope(),
                List.of(mock(Discount.class)),
                false,
                true);
    }


}
