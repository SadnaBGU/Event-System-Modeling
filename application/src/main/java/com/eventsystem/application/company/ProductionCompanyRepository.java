package com.eventsystem.application.company;

import java.util.Optional;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.member.MemberId;

public interface ProductionCompanyRepository {
    Optional<ProductionCompany> findById(CompanyId companyId);

    Optional<ProductionCompany> findByName(String companyName);

    void save(ProductionCompany productionCompany);

    boolean hasPermission(MemberId memberId, CompanyId companyId, Permission eventInventoryManagement);
}
