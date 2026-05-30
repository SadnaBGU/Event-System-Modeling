package com.eventsystem.infrastructure.api.admin;

import com.eventsystem.application.admin.IPlatformRepository;
import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.order.PurchaseHistoryService;
import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.Platform;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminStreamController {

    private final IPlatformRepository platformRepository;
    private final ProductionCompanyService productionCompanyService;
    private final PurchaseHistoryService purchaseHistoryService;

    public AdminStreamController(IPlatformRepository platformRepository,
                                 ProductionCompanyService productionCompanyService,
                                 PurchaseHistoryService purchaseHistoryService) {
        this.platformRepository = platformRepository;
        this.productionCompanyService = productionCompanyService;
        this.purchaseHistoryService = purchaseHistoryService;
    }

    @DeleteMapping("/companies/{companyId}")
    public void closeCompany(@RequestAttribute("authenticatedMemberId") MemberId actor,
                             @PathVariable String companyId,
                             HttpServletResponse response) {
        requireAdmin(actor);
        productionCompanyService.adminCloseCompany(new CompanyId(companyId));
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }

    @GetMapping("/history")
    public ResponseEntity<PurchaseHistoryPageResponse> getGlobalHistory(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(actor);
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        List<PurchaseRecordDTO> all = purchaseHistoryService.getGlobalHistory().stream()
                .sorted(Comparator.comparing(PurchaseRecordDTO::purchaseTimestamp).reversed())
                .toList();

        int totalElements = all.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        return ResponseEntity.ok(new PurchaseHistoryPageResponse(
                all.subList(fromIndex, toIndex),
                page,
                size,
                totalElements,
                totalPages));
    }

    private void requireAdmin(MemberId actor) {
        Platform platform = platformRepository.findInstance()
                .orElseThrow(() -> new IllegalStateException("Platform has not been initialised"));
        if (!platform.isAdmin(actor)) {
            throw new SecurityException("Actor " + actor.value() + " is not a system administrator");
        }
    }

    public record PurchaseHistoryPageResponse(
            List<PurchaseRecordDTO> items,
            int page,
            int size,
            int totalElements,
            int totalPages) {
    }
}
