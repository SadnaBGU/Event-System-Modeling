package com.eventsystem.infrastructure.api.policy;

import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PolicyOwnerCommand;
import com.eventsystem.application.policy.policybuilder.PolicyRuleCommand;
import com.eventsystem.application.policy.policybuilder.PolicyScopeCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.EventStatus;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.PolicyBuilder;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.AfterDatePolicy;
import com.eventsystem.domain.policy.rule.basic.CodePolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.MinAgePolicy;
import com.eventsystem.domain.policy.rule.basic.MinTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.UntilDatePolicy;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class CompanyPolicyController {

    private static final String API_POLICY_NAME = "API policy";

    private final PolicyManagementService policyManagementService;
    private final IEventRepository eventRepository;

    public CompanyPolicyController(PolicyManagementService policyManagementService,
                                   IEventRepository eventRepository) {
        this.policyManagementService = Objects.requireNonNull(
                policyManagementService,
                "policyManagementService must not be null");
        this.eventRepository = Objects.requireNonNull(
                eventRepository,
                "eventRepository must not be null");
    }

    /**
     * Creates a company-owned company-wide purchase policy.
     *
     * This endpoint belongs to the COMPANY policy ownership path:
     * /companies/{companyId}/policies.
     *
     * The created policy should be mutable by company policy managers.
     */
    @PutMapping("/companies/{companyId}/policies")
    public ResponseEntity<Void> setCompanyPolicy(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String companyId,
            @RequestBody JsonNode requestBody) {
        Objects.requireNonNull(actor, "authenticatedMemberId must not be null");

        IPolicy policy = parsePolicyTree(requestBody);

        policyManagementService.createNewCompanyWidePurchasePolicy(
                actor,
                new CompanyId(companyId),
                API_POLICY_NAME,
                policy);

        return ResponseEntity.ok().build();
    }

    /**
     * Creates an event-owned purchase policy.
     *
     * This endpoint belongs to the EVENT policy ownership path:
     * /events/{eventId}/policies.
     *
     * The created policy is owned by this event, and its scope should stay fixed
     * to this event according to the new policy ownership model.
     */
    @PutMapping("/events/{eventId}/policies")
    public ResponseEntity<Void> setEventPolicy(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String eventId,
            @RequestBody JsonNode requestBody) {
        Objects.requireNonNull(actor, "authenticatedMemberId must not be null");

        requireEventEditable(new EventId(eventId));

        IPolicy policy = parsePolicyTree(requestBody);

        policyManagementService.createNewEventOwnedPurchasePolicy(
                actor,
                new EventId(eventId),
                API_POLICY_NAME,
                policy);

        return ResponseEntity.ok().build();
    }

    // ── Discount policies ────────────────────────────────────────────────────

    @PutMapping("/companies/{companyId}/discount-policies")
    public ResponseEntity<Void> setCompanyDiscountPolicy(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                         @PathVariable String companyId,
                                                         @RequestBody DiscountPolicyRequest request) {
        PolicyScopeCommand scope = new PolicyScopeCommand(true, Set.of());
        policyManagementService.createDiscountPolicy(toCommand(actor.value(), companyId, scope, request));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/events/{eventId}/discount-policies")
    public ResponseEntity<Void> setEventDiscountPolicy(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                       @PathVariable String eventId,
                                                       @RequestBody DiscountPolicyRequest request) {
        Event event = eventRepository.findById(new EventId(eventId))
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId));
        requireDraft(event);
        PolicyScopeCommand scope = new PolicyScopeCommand(false, Set.of(eventId));
        policyManagementService.createDiscountPolicy(
                toCommand(actor.value(), event.companyId().value(), scope, request));
        return ResponseEntity.ok().build();
    }

    private DiscountPolicyCommand toCommand(String actorId, String companyId,
                                            PolicyScopeCommand scope, DiscountPolicyRequest request) {
        if (request == null || request.discounts() == null || request.discounts().isEmpty()) {
            throw new IllegalArgumentException("at least one discount is required");
        }
        List<DiscountCommand> discounts = new ArrayList<>();
        for (DiscountItem item : request.discounts()) {
            if (item.name() == null || item.name().isBlank()) {
                throw new IllegalArgumentException("each discount needs a name");
            }
            if (item.percent() == null || item.percent() <= 0 || item.percent() > 100) {
                throw new IllegalArgumentException("discount percent must be between 1 and 100");
            }
            boolean isCoupon = item.code() != null && !item.code().isBlank();
            PolicyRuleCommand rule;
            String visibility;
            if (isCoupon) {
                rule = new PolicyRuleCommand("CODE", null, item.code().trim(), null, null, null);
                visibility = "HIDDEN";
            } else if (item.minTickets() != null && item.minTickets() > 0) {
                rule = new PolicyRuleCommand("MIN_TICKETS", item.minTickets(), null, null, null, null);
                visibility = "VISIBLE";
            } else {
                rule = new PolicyRuleCommand("ALLOW_ALL", null, null, null, null, null);
                visibility = "VISIBLE";
            }
            String endDate = (item.endDate() == null || item.endDate().isBlank()) ? null : item.endDate();
            discounts.add(new DiscountCommand(item.name().trim(),
                    BigDecimal.valueOf(item.percent()), rule, visibility, endDate));
        }
        String policyName = (request.policyName() == null || request.policyName().isBlank())
                ? "API discount policy" : request.policyName();
        PolicyOwnerCommand ownerType = scope.companyWide()
                ? PolicyOwnerCommand.COMPANY : PolicyOwnerCommand.EVENT;
        return new DiscountPolicyCommand(actorId, companyId, policyName, scope,
                discounts, request.stackable(), true, ownerType);
    }

    public record DiscountPolicyRequest(String policyName, boolean stackable, List<DiscountItem> discounts) {}

    public record DiscountItem(String name, Double percent, String code, Integer minTickets, String endDate) {}

    /** Loads the event and rejects the edit unless it is still a draft (V3: no policy edits after publish). */
    private void requireEventEditable(EventId eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId.value()));
        requireDraft(event);
    }

    private void requireDraft(Event event) {
        if (event.status() != EventStatus.DRAFT) {
            throw new IllegalStateException(
                    "event policies can only be modified before the event is published");
        }
    }

    private IPolicy parsePolicyTree(JsonNode node) {
        Objects.requireNonNull(node, "requestBody must not be null");

        String type = text(node, "type").toUpperCase();

        return switch (type) {
            case "AND" -> PolicyBuilder.and(parseOperands(node));
            case "OR" -> PolicyBuilder.or(parseOperands(node));

            case "MIN_AGE" -> new MinAgePolicy(intValue(node, "value"));

            case "MAX_TICKETS",
                 "MAX_TICKETS_PER_USER",
                 "MAX_TICKETS_PER_ORDER" -> new MaxTicketPolicy(intValue(node, "value"));

            case "MIN_TICKETS",
                 "MIN_TICKETS_PER_USER",
                 "MIN_TICKETS_PER_ORDER" -> new MinTicketPolicy(intValue(node, "value"));

            case "CODE",
                 "REQUIRES_CODE" -> new CodePolicy(text(node, "value"));

            case "BEFORE_DATE",
                 "UNTIL_DATE" -> new UntilDatePolicy(LocalDate.parse(text(node, "value")));

            case "AFTER_DATE",
                 "FROM_DATE" -> new AfterDatePolicy(LocalDate.parse(text(node, "value")));

            default -> throw new IllegalArgumentException("unsupported policy type: " + type);
        };
    }

    private IPolicy[] parseOperands(JsonNode node) {
        JsonNode operands = node.get("operands");

        if (operands == null || !operands.isArray() || operands.isEmpty()) {
            throw new IllegalArgumentException("policy operands must be a non-empty array");
        }

        List<IPolicy> parsed = new ArrayList<>();

        for (JsonNode operand : operands) {
            parsed.add(parsePolicyTree(operand));
        }

        return parsed.toArray(IPolicy[]::new);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);

        if (field == null || field.isNull()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        String value = field.asText();

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }

    private int intValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);

        if (field == null || field.isNull() || !field.canConvertToInt()) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }

        return field.asInt();
    }
}