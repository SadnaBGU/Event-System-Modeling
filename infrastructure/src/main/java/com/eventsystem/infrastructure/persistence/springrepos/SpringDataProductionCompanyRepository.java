package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.ProductionCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SpringDataProductionCompanyRepository extends JpaRepository<ProductionCompany, CompanyId> {
    
    java.util.Optional<ProductionCompany> findByDetailsName(String name);
}
