package com.eventsystem.infrastructure.persistence;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.inmemoryrepos.InMemoryProductionCompanyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryProductionCompanyRepositoryTest {

    private InMemoryProductionCompanyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductionCompanyRepository();
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertThat(repository.findById(CompanyId.random())).isEmpty();
    }

    @Test
    void save_thenFindById_andFindByName_workWithNameNormalization() {
        ProductionCompany company = ProductionCompany.create(MemberId.random(), "Acme Productions", "Desc", 4.5);
        repository.save(company);

        assertThat(repository.findById(company.companyId())).contains(company);
        assertThat(repository.findByName("  ACME PRODUCTIONS  ")).contains(company);
    }

    @Test
    void findByName_unknownName_returnsEmpty() {
        assertThat(repository.findByName("missing")).isEmpty();
    }

    @Test
    void save_sameNameDifferentCompany_throwsCompanyDomainException() {
        ProductionCompany first = ProductionCompany.create(MemberId.random(), "SameName", "A", 4.0);
        ProductionCompany second = ProductionCompany.create(MemberId.random(), "  samename  ", "B", 3.0);
        repository.save(first);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("company name already exists");
    }

    @Test
    void hasPermission_returnsTrueForFounderPermissionWhenCompanyExists() {
        MemberId founder = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "FounderCo", "Desc", 4.1);
        repository.save(company);

        boolean allowed = repository.hasPermission(founder, company.companyId(), Permission.EVENT_INVENTORY_MANAGEMENT);

        assertThat(allowed).isTrue();
    }

    @Test
    void hasPermission_returnsFalseForUnknownMemberOrCompany() {
        MemberId founder = MemberId.random();
        ProductionCompany company = ProductionCompany.create(founder, "KnownCo", "Desc", 3.9);
        repository.save(company);

        boolean unknownMember = repository.hasPermission(MemberId.random(), company.companyId(), Permission.EVENT_INVENTORY_MANAGEMENT);
        boolean unknownCompany = repository.hasPermission(founder, CompanyId.random(), Permission.EVENT_INVENTORY_MANAGEMENT);

        assertThat(unknownMember).isFalse();
        assertThat(unknownCompany).isFalse();
    }
}
