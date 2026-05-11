package com.eventsystem.application.company;

import com.eventsystem.domain.company.CompanyDomainException;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.company.ProductionCompanyRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionCompanyServiceTest {
    private InMemoryMemberRepository memberRepository;
    private InMemoryProductionCompanyRepository companyRepository;
    private ProductionCompanyService service;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        companyRepository = new InMemoryProductionCompanyRepository();
        service = new ProductionCompanyService(companyRepository, memberRepository);
    }

    @Test
    void createCompanyFailsWhenFounderMissing() {
        assertThatThrownBy(() -> service.createCompany(MemberId.random(), "Nope", "desc", 3.0))
                .isInstanceOf(CompanyDomainException.class)
                .hasMessageContaining("member not found");
    }

    @Test
    void hasPermissionReturnsFalseForNonExistingMember() {
        MemberId founder = MemberId.random();
        memberRepository.save(new Member(founder));
        CompanyId companyId = service.createCompany(founder, "Comp", "desc", 4.2);

        boolean result = service.hasPermission(MemberId.random(), companyId, Permission.MODIFY_POLICIES);

        assertThat(result).isFalse();
    }

    @Test
    void removeAppointeeReassignsOwnersToParent() {
        MemberId founder = MemberId.random();
        MemberId owner = MemberId.random();
        MemberId childOwner = MemberId.random();
        memberRepository.save(new Member(founder));
        memberRepository.save(new Member(owner));
        memberRepository.save(new Member(childOwner));

        CompanyId companyId = service.createCompany(founder, "Cascade", "desc", 4.1);
        service.appointOwner(companyId, founder, owner);
        service.appointOwner(companyId, owner, childOwner);

        service.removeAppointee(companyId, founder, owner);

        ProductionCompany company = companyRepository.findById(companyId).orElseThrow();
        assertThat(company.isOwner(owner)).isFalse();
        // childOwner should be reassigned to founder (owner's appointer)
        assertThat(company.isOwner(childOwner)).isTrue();
    }

    private static final class InMemoryMemberRepository implements MemberRepository {
        private final Map<MemberId, Member> members = new ConcurrentHashMap<>();

        @Override
        public Optional<Member> findById(MemberId memberId) {
            return Optional.ofNullable(members.get(memberId));
        }

        void save(Member member) {
            members.put(member.memberId(), member);
        }
    }

    private static final class InMemoryProductionCompanyRepository implements ProductionCompanyRepository {
        private final Map<CompanyId, ProductionCompany> companiesById = new ConcurrentHashMap<>();
        private final Map<String, CompanyId> names = new ConcurrentHashMap<>();

        @Override
        public Optional<ProductionCompany> findById(CompanyId companyId) {
            return Optional.ofNullable(companiesById.get(companyId));
        }

        @Override
        public Optional<ProductionCompany> findByName(String companyName) {
            CompanyId id = names.get(companyName.toLowerCase());
            if (id == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(companiesById.get(id));
        }

        @Override
        public void save(ProductionCompany productionCompany) {
            names.put(productionCompany.companyDetails().name().toLowerCase(), productionCompany.companyId());
            companiesById.put(productionCompany.companyId(), productionCompany);
        }
    }
}
