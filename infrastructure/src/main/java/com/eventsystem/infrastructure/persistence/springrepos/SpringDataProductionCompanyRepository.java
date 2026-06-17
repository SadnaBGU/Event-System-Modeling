package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.ProductionCompany;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SpringDataProductionCompanyRepository extends JpaRepository<ProductionCompany, CompanyId> {
    
    
    @Query("SELECT c FROM ProductionCompany c WHERE c.companyDetails.name = :name")
    Optional<ProductionCompany> findByDetailsName(@Param("name") String name);
}
