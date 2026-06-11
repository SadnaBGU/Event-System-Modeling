package com.eventsystem.infrastructure.persistence.inmemoryrepostests;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
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
        DiscountPolicy policy = inactivePolicyForEvent(companyId, eventId);

        repository.save(policy);

        assertThat(repository.findById(policy.id())).contains(policy);
        assertThat(repository.existsById(policy.id())).isTrue();
    }

    @Test
    void findByCompanyId_returnsOnlyMatchingCompanyPolicies() {
        DiscountPolicy p1 = inactivePolicyForEvent(companyId, eventId);
        DiscountPolicy p2 = activePolicyForEvent(companyId, EventId.random());
        DiscountPolicy other = activePolicyForEvent(CompanyId.random(), eventId);

        repository.save(p1);
        repository.save(p2);
        repository.save(other);

        List<DiscountPolicy> found = repository.findByCompanyId(companyId);
        assertThat(found).containsExactlyInAnyOrder(p1, p2);
    }

    @Test
    void findActiveByCompanyId_returnsOnlyActivePoliciesForCompany() {
        DiscountPolicy active = activePolicyForEvent(companyId, eventId);
        DiscountPolicy inactive = inactivePolicyForEvent(companyId, EventId.random());
        DiscountPolicy otherCompanyActive = activePolicyForEvent(CompanyId.random(), eventId);

        repository.save(active);
        repository.save(inactive);
        repository.save(otherCompanyActive);

        List<DiscountPolicy> found = repository.findActiveByCompanyId(companyId);
        assertThat(found).containsExactly(active);
    }

    @Test
    void findApplicableToPurchase_filtersByActiveCompanyAndEvent() {
        DiscountPolicy match = activePolicyForEvent(companyId, eventId);
        DiscountPolicy wrongEvent = activePolicyForEvent(companyId, EventId.random());
        DiscountPolicy inactive = inactivePolicyForEvent(companyId, eventId);
        DiscountPolicy wrongCompany = activePolicyForEvent(CompanyId.random(), eventId);

        repository.save(match);
        repository.save(wrongEvent);
        repository.save(inactive);
        repository.save(wrongCompany);

        List<DiscountPolicy> found = repository.findApplicableToPurchase(companyId, eventId);
        assertThat(found).containsExactly(match);
    }

    @Test
    void deleteById_removesPolicy() {
        DiscountPolicy policy = activePolicyForEvent(companyId, eventId);
        repository.save(policy);

        repository.deleteById(policy.id());

        assertThat(repository.findById(policy.id())).isEmpty();
        assertThat(repository.existsById(policy.id())).isFalse();
    }

    @Test
    void findActive_returnsOnlyActivePolicies() {
        DiscountPolicy activeForCompany = activePolicyForEvent(companyId, eventId);
        DiscountPolicy activeForOtherCompany = activePolicyForEvent(CompanyId.random(), EventId.random());
        DiscountPolicy inactive = inactivePolicyForEvent(companyId, EventId.random());

        repository.save(activeForCompany);
        repository.save(activeForOtherCompany);
        repository.save(inactive);

        List<DiscountPolicy> found = repository.findActive();

        assertThat(found)
                .containsExactlyInAnyOrder(activeForCompany, activeForOtherCompany)
                .doesNotContain(inactive);
    }

    @Test
    void findActive_returnsEmptyListWhenNoActivePoliciesExist() {
        DiscountPolicy inactive1 = inactivePolicyForEvent(companyId, eventId);
        DiscountPolicy inactive2 = inactivePolicyForEvent(CompanyId.random(), EventId.random());

        repository.save(inactive1);
        repository.save(inactive2);

        List<DiscountPolicy> found = repository.findActive();

        assertThat(found).isEmpty();
    }

    @Test
    void findApplicableToEvent_returnsActiveCompanyWideAndEventScopedPolicies() {
        DiscountPolicy eventScopedMatch = activePolicyForEvent(companyId, eventId);
        DiscountPolicy companyWideMatch = activeCompanyWidePolicy(companyId);
        DiscountPolicy wrongEvent = activePolicyForEvent(companyId, EventId.random());
        DiscountPolicy inactiveForEvent = inactivePolicyForEvent(companyId, eventId);

        repository.save(eventScopedMatch);
        repository.save(companyWideMatch);
        repository.save(wrongEvent);
        repository.save(inactiveForEvent);

        List<DiscountPolicy> found = repository.findApplicableToEvent(eventId);

        assertThat(found)
                .containsExactlyInAnyOrder(eventScopedMatch, companyWideMatch)
                .doesNotContain(wrongEvent, inactiveForEvent);
    }

    @Test
    void findApplicableToEvent_returnsEmptyListWhenNoActivePolicyAppliesToEvent() {
        DiscountPolicy wrongEvent = activePolicyForEvent(companyId, EventId.random());
        DiscountPolicy inactiveForEvent = inactivePolicyForEvent(companyId, eventId);

        repository.save(wrongEvent);
        repository.save(inactiveForEvent);

        List<DiscountPolicy> found = repository.findApplicableToEvent(eventId);

        assertThat(found).isEmpty();
    }

    @Test
    void findApplicableToEvent_requiresNonNullEventId() {
        assertThatThrownBy(() -> repository.findApplicableToEvent(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findActiveWithVisibleDiscounts_shouldReturnOnlyActivePoliciesWithVisibleNonExpiredDiscounts() {
        DiscountPolicy activeVisible = DiscountPolicy.inactiveCompanyWide(companyId);
        activeVisible.addDiscount(new Discount(
                "Visible",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                true,
                LocalDate.now().plusDays(1)
        ));
        activeVisible.activate();

        DiscountPolicy inactiveVisible = DiscountPolicy.inactiveCompanyWide(companyId);
        inactiveVisible.addDiscount(new Discount(
                "Inactive visible",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                true,
                LocalDate.now().plusDays(1)
        ));

        DiscountPolicy activeHidden = DiscountPolicy.inactiveCompanyWide(companyId);
        activeHidden.addDiscount(new Discount(
                "Hidden",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                false,
                LocalDate.now().plusDays(1)
        ));
        activeHidden.activate();

        DiscountPolicy activeExpiredVisible = DiscountPolicy.inactiveCompanyWide(companyId);
        activeExpiredVisible.addDiscount(new Discount(
                "Expired visible",
                BigDecimal.TEN,
                AlwaysTruePolicy.INSTANCE,
                true,
                LocalDate.now().minusDays(1)
        ));
        activeExpiredVisible.activate();

        repository.save(activeVisible);
        repository.save(inactiveVisible);
        repository.save(activeHidden);
        repository.save(activeExpiredVisible);

        assertThat(repository.findActiveWithVisibleDiscounts())
                .containsExactly(activeVisible);
    }

    @Test
    void nullArguments_areRejected() {
        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findActiveByCompanyId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(null, eventId)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.findApplicableToPurchase(companyId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.deleteById(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> repository.existsById(null)).isInstanceOf(NullPointerException.class);
    }

    private static DiscountPolicy inactivePolicyForEvent(CompanyId companyId, EventId eventId) {
        return DiscountPolicy.inactiveForSingleEvent(companyId, eventId);
    }

    private static DiscountPolicy activePolicyForEvent(CompanyId companyId, EventId eventId) {
        DiscountPolicy policy = DiscountPolicy.inactiveForSingleEvent(companyId, eventId);
        policy.addDiscount(mock(Discount.class));
        policy.activate();
        return policy;
    }

    private static DiscountPolicy activeCompanyWidePolicy(CompanyId companyId) {
        DiscountPolicy policy = DiscountPolicy.inactiveCompanyWide(companyId);
        policy.addDiscount(mock(Discount.class));
        policy.activate();
        return policy;
    }
}
