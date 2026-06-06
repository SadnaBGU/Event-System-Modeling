package com.eventsystem.application.company;

import java.util.List;
import java.util.Optional;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.member.MemberId;

public interface IProductionCompanyRepository {
    Optional<ProductionCompany> findById(CompanyId companyId);

    Optional<ProductionCompany> findByName(String companyName);

    void save(ProductionCompany productionCompany);

    boolean hasPermission(MemberId memberId, CompanyId companyId, Permission eventInventoryManagement);

    /** All companies currently in the system. Default returns empty for legacy implementations. */
    default List<ProductionCompany> findAll() {
        return List.of();
    }
}
