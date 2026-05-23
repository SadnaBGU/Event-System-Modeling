package com.eventsystem.application.event;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompanyRepository;
import com.eventsystem.domain.member.MemberId;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ProductionEventPermissionChecker implements EventPermissionChecker {

    private final ProductionCompanyRepository productionCompanyRepository;

    public ProductionEventPermissionChecker(ProductionCompanyRepository productionCompanyRepository) {
        this.productionCompanyRepository = Objects.requireNonNull(
                productionCompanyRepository,
                "productionCompanyRepository must not be null"
        );
    }

    @Override
    public boolean canManageEvents(String actorId, String companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (actorId.isBlank() || companyId.isBlank()) {
            return false;
        }

        return productionCompanyRepository.findById(new CompanyId(companyId))
                .map(company -> company.hasPermission(new MemberId(actorId), Permission.EVENT_INVENTORY_MANAGEMENT))
                .orElse(false);
    }
}