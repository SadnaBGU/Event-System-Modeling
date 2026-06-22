package com.eventsystem.domain.policy.discount;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;

import java.util.Optional;
import java.util.List;

public interface IDiscountPolicyRepository {

    Optional<DiscountPolicy> findById(DiscountPolicyId policyId);

    List<DiscountPolicy> findByCompanyId(CompanyId companyId);

    List<DiscountPolicy> findByEventId(EventId eventId);

    List<DiscountPolicy> findCompanyOwnedPolicies(CompanyId companyId);

    List<DiscountPolicy> findEventOwnedPolicy(EventId eventId);

    List<DiscountPolicy> findAllActive();

    List<DiscountPolicy> findActiveWithVisibleDiscounts();

    List<DiscountPolicy> findActiveByCompanyId(CompanyId companyId);

    List<DiscountPolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId);


    void save(DiscountPolicy discountPolicy);

    void deleteById(DiscountPolicyId policyId);

    boolean existsById(DiscountPolicyId policyId);
}