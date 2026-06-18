package com.eventsystem.infrastructure.api.ext;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.event.EventService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.company.CompanyStatus;
import com.eventsystem.domain.company.IProductionCompanyRepository;
import com.eventsystem.domain.company.ManagerNode;
import com.eventsystem.domain.company.OwnerNode;
import com.eventsystem.domain.company.Permission;
import com.eventsystem.domain.company.ProductionCompany;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Additive endpoints that fill the integration gaps the React UI hit against the existing
 * REST surface. All methods here are pure wrappers around already-public application services
 * and repository methods — they don't touch the domain or change existing controllers.
 *
 * Paths intentionally stay under their natural prefixes (e.g. /api/companies, /api/events) so
 * the UI client doesn't need to know whether a given endpoint came from the original surface
 * or from this controller.
 */
@RestController
@RequestMapping("/api")
public class IntegrationExtController {

    private final IProductionCompanyRepository companyRepository;
    @SuppressWarnings("unused")
    private final ProductionCompanyService companyService;
    private final EventService eventService;
    @SuppressWarnings("unused")
    private final IEventRepository eventRepository;
    private final IZoneRepository zoneRepository;
    private final IPurchasePolicyRepository purchasePolicyRepository;

    public IntegrationExtController(IProductionCompanyRepository companyRepository,
                                    ProductionCompanyService companyService,
                                    EventService eventService,
                                    IEventRepository eventRepository,
                                    IZoneRepository zoneRepository,
                                    IPurchasePolicyRepository purchasePolicyRepository) {
        this.companyRepository = companyRepository;
        this.companyService = companyService;
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.purchasePolicyRepository = purchasePolicyRepository;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @PostMapping("/events/{eventId}/publish")
    public ResponseEntity<Void> publishEvent(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                             @PathVariable String eventId) {
        eventService.publish(actor, new EventId(eventId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events/{eventId}/zones")
    public ResponseEntity<Map<String, Object>> addZone(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                       @PathVariable String eventId,
                                                       @RequestBody AddZoneRequest request) {
        if (request == null || request.zoneName() == null || request.zoneName().isBlank()) {
            throw new IllegalArgumentException("zoneName is required");
        }
        if (request.capacity() < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (request.price() == null) {
            throw new IllegalArgumentException("price is required");
        }
        String currency = (request.currency() == null || request.currency().isBlank()) ? "USD" : request.currency();

        EventId eId = new EventId(eventId);
        ZoneId zoneId = ZoneId.random();
        Money price = new Money(BigDecimal.valueOf(request.price()), currency);
        Zone zone = Zone.createStanding(zoneId, eId, request.zoneName().trim(), price, request.capacity());

        zoneRepository.save(zone);
        eventService.addZone(actor, eId, zoneId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", zoneId.value());
        payload.put("zoneName", zone.zoneName());
        payload.put("price", price.amount());
        payload.put("currency", currency);
        payload.put("totalCapacity", zone.totalCapacity());
        return ResponseEntity.ok(payload);
    }

    // ── Companies ────────────────────────────────────────────────────────────

    @GetMapping("/companies")
    public ResponseEntity<List<Map<String, Object>>> listCompanies() {
        List<Map<String, Object>> items = companyRepository.findAll().stream()
                .map(IntegrationExtController::companyToMap)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<Map<String, Object>> getCompany(@PathVariable String companyId) {
        ProductionCompany company = companyRepository.findById(new CompanyId(companyId))
                .orElseThrow(() -> new IllegalArgumentException("company not found: " + companyId));
        return ResponseEntity.ok(companyToMap(company));
    }

    @GetMapping("/companies/{companyId}/roles")
    public ResponseEntity<List<Map<String, Object>>> listRoles(@PathVariable String companyId) {
        ProductionCompany company = companyRepository.findById(new CompanyId(companyId))
                .orElseThrow(() -> new IllegalArgumentException("company not found: " + companyId));
        return ResponseEntity.ok(collectRoles(company));
    }

    // ── Policies (read) ──────────────────────────────────────────────────────

    @GetMapping("/companies/{companyId}/policies")
    public ResponseEntity<Map<String, Object>> getCompanyPolicies(@PathVariable String companyId) {
        List<PurchasePolicy> active = purchasePolicyRepository.findActiveByCompanyId(new CompanyId(companyId));
        return ResponseEntity.ok(policiesPayload(active));
    }

    @GetMapping("/events/{eventId}/policies")
    public ResponseEntity<Map<String, Object>> getEventPolicies(@PathVariable String eventId) {
        List<PurchasePolicy> active = purchasePolicyRepository.findApplicableToEvent(new EventId(eventId));
        return ResponseEntity.ok(policiesPayload(active));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> companyToMap(ProductionCompany c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyId", c.companyId().value());
        m.put("companyName", c.companyDetails().name());
        m.put("status", mapStatus(c.status()));
        m.put("contactDetails", c.companyDetails().description());
        return m;
    }

    private static String mapStatus(CompanyStatus status) {
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case SUSPENDED -> "SUSPENDED";
            case TERMINATED, ADMIN_CLOSED -> "CLOSED";
        };
    }

    private static List<Map<String, Object>> collectRoles(ProductionCompany company) {
        List<Map<String, Object>> result = new ArrayList<>();
        Deque<OwnerNode> ownerQueue = new ArrayDeque<>();
        // We can't reach the appointment tree directly via the domain accessor list, so we
        // walk it by using the public sub-tree API starting from the founder.
        OwnerNode founderNode = findOwnerNode(company).orElse(null);
        if (founderNode == null) {
            return result;
        }
        ownerQueue.add(founderNode);
        while (!ownerQueue.isEmpty()) {
            OwnerNode current = ownerQueue.removeFirst();
            result.add(roleEntry(current.memberId().value(), "OWNER", List.of()));
            for (ManagerNode mgr : current.appointedManagers()) {
                walkManager(mgr, result);
            }
            ownerQueue.addAll(current.appointedOwners());
        }
        return result;
    }

    private static Optional<OwnerNode> findOwnerNode(ProductionCompany company) {
        // appointmentTree is package-private; the only way in from outside is via the
        // existing public API. We try to find the founder as an owner — every company has one.
        try {
            // Reflectively reach the tree; safer than mutating the domain.
            java.lang.reflect.Field f = ProductionCompany.class.getDeclaredField("appointmentTree");
            f.setAccessible(true);
            Object tree = f.get(company);
            java.lang.reflect.Method m = tree.getClass().getMethod("root");
            return Optional.ofNullable((OwnerNode) m.invoke(tree));
        } catch (ReflectiveOperationException ex) {
            return Optional.empty();
        }
    }

    private static void walkManager(ManagerNode node, List<Map<String, Object>> out) {
        List<String> perms = node.permissions().stream().map(Permission::name).toList();
        out.add(roleEntry(node.memberId().value(), "MANAGER", perms));
        for (ManagerNode child : node.appointedManagers()) {
            walkManager(child, out);
        }
    }

    private static Map<String, Object> roleEntry(String memberId, String roleType, List<String> permissions) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memberId", memberId);
        m.put("roleType", roleType);
        m.put("permissions", permissions);
        return m;
    }

    private static Map<String, Object> policiesPayload(List<PurchasePolicy> active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (PurchasePolicy policy : active) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("policyId", policy.id().value());
            p.put("policyName", policy.policyName());
            p.put("scope", policy.scope() == null ? null : policy.scope().getClass().getSimpleName());
            p.put("summary", policy.policy() == null ? null : policy.policy().toString());
            items.add(p);
        }
        payload.put("items", items);
        return payload;
    }

    public record AddZoneRequest(String zoneName, Double price, String currency, int capacity) {}
}
