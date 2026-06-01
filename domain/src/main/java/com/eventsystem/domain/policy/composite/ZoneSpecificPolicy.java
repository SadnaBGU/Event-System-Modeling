package com.eventsystem.domain.policy.composite;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.policy.IPolicy;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.zone.ZoneId;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ZoneSpecificPolicy implements ICompositePolicy {

    private final IPolicy policy;
    private final Set<ZoneId> affectedZones;
    private final boolean noAffectedTicketsPass;

    public ZoneSpecificPolicy(Set<ZoneId> affectedZones, IPolicy policy, boolean passWhenNoAffectedTickets) {
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