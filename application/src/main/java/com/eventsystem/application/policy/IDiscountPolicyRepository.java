package com.eventsystem.application.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;

import java.util.Optional;
import java.util.List;

public interface IDiscountPolicyRepository {

    Optional<DiscountPolicy> findById(DiscountPolicyId policyId);

    List<DiscountPolicy> findByCompanyId(CompanyId companyId);

    List<DiscountPolicy> findActive();

    List<DiscountPolicy> findActiveWithVisibleDiscounts();

    List<DiscountPolicy> findActiveByCompanyId(CompanyId companyId);

    List<DiscountPolicy> findApplicableToEvent(EventId eventId);

    List<DiscountPolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId);

    void save(DiscountPolicy discountPolicy);

    void deleteById(DiscountPolicyId policyId);

    boolean existsById(DiscountPolicyId policyId);
}