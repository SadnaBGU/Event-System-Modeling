package com.eventsystem.domain.policy.rule;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "policyClass"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy.class, name = "MaxTicketPolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.MinTicketPolicy.class, name = "MinTicketPolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.MinAgePolicy.class, name = "MinAgePolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.UntilDatePolicy.class, name = "UntilDatePolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.AfterDatePolicy.class, name = "AfterDatePolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.CodePolicy.class, name = "CodePolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy.class, name = "AlwaysTruePolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.basic.NeverAllowPolicy.class, name = "NeverAllowPolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.composite.AndPolicy.class, name = "AndPolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.composite.OrPolicy.class, name = "OrPolicy"),
        @JsonSubTypes.Type(value = com.eventsystem.domain.policy.rule.composite.ZoneSpecificPolicy.class, name = "ZoneSpecificPolicy")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface IPolicy {

    @JsonIgnore
    default PolicyType type() {
        return PolicyType.UNKNOWN;
    }

    PolicyValidationResult evaluate(PurchaseContext context);

    @JsonIgnore
    default boolean validate(PurchaseContext context) {
        return evaluate(context).isSuccess();
    }

    default void require(PurchaseContext context) {
        PolicyValidationResult result = evaluate(context);
        if (!result.isSuccess()) {
            throw new PurchasePolicyException(result.reason());
        }
    }

    @JsonIgnore
    boolean isValidPolicy();

    @JsonIgnore
    boolean isComposite();
}