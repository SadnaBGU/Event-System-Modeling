package com.eventsystem.domain.company;

import java.util.Optional;

public interface ProductionCompanyRepository {
    Optional<ProductionCompany> findById(CompanyId companyId);

    Optional<ProductionCompany> findByName(String companyName);

    void save(ProductionCompany productionCompany);
}
