package com.eventsystem.domain.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.basic.NeverAllowPolicy;

import com.eventsystem.domain.company.CompanyId;




public class PurchasePolicy{

    private final PurchasePolicyId id;
    private String policyName;
    private final CompanyId companyId;
    private PolicyScope scope;
    private final IPolicy policy;


    public PurchasePolicy(PurchasePolicyId policyId, CompanyId companyId, String policyName, PolicyScope scope, IPolicy policy) {
        this.id = Objects.requireNonNull(policyId, "policy id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.policyName = Objects.requireNonNull(policyName, "policy name must not be null");
        this.scope = Objects.requireNonNull(scope, "policy scope must not be null");
        PolicyConflictDetector.requireValidPolicy(policy);

        this.policy = policy;
    }

    public PurchasePolicy(PurchasePolicyId policyId, CompanyId companyId, String policyName, PolicyScope scope, List<IPolicy> policies) {
        PolicyConflictDetector.requireValidPolicy(policies);
        this.id = Objects.requireNonNull(policyId, "policy id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.policyName = Objects.requireNonNull(policyName, "policy name must not be null");
        this.scope = Objects.requireNonNull(scope, "policy scope must not be null");

        this.policy = new AndPolicy(policies);
        
    }

    public PurchasePolicyId id() {
        return id;
    }

    public String policyName() {
        return policyName;
    }

    public CompanyId companyId() {
        return companyId;
    }

    public PolicyScope scope() {
        return scope;
    }

    public void setNameTo(String newName) {
        this.policyName = Objects.requireNonNull(newName, "policy name must not be null");
    }

    public void setScopeTo(PolicyScope newScope) {
        this.scope = Objects.requireNonNull(newScope, "policy scope must not be null");
    }

        public void setCompanyWide() {
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(true, affectedEvents);
    }

    public void deactivateCompanyWide() {
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(false, affectedEvents);
    }

    public void activateForEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Set<EventId> affectedEvents = new HashSet<>(scope.eventIds());
        affectedEvents.add(eventId);

        this.scope = new PolicyScope(scope.isCompanyWide(), affectedEvents);
    }

    public void deactivateForEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Set<EventId> affectedEvents = new HashSet<>(scope.eventIds());
        affectedEvents.remove(eventId);

        this.scope = new PolicyScope(scope.isCompanyWide(), affectedEvents);
    }

    public IPolicy policy() {
        return policy;
    }

    public static PurchasePolicy NewPurchasePolicy(CompanyId companyId, String policyName, IPolicy policy) {
        return new PurchasePolicy(PurchasePolicyId.random(), companyId, policyName, PolicyScope.clearScope(), policy);
    }

    public static PurchasePolicy NewPurchasePolicy(CompanyId companyId, String policyName, List<IPolicy> policies) {
        return new PurchasePolicy(PurchasePolicyId.random(), companyId, policyName, PolicyScope.clearScope(), policies);
    }


    public static PurchasePolicy AllowAll(PurchasePolicyId id, CompanyId companyId, String policyName) {
        return new PurchasePolicy(id, companyId, policyName, PolicyScope.clearScope(),AlwaysTruePolicy.INSTANCE);
    }

    public static PurchasePolicy NewAllowAllPolicy(CompanyId companyId, String policyName) {
        return PurchasePolicy.NewPurchasePolicy(companyId, policyName,AlwaysTruePolicy.INSTANCE);
    }

    public static PurchasePolicy NotAllowed(PurchasePolicyId id, CompanyId companyId, String policyName) {
        return new PurchasePolicy(id, companyId, policyName, PolicyScope.clearScope(),NeverAllowPolicy.INSTANCE);
    }

    public static PurchasePolicy NewNeverAllowedPolicy(CompanyId companyId, String policyName) {
        return PurchasePolicy.NewPurchasePolicy(companyId, policyName,NeverAllowPolicy.INSTANCE);
    }

    public static PurchasePolicy NewMaxTicketPolicy(CompanyId companyId, String policyName, int maxAllowedTickets) {
        return PurchasePolicy.NewPurchasePolicy(companyId, policyName,new MaxTicketPolicy(maxAllowedTickets));
    }
    
    public boolean isPurchaseAllowedInContext(PurchaseContext context) {
        return evaluate(context).isSuccess();
    }

    public void requirePurchasePolicy(PurchaseContext context) {
        PolicyValidationResult result = evaluate(context);

        if (!result.isSuccess()) {
            throw new PurchasePolicyException(
                    "Purchase policy violation: " + result.reason()
            );
        }
    }

    public PolicyValidationResult evaluate(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (context.ticketCount() <= 0) {
            return PolicyValidationResult.failure("Purchase context contains no tickets");
        }

        return policy.evaluate(context);
    }

    public boolean isActive() {
        return scope.isScopedToEventsOrCompany();
    }

    public boolean isActiveForEvent(EventId eventId) {
        return scope.appliesTo(eventId);
    }


}
