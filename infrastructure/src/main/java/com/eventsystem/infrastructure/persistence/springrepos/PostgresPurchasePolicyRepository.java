package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PostgresPurchasePolicyRepository implements IPurchasePolicyRepository {

    private final SpringDataPurchasePolicyRepository jpaRepository;

    public PostgresPurchasePolicyRepository(SpringDataPurchasePolicyRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "jpaRepository must not be null");
    }

    @SuppressWarnings("null")
    @Override
    public Optional<PurchasePolicy> findById(PurchasePolicyId policyId) {
        return jpaRepository.findById(policyId);
    }

    @Override
    public List<PurchasePolicy> findByCompanyId(CompanyId companyId) {
        return jpaRepository.findByCompanyId(companyId);
    }

    @Override
    public List<PurchasePolicy> findActiveByCompanyId(CompanyId companyId) {
        // סינון בזיכרון לאחר שליפת הפוליסות של החברה
        return jpaRepository.findByCompanyId(companyId).stream()
                .filter(PurchasePolicy::isActive)
                .toList();
    }

    @Override
    public List<PurchasePolicy> findApplicableToEvent(EventId eventId) {
        // שליפת הכל וסינון לפי הלוגיקה של ה-Domain
        return jpaRepository.findAll().stream()
                .filter(policy -> policy.isActiveForEvent(eventId))
                .toList();
    }

    @Override
    public List<PurchasePolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
        return jpaRepository.findByCompanyId(companyId).stream()
                .filter(policy -> policy.isActiveForEvent(eventId))
                .toList();
    }

    @Override
    public List<PurchasePolicy> findSingleEventPolicies(CompanyId companyId) {
        return jpaRepository.findByCompanyId(companyId).stream()
                .filter(PurchasePolicy::isSingleEventPolicy)
                .toList();
    }

    @Override
    public List<PurchasePolicy> findSpecificForEvent(EventId eventId) {
        return jpaRepository.findAll().stream()
                .filter(policy -> policy.isSpecificFor(eventId))
                .toList();
    }

    @Override
    public List<PurchasePolicy> findCompanyOwnedPolicies(CompanyId companyId) {
        return jpaRepository.findByCompanyId(companyId)
                .stream()
                .filter(policy -> policy.companyId().equals(companyId))
                .filter(policy -> policy.isCompanyPolicy())
                .toList();
    }

    @Override
    public List<PurchasePolicy> findEventOwnedPolicy(EventId eventId) {
        return jpaRepository.findAll().stream()
                .filter(policy -> policy.scope().isListedIn(eventId))
                .filter(policy -> policy.isEventPolicy())
                .toList();
    }

    @Override
    public void save(PurchasePolicy purchasePolicy) {
        Objects.requireNonNull(purchasePolicy, "purchasePolicy must not be null");
        jpaRepository.save(purchasePolicy);
    }

    @SuppressWarnings("null")
    @Override
    public void deleteById(PurchasePolicyId policyId) {
        jpaRepository.deleteById(policyId);
    }

    @SuppressWarnings("null")
    @Override
    public boolean existsById(PurchasePolicyId policyId) {
        return jpaRepository.existsById(policyId);
    }

}
