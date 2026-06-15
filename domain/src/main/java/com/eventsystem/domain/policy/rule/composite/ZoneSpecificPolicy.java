package com.eventsystem.domain.policy.rule.composite;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.PolicyType;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.zone.ZoneId;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ZoneSpecificPolicy implements ICompositePolicy {
    @JsonProperty("policy")
    private final IPolicy policy;
    @JsonProperty("affectedZones")
    private final Set<ZoneId> affectedZones;
    @JsonProperty("noAffectedTicketsPass")
    private final boolean noAffectedTicketsPass;

    public ZoneSpecificPolicy(@JsonProperty("affectedZones") Set<ZoneId> affectedZones,
            @JsonProperty("policy") IPolicy policy,
            @JsonProperty("noAffectedTicketsPass") boolean passWhenNoAffectedTickets) {
        Objects.requireNonNull(affectedZones, "affectedZones must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        if (affectedZones.isEmpty()) {
            throw new PolicyException("ZoneSpecificPolicy must contain at least one affected zone");
        }

        if (affectedZones.stream().anyMatch(Objects::isNull)) {
            throw new PolicyException("ZoneSpecificPolicy cannot contain null zones");
        }

        this.affectedZones = Set.copyOf(affectedZones);
        this.policy = policy;
        this.noAffectedTicketsPass = passWhenNoAffectedTickets;
    }

        @Override
    public PolicyType type() {
        return noAffectedTicketsPass 
                ? PolicyType.ZONE_SPECIFIC_0_PASS
                : PolicyType.ZONE_SPECIFIC_0_FAIL;
    }

    public ZoneSpecificPolicy(Set<ZoneId> affectedZones, List<IPolicy> policies, boolean noAffectedTicketsPass) {
        this(affectedZones, new AndPolicy(policies), noAffectedTicketsPass);
    }

    @Override
    public PolicyValidationResult evaluate(PurchaseContext context) {
        PurchaseContext relevantContext = generateContextForAffectedZones(context);

        if (relevantContext.zonesOfEachEventTicket().isEmpty()) {
            if (noAffectedTicketsPass) {
                return PolicyValidationResult.success();
            }

            return PolicyValidationResult.failure(String.format(
                    "No tickets purchased for zones %s",
                    affectedZones
            ));
        }

        PolicyValidationResult result = policy.evaluate(relevantContext);

        if (result.isSuccess()) {
            return PolicyValidationResult.success();
        }

        return PolicyValidationResult.failure(String.format(
                "Purchase policy violation for zones %s: %s",
                affectedZones,
                result.reason()
        ));
    }

    private List<ZoneId> listOnlyTicketsOfAffectedZones(PurchaseContext context) {
        return context.zonesOfEachEventTicket()
                .stream()
                .filter(affectedZones::contains)
                .collect(Collectors.toList());
    }

    private PurchaseContext generateContextForAffectedZones(PurchaseContext context) {
        return new PurchaseContext(
                context.eventId(),
                context.companyId(),
                listOnlyTicketsOfAffectedZones(context),
                context.buyerBirthDate(),
                context.purchaseDate(),
                context.discountCode()
        );
    }

    @Override
    public List<IPolicy> children() {
        return List.of(policy);
    }
}