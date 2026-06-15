package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SpringDataDiscountPolicyRepository extends JpaRepository<DiscountPolicy, DiscountPolicyId> {
    List<DiscountPolicy> findByCompanyId(com.eventsystem.domain.company.CompanyId companyId);
}
