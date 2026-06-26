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
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.IZoneRepository;
import com.eventsystem.domain.zone.Zone;
import com.eventsystem.domain.zone.ZoneId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 * REST surface. All methods here are wrappers around already-public application services
 * and repository methods. They do not change the domain.
 *
 * Paths intentionally stay under their natural prefixes, for example /api/companies
 * and /api/events, so the UI client does not need to know whether a given endpoint
 * came from the original surface or from this extension controller.
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

    public IntegrationExtController(
            IProductionCompanyRepository companyRepository,
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
    public ResponseEntity<Void> publishEvent(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String eventId) {
        eventService.publish(actor, new EventId(eventId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/events/{eventId}/zones")
    public ResponseEntity<Map<String, Object>> addZone(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String eventId,
            @RequestBody AddZoneRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (request.zoneName() == null || request.zoneName().isBlank()) {
            throw new IllegalArgumentException("zoneName is required");
        }
        if (request.capacity() < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (request.price() == null) {
            throw new IllegalArgumentException("price is required");
        }
        if (request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }

        String currency = request.currency() == null || request.currency().isBlank()
                ? "USD"
                : request.currency().trim();

        EventId eId = new EventId(eventId);
        ZoneId zoneId = ZoneId.random();
        Money price = Money.of(request.price(), currency);

        boolean seated = "SEATED".equalsIgnoreCase(request.zoneType());
        Zone zone;
        if (seated) {
            int rows = request.rows() == null || request.rows() < 1 ? 1 : request.rows();
            int perRow = request.seatsPerRow() == null || request.seatsPerRow() < 1
                    ? request.capacity() : request.seatsPerRow();
            List<com.eventsystem.domain.zone.Row> rowList = new ArrayList<>();
            for (int r = 0; r < rows; r++) {
                String rowLabel = String.valueOf((char) ('A' + (r % 26)));
                List<com.eventsystem.domain.zone.Seat> seats = new ArrayList<>();
                for (int s = 1; s <= perRow; s++) {
                    seats.add(new com.eventsystem.domain.zone.Seat(
                            com.eventsystem.domain.zone.SeatId.random(), rowLabel, s));
                }
                rowList.add(new com.eventsystem.domain.zone.Row(rowLabel, seats));
            }
            zone = Zone.createSeated(zoneId, eId, request.zoneName().trim(), price, rowList);
        } else {
            zone = Zone.createStanding(zoneId, eId, request.zoneName().trim(), price, request.capacity());
        }

        zoneRepository.save(zone);
        eventService.addZone(actor, eId, zoneId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", zoneId.value());
        payload.put("zoneName", zone.zoneName());
        payload.put("zoneType", zone.zoneType().name());
        payload.put("price", price.amount());
        payload.put("currency", price.currency());
        payload.put("totalCapacity", zone.totalCapacity());
        payload.put("availableCount", zone.getAvailableCount());

        return ResponseEntity.ok(payload);
    }

    /**
     * Update an existing event's details (name, dates, category, location, description).
     * Permission is enforced by {@link EventService#updateDetails} (owner/manager of the company).
     */
    @PutMapping("/events/{eventId}/details")
    public ResponseEntity<Void> updateEventDetails(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                   @PathVariable String eventId,
                                                   @RequestBody UpdateEventRequest request) {
        if (request == null || request.eventName() == null || request.eventName().isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        if (request.dates() == null || request.dates().isEmpty()) {
            throw new IllegalArgumentException("at least one date is required");
        }
        List<java.time.LocalDateTime> dates = request.dates().stream()
                .map(java.time.LocalDateTime::parse)
                .toList();
        com.eventsystem.domain.event.EventDetails details = new com.eventsystem.domain.event.EventDetails(
                request.eventName().trim(),
                dates,
                blankToDash(request.category()),
                blankToDash(request.location()),
                blankToDash(request.description()));
        eventService.updateDetails(actor, new EventId(eventId), details);
        return ResponseEntity.noContent().build();
    }

    /**
     * Per-seat status for a SEATED zone, so the UI can render an accurate seat map
     * (which seats are AVAILABLE / RESERVED / SOLD). Standing zones return an empty list.
     */
    @GetMapping("/zones/{zoneId}/seats")
    public ResponseEntity<Map<String, Object>> getZoneSeats(@PathVariable String zoneId) {
        Zone zone = zoneRepository.findById(new ZoneId(zoneId))
                .orElseThrow(() -> new IllegalArgumentException("zone not found: " + zoneId));

        List<Map<String, Object>> seats = new ArrayList<>();
        if (zone.zoneType() == com.eventsystem.domain.zone.ZoneType.SEATED) {
            for (com.eventsystem.domain.zone.Row row : zone.rows()) {
                for (com.eventsystem.domain.zone.Seat seat : row.seats()) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("seatId", seat.seatId().value());
                    s.put("rowLabel", seat.rowLabel());
                    s.put("seatNumber", seat.seatNumber());
                    s.put("status", seat.status().name());
                    seats.add(s);
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", zone.zoneId().value());
        payload.put("zoneName", zone.zoneName());
        payload.put("zoneType", zone.zoneType().name());
        payload.put("totalCapacity", zone.totalCapacity());
        payload.put("availableCount", zone.getAvailableCount());
        payload.put("seats", seats);
        return ResponseEntity.ok(payload);
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    // ── Companies ────────────────────────────────────────────────────────────

    @GetMapping("/companies")
    public ResponseEntity<List<Map<String, Object>>> listCompanies(
            @RequestAttribute("authenticatedMemberId") MemberId actor) {
        // Members should only see companies they can actually act on: ones they own or
        // manage. Everything else is hidden from this list (admins manage companies from
        // the dedicated admin surface, not here).
        List<Map<String, Object>> items = companyRepository.findAll().stream()
                .filter(c -> c.isOwner(actor) || c.isManager(actor))
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

    // ── Policies read endpoints ──────────────────────────────────────────────

    @GetMapping("/companies/{companyId}/policies")
    public ResponseEntity<Map<String, Object>> getCompanyPolicies(@PathVariable String companyId) {
        CompanyId cId = new CompanyId(companyId);

        List<PurchasePolicy> policies = purchasePolicyRepository.findCompanyOwnedPolicies(cId);

        return ResponseEntity.ok(policiesPayload(policies));
    }

    @GetMapping("/events/{eventId}/policies")
    public ResponseEntity<Map<String, Object>> getEventPolicies(@PathVariable String eventId) {
        EventId eId = new EventId(eventId);
        CompanyId companyId = eventService.companyOfEvent(eId);

        List<PurchasePolicy> policies = purchasePolicyRepository.findApplicableToPurchase(companyId, eId);

        return ResponseEntity.ok(policiesPayload(policies));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> companyToMap(ProductionCompany company) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("companyId", company.companyId().value());
        payload.put("companyName", company.companyDetails().name());
        payload.put("status", mapStatus(company.status()));
        payload.put("contactDetails", company.companyDetails().description());

        return payload;
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

        OwnerNode founderNode = findOwnerNode(company).orElse(null);
        if (founderNode == null) {
            return result;
        }

        ownerQueue.add(founderNode);

        while (!ownerQueue.isEmpty()) {
            OwnerNode current = ownerQueue.removeFirst();

            result.add(roleEntry(current.memberId().value(), "OWNER", List.of()));

            for (ManagerNode manager : current.appointedManagers()) {
                walkManager(manager, result);
            }

            ownerQueue.addAll(current.appointedOwners());
        }

        return result;
    }

    private static Optional<OwnerNode> findOwnerNode(ProductionCompany company) {
        try {
            java.lang.reflect.Field field = ProductionCompany.class.getDeclaredField("appointmentTree");
            field.setAccessible(true);

            Object tree = field.get(company);
            if (tree == null) {
                return Optional.empty();
            }

            java.lang.reflect.Method rootMethod = tree.getClass().getMethod("root");
            return Optional.ofNullable((OwnerNode) rootMethod.invoke(tree));
        } catch (ReflectiveOperationException ex) {
            return Optional.empty();
        }
    }

    private static void walkManager(ManagerNode node, List<Map<String, Object>> out) {
        List<String> permissions = node.permissions()
                .stream()
                .map(Permission::name)
                .toList();

        out.add(roleEntry(node.memberId().value(), "MANAGER", permissions));

        for (ManagerNode child : node.appointedManagers()) {
            walkManager(child, out);
        }
    }

    private static Map<String, Object> roleEntry(
            String memberId,
            String roleType,
            List<String> permissions) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("memberId", memberId);
        payload.put("roleType", roleType);
        payload.put("permissions", permissions);

        return payload;
    }

    private static Map<String, Object> policiesPayload(List<PurchasePolicy> policies) {
        Map<String, Object> payload = new LinkedHashMap<>();

        List<Map<String, Object>> items = policies.stream()
                .map(IntegrationExtController::policyToMap)
                .toList();

        payload.put("items", items);
        return payload;
    }

    private static Map<String, Object> policyToMap(PurchasePolicy policy) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("policyId", policy.id().value());
        payload.put("policyName", policy.policyName());
        payload.put("companyId", policy.companyId().value());
        payload.put("active", policy.isActive());
        payload.put("ownerType", policy.isEventPolicy() ? "EVENT" : "COMPANY");
        payload.put("scope", scopeToMap(policy.scope()));
        payload.put("summary", policy.policy() == null ? null : policy.policy().toString());

        return payload;
    }

    private static Map<String, Object> scopeToMap(PolicyScope scope) {
        if (scope == null) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("companyWide", scope.isCompanyWide());
        payload.put("eventIds", scope.eventIds()
                .stream()
                .map(EventId::value)
                .toList());

        return payload;
    }

    public record AddZoneRequest(String zoneName, BigDecimal price, String currency, int capacity,
                                 String zoneType, Integer rows, Integer seatsPerRow) {
        /** Backward-compatible constructor: standing zone with no seat layout. */
        public AddZoneRequest(String zoneName, BigDecimal price, String currency, int capacity) {
            this(zoneName, price, currency, capacity, "STANDING", null, null);
        }
    }

    public record UpdateEventRequest(String eventName, List<String> dates, String category,
                                     String location, String description) {}
}
