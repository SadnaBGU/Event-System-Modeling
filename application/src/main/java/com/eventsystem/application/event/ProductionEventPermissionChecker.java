package com.eventsystem.application.event;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.member.MemberId;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ProductionEventPermissionChecker implements EventPermissionChecker {

    private final ProductionCompanyService productionCompanyService;

    public ProductionEventPermissionChecker(ProductionCompanyService productionCompanyService) {
        this.productionCompanyService = Objects.requireNonNull(
                productionCompanyService,
                "productionCompanyService must not be null"
        );
    }

    @Override
    public boolean canManageEvents(String actorId, String companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (actorId.isBlank() || companyId.isBlank()) {
            return false;
        }

        return productionCompanyService.hasPermission(
                new MemberId(actorId),
                new CompanyId(companyId),
                Permission.EVENT_INVENTORY_MANAGEMENT
        );
    }
}