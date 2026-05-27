package com.eventsystem.application.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.application.company.ProductionCompanyRepository;
import com.eventsystem.domain.member.MemberId;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DiscountPermissionChecker implements IDiscountPermissionChecker {

    private final ProductionCompanyRepository productionCompanyRepository;

    public DiscountPermissionChecker(ProductionCompanyRepository productionCompanyRepository) {
        this.productionCompanyRepository = Objects.requireNonNull(
                productionCompanyRepository,
                "productionCompanyRepository must not be null"
        );
    }

    @Override
    public boolean canManagePolicies(String actorId, String companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (actorId.isBlank() || companyId.isBlank()) {
            return false;
        }

        return productionCompanyRepository.hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.MODIFY_POLICIES
        );
    }
}