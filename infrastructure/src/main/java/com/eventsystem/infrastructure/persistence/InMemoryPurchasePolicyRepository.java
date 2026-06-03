package com.eventsystem.infrastructure.persistence;

import com.eventsystem.application.policy.IPurchasePolicyRepository;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.company.CompanyId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPurchasePolicyRepository implements IPurchasePolicyRepository {

    private final Map<PurchasePolicyId, PurchasePolicy> policiesById = new ConcurrentHashMap<>();

    @Override
    public Optional<PurchasePolicy> findById(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return Optional.ofNullable(policiesById.get(policyId));
    }

    @Override
    public List<PurchasePolicy> findByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return policiesById.values()
                .stream()
                .filter(policy -> policy.companyId().equals(companyId))
                .toList();
    }

    @Override
    public List<PurchasePolicy> findActiveByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        return policiesById.values()
                .stream()
                .filter(PurchasePolicy::isActive)
                .filter(policy -> policy.companyId().equals(companyId))
                .toList();
    }

    @Override
    public List<PurchasePolicy> findApplicableToEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        return policiesById.values()
                .stream()
                .filter(PurchasePolicy::isActive)
                .filter(policy -> policy.scope().appliesTo(eventId))
                .toList();
    }

    @Override
    public List<PurchasePolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        return policiesById.values()
                .stream()
                .filter(PurchasePolicy::isActive)
                .filter(policy -> policy.companyId().equals(companyId))
                .filter(policy -> policy.scope().appliesTo(eventId))
                .toList();
    }

    @Override
    public void save(PurchasePolicy purchasePolicy) {
        Objects.requireNonNull(purchasePolicy, "PurchasePolicy must not be null");

        policiesById.put(purchasePolicy.id(), purchasePolicy);
    }

    @Override
    public void deleteById(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        policiesById.remove(policyId);
    }

    @Override
    public boolean existsById(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return policiesById.containsKey(policyId);
    }
}
