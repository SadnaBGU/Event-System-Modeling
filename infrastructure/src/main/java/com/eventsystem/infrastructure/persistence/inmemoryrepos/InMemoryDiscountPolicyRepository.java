package com.eventsystem.infrastructure.persistence.inmemoryrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.discount.IDiscountPolicyRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class InMemoryDiscountPolicyRepository implements IDiscountPolicyRepository {

    private final Map<DiscountPolicyId, DiscountPolicy> policiesById = new ConcurrentHashMap<>();

    @Override
    public Optional<DiscountPolicy> findById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return Optional.ofNullable(policiesById.get(policyId));
    }

    @Override
    public List<DiscountPolicy> findByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return policiesById.values()
                .stream()
                .filter(policy -> policy.companyId().equals(companyId))
                .toList();
    }

    @Override
    public List<DiscountPolicy> findActive() {
        return policiesById.values()
                .stream()
                .filter(DiscountPolicy::isActive)
                .toList();
    }

    @Override
    public List<DiscountPolicy> findActiveWithVisibleDiscounts() {
            return policiesById.values()
                .stream()
                .filter(DiscountPolicy::isActive)
                .filter(DiscountPolicy::doesHaveVisibleDiscounts)
                .toList();
    }


    @Override
    public List<DiscountPolicy> findActiveByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return policiesById.values()
                .stream()
                .filter(DiscountPolicy::isActive)
                .filter(policy -> policy.companyId().equals(companyId))
                .toList();
    }

    @Override
    public List<DiscountPolicy> findApplicableToEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return policiesById.values()
                .stream()
                .filter(DiscountPolicy::isActive)
                .filter(policy -> policy.scope().appliesTo(eventId))
                .toList();
    }

    @Override
    public List<DiscountPolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        return policiesById.values()
                .stream()
                .filter(DiscountPolicy::isActive)
                .filter(policy -> policy.companyId().equals(companyId))
                .filter(policy -> policy.scope().appliesTo(eventId))
                .toList();
    }

    @Override
    public void save(DiscountPolicy discountPolicy) {
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

        policiesById.put(discountPolicy.id(), discountPolicy);
    }

    @Override
    public void deleteById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        policiesById.remove(policyId);
    }

    @Override
    public boolean existsById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return policiesById.containsKey(policyId);
    }

    @Override
    public List<DiscountPolicy> findSingleEventPolicies(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return policiesById.values()
                .stream()
                .filter(policy -> policy.companyId().equals(companyId))
                .filter(policy -> policy.scope().isForSingleEvent())
                .toList();
    }

    @Override
    public List<DiscountPolicy> findSpecificForEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return policiesById.values()
                .stream()
                .filter(policy -> policy.scope().appliesTo(eventId))
                .filter(policy -> policy.scope().isForSingleEvent())
                .toList();
    }
}