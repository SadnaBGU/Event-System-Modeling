package com.eventsystem.infrastructure.api.policy;

import com.eventsystem.application.policy.PolicyManagementService;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class CompanyPolicyController {

    private static final String API_POLICY_NAME = "API policy";

    private final PolicyManagementService policyManagementService;

    public CompanyPolicyController(PolicyManagementService policyManagementService) {
        this.policyManagementService = Objects.requireNonNull(
                policyManagementService,
                "policyManagementService must not be null");
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

        IPolicy policy = parsePolicyTree(requestBody);

        policyManagementService.createNewEventOwnedPurchasePolicy(
                actor,
                new EventId(eventId),
                API_POLICY_NAME,
                policy);

        return ResponseEntity.ok().build();
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