package com.eventsystem.domain.company;

import java.util.List;
import java.util.Optional;

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
