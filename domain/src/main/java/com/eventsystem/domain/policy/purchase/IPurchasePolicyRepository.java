package com.eventsystem.domain.policy.purchase;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;

public interface IPurchasePolicyRepository {

    Optional<PurchasePolicy> findById(PurchasePolicyId policyId);

    List<PurchasePolicy> findByCompanyId(CompanyId companyId);

    List<PurchasePolicy> findByEventId(EventId eventId);
    
    List<PurchasePolicy> findCompanyOwnedPolicies(CompanyId companyId);

    List<PurchasePolicy> findEventOwnedPolicy(EventId eventId);

    List<PurchasePolicy> findActiveByCompanyId(CompanyId companyId);

    List<PurchasePolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId);

    void save(PurchasePolicy discountPolicy);

    void deleteById(PurchasePolicyId policyId);

    boolean existsById(PurchasePolicyId policyId);

}