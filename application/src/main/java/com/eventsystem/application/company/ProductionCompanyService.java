package com.eventsystem.application.company;

import com.eventsystem.application.member.IMemberRepository;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.domainexceptions.CompanyDomainException;
import com.eventsystem.domain.member.MemberId;

import java.util.Objects;
import java.util.Set;

public class ProductionCompanyService implements ICompanyPermissionServicePort{
    private final IProductionCompanyRepository productionCompanyRepository;
    private final IMemberRepository memberRepository;

    public ProductionCompanyService(
            IProductionCompanyRepository productionCompanyRepository,
            IMemberRepository memberRepository) {
        this.productionCompanyRepository = Objects.requireNonNull(
                productionCompanyRepository,
                "productionCompanyRepository must not be null");
        this.memberRepository = Objects.requireNonNull(memberRepository, "memberRepository must not be null");
    }

    public CompanyId createCompany(MemberId founderMemberId, String name, String description, double rating) {
        requireMemberExists(founderMemberId);
        if (productionCompanyRepository.findByName(name).isPresent()) {
            throw new CompanyDomainException("company name already exists");
        }

        ProductionCompany company = ProductionCompany.create(founderMemberId, name, description, rating);
        productionCompanyRepository.save(company);
        return company.companyId();
    }

    public void appointOwner(CompanyId companyId, MemberId actor, MemberId target) {
        requireMemberExists(target);
        ProductionCompany company = loadCompany(companyId);
        company.appointOwner(actor, target);
        productionCompanyRepository.save(company);
    }

    public void appointManager(CompanyId companyId, MemberId actor, MemberId target, Set<Permission> permissions) {
        requireMemberExists(target);
        ProductionCompany company = loadCompany(companyId);
        company.appointManager(actor, target, permissions);
        productionCompanyRepository.save(company);
    }

    public void appointManagerToManager(CompanyId companyId, MemberId actor, MemberId target, Set<Permission> permissions) {
        requireMemberExists(target);
        ProductionCompany company = loadCompany(companyId);
        company.appointManagerToManager(actor, target, permissions);
        productionCompanyRepository.save(company);
    }

    public void removeAppointee(CompanyId companyId, MemberId actor, MemberId target) {
        ProductionCompany company = loadCompany(companyId);
        if (company.isOwner(target)) {
            company.removeOwner(actor, target);
        } else if (company.isManager(target)) {
            company.removeManager(actor, target);
        } else {
            throw new CompanyDomainException("target appointee is not part of company appointments");
        }
        productionCompanyRepository.save(company);
    }

    public void relinquishOwnership(CompanyId companyId, MemberId memberId) {
        requireMemberExists(memberId);
        ProductionCompany company = loadCompany(companyId);
        company.relinquishOwnership(memberId);
        productionCompanyRepository.save(company);
    }

    public void modifyManagerPermissions(
            CompanyId companyId,
            MemberId actor,
            MemberId managerId,
            Set<Permission> permissions) {
        ProductionCompany company = loadCompany(companyId);
        company.modifyManagerPermissions(actor, managerId, permissions);
        productionCompanyRepository.save(company);
    }

    public boolean hasPermission(MemberId memberId, CompanyId companyId, Permission permission) {
        if (memberRepository.findById(memberId).isEmpty()) {
            return false;
        }
        return loadCompany(companyId).hasPermission(memberId, permission);
    }

    public void suspendCompany(CompanyId companyId) {
        ProductionCompany company = loadCompany(companyId);
        company.suspend();
        productionCompanyRepository.save(company);
    }

    public void reopenCompany(CompanyId companyId) {
        ProductionCompany company = loadCompany(companyId);
        company.reopen();
        productionCompanyRepository.save(company);
    }

    public void terminateCompany(CompanyId companyId) {
        ProductionCompany company = loadCompany(companyId);
        company.terminate();
        productionCompanyRepository.save(company);
    }

    public void adminCloseCompany(CompanyId companyId) {
        ProductionCompany company = loadCompany(companyId);
        company.adminClose();
        productionCompanyRepository.save(company);
    }

    public void updateCompanyName(CompanyId companyId, String newName) {
        ProductionCompany company = loadCompany(companyId);
        company.updateName(newName);
        productionCompanyRepository.save(company);
    }

    public void updateCompanyDescription(CompanyId companyId, String newDescription) {
        ProductionCompany company = loadCompany(companyId);
        company.updateDescription(newDescription);
        productionCompanyRepository.save(company);
    }

    public void updateCompanyRating(CompanyId companyId, double newRating) {
        ProductionCompany company = loadCompany(companyId);
        company.updateRating(newRating);
        productionCompanyRepository.save(company);
    }

    public void acceptAppointment(CompanyId companyId, MemberId target) {
        ProductionCompany company = loadCompany(companyId);
        company.acceptAppointment(target);
        productionCompanyRepository.save(company);
    }

    private ProductionCompany loadCompany(CompanyId companyId) {
        return productionCompanyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyDomainException("company not found"));
    }

    private void requireMemberExists(MemberId memberId) {
        if (memberRepository.findById(memberId).isEmpty()) {
            throw new CompanyDomainException("member not found");
        }
    }

    //Added for Event and policy managment by company
    @Override
    public boolean canManageEvents(MemberId actorId, CompanyId companyId) {
        return hasPermission(actorId, companyId, Permission.EVENT_INVENTORY_MANAGEMENT);
    }

    @Override
    public boolean canManageDiscountPolicies(MemberId actorId, CompanyId companyId) {
        return hasPermission(actorId, companyId, Permission.MODIFY_POLICIES);
    }

    @Override
    public boolean canManagePurchasePolicies(MemberId actorId, CompanyId companyId) {
        return hasPermission(actorId, companyId, Permission.MODIFY_POLICIES);
    }

    @Override
    public String getCompanyName(CompanyId companyId) {
        return loadCompany(companyId).companyDetails().name();
    }
}
