package com.eventsystem.application.policy;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.policy.PurchasePolicyId;

public interface IPurchasePolicyRepository {

    Optional<PurchasePolicy> findById(PurchasePolicyId policyId);

    List<PurchasePolicy> findByCompanyId(CompanyId companyId);

    List<PurchasePolicy> findActiveByCompanyId(CompanyId companyId);

    List<PurchasePolicy> findApplicableToEvent(EventId eventId);

    List<PurchasePolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId);

    void save(PurchasePolicy discountPolicy);

    void deleteById(PurchasePolicyId policyId);

    boolean existsById(PurchasePolicyId policyId);
    
}