package com.eventsystem.application.company;

import com.eventsystem.domain.company.*;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionCompanyServiceTest {
    private IMemberRepository memberRepository;
    private IProductionCompanyRepository companyRepository;
    private ProductionCompanyService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(IMemberRepository.class);
        companyRepository = mock(IProductionCompanyRepository.class);
        service = new ProductionCompanyService(companyRepository, memberRepository);
    }

    @Test
    void createCompanyFailsWhenFounderMissing() {
        assertThatThrownBy(() -> service.createCompany(MemberId.random(), "Name", "Desc", 5.0))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("member not found");
    }

    @Test
    void createCompanyFailsWhenNameExists() {
        MemberId founder = MemberId.random();
        memberRepository.save(new Member(founder));
        when(companyRepository.findByName("Exists")).thenReturn(Optional.of(ProductionCompany.create(founder, "Exists", "Desc", 5.0)));
        when(memberRepository.findById(founder)).thenReturn(Optional.of(new Member(founder)));
        assertThatThrownBy(() -> service.createCompany(founder, "Exists", "Desc", 5.0))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createCompanySuccess() {
        MemberId founder = MemberId.random();
        memberRepository.save(new Member(founder));
        when(memberRepository.findById(founder)).thenReturn(Optional.of(new Member(founder)));

        CompanyId id = service.createCompany(founder, "New", "Desc", 4.5);
        
        assertThat(id).isNotNull();
    }

    @Test
    void permissionChecks_ReturnsFalseWhenCompanyMissing() {
        MemberId actor = MemberId.random();
        CompanyId company = CompanyId.random();

        assertThat(service.canManageEvents(actor, company)).isFalse();
        assertThat(service.canManageDiscountPolicies(actor, company)).isFalse();
        assertThat(service.canManagePurchasePolicies(actor, company)).isFalse();
    }
    
    @Test
    void acceptAppointment_ThrowsWhenCompanyMissing() {
        assertThatThrownBy(() -> service.acceptAppointment(CompanyId.random(), MemberId.random()))
            .isInstanceOf(CompanyDomainException.class)
            .hasMessageContaining("company not found");
    }

    @Test
    void getCompanyName_ThrowsWhenCompanyMissing() {
        assertThatThrownBy(() -> service.getCompanyName(CompanyId.random()))
            .isInstanceOf(CompanyDomainException.class);
    }
    
    @Test
    void constructor_NullChecks() {
        assertThatThrownBy(() -> new ProductionCompanyService(null, memberRepository))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProductionCompanyService(companyRepository, null))
            .isInstanceOf(NullPointerException.class);
    }
}