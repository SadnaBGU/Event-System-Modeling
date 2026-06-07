package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryProductionCompanyRepository implements IProductionCompanyRepository {
    private final ConcurrentMap<CompanyId, ProductionCompany> companiesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompanyId> companyNameToId = new ConcurrentHashMap<>();

    @Override
    public Optional<ProductionCompany> findById(CompanyId companyId) {
        return Optional.ofNullable(companiesById.get(companyId));
    }

    @Override
    public Optional<ProductionCompany> findByName(String companyName) {
        CompanyId companyId = companyNameToId.get(normalize(companyName));
        if (companyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(companiesById.get(companyId));
    }

    @Override
    public List<ProductionCompany> findAll() {
        return List.copyOf(companiesById.values());
    }

    @Override
    public void save(ProductionCompany productionCompany) {
        String normalizedName = normalize(productionCompany.companyDetails().name());
        CompanyId newId = productionCompany.companyId();

        companyNameToId.compute(normalizedName, (name, existingId) -> {
            if (existingId != null && !existingId.equals(newId)) {
                throw new CompanyDomainException("company name already exists");
            }
            return newId;
        });

        companiesById.put(newId, productionCompany);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean hasPermission(MemberId memberId, CompanyId companyId, Permission eventInventoryManagement) {
        return findById(companyId)
                .map(company -> company.hasPermission(memberId, eventInventoryManagement))
                .orElse(false); 
    }
}
