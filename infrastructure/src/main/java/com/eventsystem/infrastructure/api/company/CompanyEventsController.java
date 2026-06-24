package com.eventsystem.infrastructure.api.company;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.EventService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Lists all events of a company (drafts included). Owner/manager only.
@RestController
@RequestMapping("/api/companies/{companyId}/events")
public class CompanyEventsController {

    private final EventService eventService;
    private final ICompanyPermissionServicePort permissionService;

    public CompanyEventsController(EventService eventService,
                                   ICompanyPermissionServicePort companyPermissionServicePort) {
        this.eventService = eventService;
        this.permissionService = companyPermissionServicePort;
    }

    @GetMapping("")
    public ResponseEntity<List<Map<String, Object>>> listCompanyEvents(
            @PathVariable String companyId,
            @RequestAttribute("authenticatedMemberId") MemberId actor) {

        CompanyId cId = new CompanyId(companyId);
        if (!permissionService.canManageEvents(actor, cId)) {
            throw new SecurityException("You do not have permission to view this company's events");
        }

        List<Map<String, Object>> events = eventService.findByCompany(cId).stream()
                .map(this::toListItem)
                .toList();

        return ResponseEntity.ok(events);
    }

    private Map<String, Object> toListItem(Event event) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", event.id().value());
        m.put("eventName", event.details().name());
        m.put("status", event.status().name());
        m.put("salesMethod", event.salesMethod().name());
        m.put("category", event.details().category());
        m.put("location", event.details().location());
        m.put("dates", event.details().dates().stream().map(LocalDateTime::toString).toList());
        return m;
    }
}
