package com.eventsystem.infrastructure.api.policy;

import com.fasterxml.jackson.databind.JsonNode;
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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class CompanyPolicyController {

    private final PolicyManagementService policyManagementService;

    public CompanyPolicyController(PolicyManagementService policyManagementService) {
        this.policyManagementService = policyManagementService;
    }

    @PutMapping("/companies/{companyId}/policies")
    public ResponseEntity<Void> setCompanyPolicy(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                                 @PathVariable String companyId,
                                                 @RequestBody JsonNode requestBody) {
        IPolicy policy = parsePolicyTree(requestBody);
        policyManagementService.createNewCompanyWidePurchasePolicy(actor, new CompanyId(companyId), "API policy", policy);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/events/{eventId}/policies")
    public ResponseEntity<Void> setEventPolicy(@RequestAttribute("authenticatedMemberId") MemberId actor,
                                               @PathVariable String eventId,
                                               @RequestBody JsonNode requestBody) {
        IPolicy policy = parsePolicyTree(requestBody);
        policyManagementService.createNewEventOwnedPurchasePolicy(actor, new EventId(eventId), "API policy", policy);
        return ResponseEntity.ok().build();
    }

    private IPolicy parsePolicyTree(JsonNode node) {
        Objects.requireNonNull(node, "requestBody must not be null");
        String type = text(node, "type").toUpperCase();

        return switch (type) {
            case "AND" -> PolicyBuilder.and(parseOperands(node));
            case "OR" -> PolicyBuilder.or(parseOperands(node));
            case "MIN_AGE" -> new MinAgePolicy(intValue(node, "value"));
            case "MAX_TICKETS_PER_USER" -> new MaxTicketPolicy(intValue(node, "value"));
            case "MIN_TICKETS", "MIN_TICKETS_PER_USER" -> new MinTicketPolicy(intValue(node, "value"));
            case "CODE" -> new CodePolicy(text(node, "value"));
            case "BEFORE_DATE" -> new UntilDatePolicy(LocalDate.parse(text(node, "value")));
            case "AFTER_DATE" -> new AfterDatePolicy(LocalDate.parse(text(node, "value")));
            default -> throw new IllegalArgumentException("unsupported policy type: " + type);
        };
    }

    private IPolicy[] parseOperands(JsonNode node) {
        JsonNode operands = node.get("operands");
        if (operands == null || !operands.isArray() || operands.isEmpty()) {
            throw new IllegalArgumentException("policy operands must be a non-empty array");
        }

        List<IPolicy> parsed = new java.util.ArrayList<>();
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
        return value;
    }

    private int intValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.canConvertToInt()) {
            throw new IllegalArgumentException(fieldName + " must be an integer");
        }
        return field.asInt();
    }
}