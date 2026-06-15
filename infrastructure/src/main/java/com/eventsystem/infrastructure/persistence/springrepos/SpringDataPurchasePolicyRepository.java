package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SpringDataPurchasePolicyRepository extends JpaRepository<PurchasePolicy, PurchasePolicyId> {
    List<PurchasePolicy> findByCompanyId(com.eventsystem.domain.company.CompanyId companyId);
}
