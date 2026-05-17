package com.eventsystem.application.company;

import java.util.Optional;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.ProductionCompany;

public interface ProductionCompanyRepository {
    Optional<ProductionCompany> findById(CompanyId companyId);

    Optional<ProductionCompany> findByName(String companyName);

    void save(ProductionCompany productionCompany);
}
