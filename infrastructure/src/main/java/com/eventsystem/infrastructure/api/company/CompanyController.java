package com.eventsystem.infrastructure.api.company;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.application.order.ReportService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.CompanyStatus;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.event.EventDetails;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final ProductionCompanyService productionCompanyService;
    private final EventService eventService;
    private final ReportService reportService;

    public CompanyController(ProductionCompanyService productionCompanyService,
                             EventService eventService,
                             ReportService reportService) {
        this.productionCompanyService = productionCompanyService;
        this.eventService = eventService;
        this.reportService = reportService;
    }

    @SuppressWarnings("null")
    @PostMapping
    public ResponseEntity<Void> createCompany(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                              @RequestBody CreateCompanyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        CompanyId companyId = productionCompanyService.createCompany(
                actor,
                request.companyName(),
                request.contactDetails(),
                0.0
        );
        return ResponseEntity.created(URI.create("/api/companies/" + companyId.value())).build();
    }

    @PatchMapping("/{companyId}/status")
    public ResponseEntity<Void> updateCompanyStatus(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                    @PathVariable String companyId,
                                                    @RequestBody CompanyStatusRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        CompanyId id = new CompanyId(companyId);
        CompanyStatus status = CompanyStatus.valueOf(request.status().trim().toUpperCase());

        switch (status) {
            case ACTIVE -> productionCompanyService.reopenCompany(id);
            case SUSPENDED -> productionCompanyService.suspendCompany(id);
            default -> throw new IllegalArgumentException("status must be ACTIVE or SUSPENDED");
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{companyId}/reports/sales")
    public ResponseEntity<ReportService.CompanySalesReportDTO> getSalesReport(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                                             @PathVariable String companyId) {
        CompanyId id = new CompanyId(companyId);
        if (!productionCompanyService.hasPermission(actor, id, Permission.GENERATE_SALES_REPORT)) {
            throw new SecurityException("actor is not allowed to generate sales report for company: " + companyId);
        }

        ReportService.CompanySalesReportDTO report = reportService.generateCompanySalesReport(
                id,
                eventService.findByCompany(id)
        );
        return ResponseEntity.ok(report);
    }

    @PostMapping("/{companyId}/roles")
    public ResponseEntity<Void> appointRole(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                            @PathVariable String companyId,
                                            @RequestBody RoleRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        CompanyId id = new CompanyId(companyId);
        MemberId target = new MemberId(request.targetMemberId());
        String roleType = request.roleType() == null ? "" : request.roleType().trim().toUpperCase();

        switch (roleType) {
            case "OWNER" -> productionCompanyService.appointOwner(id, actor, target);
            case "MANAGER" -> productionCompanyService.appointManager(id, actor, target, parsePermissions(request.permissionsList()));
            default -> throw new IllegalArgumentException("roleType must be OWNER or MANAGER");
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{companyId}/roles/{targetMemberId}/accept")
    public ResponseEntity<Void> acceptRoleAppointment(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                      @PathVariable String companyId,
                                                      @PathVariable String targetMemberId) {
        if (!actor.value().equals(targetMemberId)) {
            throw new SecurityException("only the appointed member may accept the appointment");
        }

        productionCompanyService.acceptAppointment(new CompanyId(companyId), new MemberId(targetMemberId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{companyId}/roles/{targetMemberId}")
    public ResponseEntity<Void> removeAppointee(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                @PathVariable String companyId,
                                                @PathVariable String targetMemberId) {
        productionCompanyService.removeAppointee(new CompanyId(companyId), actor, new MemberId(targetMemberId));
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("null")
    @PostMapping("/{companyId}/events")
    public ResponseEntity<Void> createEvent(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                           @PathVariable String companyId,
                                           @RequestBody CreateEventRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        EventDetails details = new EventDetails(
                request.eventName(),
                request.dates(),
                request.category(),
                request.location(),
                request.description()
        );
        EventId eventId = eventService.createDraft(actor, new CompanyId(companyId), details);
        return ResponseEntity.created(URI.create("/api/events/" + eventId.value())).build();
    }

    private Set<Permission> parsePermissions(List<String> permissionsList) {
        if (permissionsList == null || permissionsList.isEmpty()) {
            throw new IllegalArgumentException("permissionsList must not be empty for a manager role");
        }

        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String permissionName : permissionsList) {
            if (permissionName == null || permissionName.isBlank()) {
                continue;
            }
            permissions.add(Permission.valueOf(permissionName.trim().toUpperCase()));
        }
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("permissionsList must not be empty for a manager role");
        }
        return Set.copyOf(permissions);
    }

    public record CreateCompanyRequest(String companyName, String contactDetails) {
    }

    public record CompanyStatusRequest(String status) {
    }

    public record RoleRequest(String targetMemberId, String roleType, List<String> permissionsList) {
    }

    public record CreateEventRequest(
            String eventName,
            List<java.time.LocalDateTime> dates,
            String category,
            String location,
            String description) {
    }
}