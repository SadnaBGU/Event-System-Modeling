package com.eventsystem.application.admin;

import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.domain.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use case (UC22): a System Admin bans/removes a member.
 *
 * Banning disables the member's account (so they can no longer log in) and
 * revokes all of their production-company roles globally. If the member is the
 * sole founder of a company, that founder role cannot be removed; the company is
 * reported as orphaned so the admin can reassign a founder or close it.
 */
public class AdminMemberBanService {

    private static final Logger log = LoggerFactory.getLogger(AdminMemberBanService.class);

    private final IPlatformRepository platformRepo;
    private final IMemberRepository memberRepo;
    private final IProductionCompanyRepository companyRepo;

    public AdminMemberBanService(IPlatformRepository platformRepo,
                                 IMemberRepository memberRepo,
                                 IProductionCompanyRepository companyRepo) {
        this.platformRepo = Objects.requireNonNull(platformRepo, "platformRepo must not be null");
        this.memberRepo = Objects.requireNonNull(memberRepo, "memberRepo must not be null");
        this.companyRepo = Objects.requireNonNull(companyRepo, "companyRepo must not be null");
    }

    /**
     * Bans the target member: disables the account and revokes all company roles.
     *
     * @return a {@link BanResult} listing any companies left orphaned (target was sole founder)
     */
    public BanResult banMember(MemberId actor, MemberId target) {
        requireAdmin(actor);
        Objects.requireNonNull(target, "target must not be null");

        Member member = memberRepo.findById(target)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + target.value()));
        member.cancel();
        memberRepo.save(member);

        List<CompanyId> orphaned = new ArrayList<>();
        for (ProductionCompany company : companyRepo.findAll()) {
            boolean isFounder = company.founderId().equals(target);
            boolean hasRole = isFounder || company.isOwner(target) || company.isManager(target);
            if (!hasRole) {
                continue;
            }
            if (company.adminRevokeRolesOf(target)) {
                orphaned.add(company.companyId());
            }
            companyRepo.save(company);
        }

        log.info("Member {} banned by admin={}. orphanedCompanies={}",
                target.value(), actor.value(), orphaned.size());
        return new BanResult(target, orphaned);
    }

    private void requireAdmin(MemberId actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        Platform platform = platformRepo.findInstance()
                .orElseThrow(() -> new IllegalStateException("Platform has not been initialised"));
        if (!platform.isAdmin(actor)) {
            throw new NotAuthorizedException(actor.value());
        }
    }
}