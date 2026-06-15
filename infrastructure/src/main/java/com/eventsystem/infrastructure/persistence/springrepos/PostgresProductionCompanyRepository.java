package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.member.MemberId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PostgresProductionCompanyRepository implements IProductionCompanyRepository {

    private final SpringDataProductionCompanyRepository jpaRepository;

    public PostgresProductionCompanyRepository(SpringDataProductionCompanyRepository jpaRepository) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
    }

    @Override
    public Optional<ProductionCompany> findById(CompanyId companyId) {
        return jpaRepository.findById(companyId);
    }

    @Override
    public Optional<ProductionCompany> findByName(String companyName) {
        return jpaRepository.findByDetailsName(companyName);
    }

    @Override
    public void save(ProductionCompany company) {
        jpaRepository.save(company);
    }

    @Override
    public boolean hasPermission(MemberId memberId, CompanyId companyId, Permission permission) {
        return jpaRepository.findById(companyId)
                .map(company -> company.hasPermission(memberId, permission))
                .orElse(false);
    }

    @Override
    public List<ProductionCompany> findAll() {
        return jpaRepository.findAll();
    }
}
