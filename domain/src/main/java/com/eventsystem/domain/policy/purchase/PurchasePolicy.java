package com.eventsystem.domain.policy.purchase;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.PolicyConflictDetector;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.rule.basic.MaxTicketPolicy;
import com.eventsystem.domain.policy.rule.basic.NeverAllowPolicy;
import com.eventsystem.domain.policy.rule.composite.AndPolicy;
import com.eventsystem.domain.policy.shared.PolicyOwnerType;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.company.CompanyId;

@Entity
@Table(name = "purchase_policies")
public class PurchasePolicy {

    @EmbeddedId
    private PurchasePolicyId id;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "company_id", nullable = false))
    })
    private CompanyId companyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_scope", columnDefinition = "jsonb")
    private PolicyScope scope;

    // כאן כל העץ המורכב נשמר כגוש JSON אחד!
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_tree", columnDefinition = "jsonb")
    private IPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private PolicyOwnerType ownerType = PolicyOwnerType.COMPANY;

    @Version
    private Long version;

    // חובה עבור JPA
    protected PurchasePolicy() {
    }

    public PurchasePolicy(PurchasePolicyId policyId, CompanyId companyId, String policyName,
            PolicyScope scope, IPolicy policy, PolicyOwnerType ownerType) {
        this.id = Objects.requireNonNull(policyId, "policy id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.policyName = Objects.requireNonNull(policyName, "policy name must not be null");
        this.scope = Objects.requireNonNull(scope, "policy scope must not be null");
        this.ownerType = Objects.requireNonNull(ownerType, "policy owner type must not be null");

        requireValidOwnerAndScope(this.ownerType, this.scope);
        PolicyConflictDetector.requireValidPolicy(policy);

        this.policy = policy;
    }

    public PurchasePolicy(PurchasePolicyId policyId, CompanyId companyId, String policyName, PolicyScope scope,
            IPolicy policy) {
        this.id = Objects.requireNonNull(policyId, "policy id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.policyName = Objects.requireNonNull(policyName, "policy name must not be null");
        this.scope = Objects.requireNonNull(scope, "policy scope must not be null");
        this.ownerType = PolicyOwnerType.COMPANY;
        PolicyConflictDetector.requireValidPolicy(policy);

        this.policy = policy;
    }

    public PurchasePolicy(PurchasePolicyId policyId, CompanyId companyId, String policyName, PolicyScope scope,
            List<IPolicy> policies, PolicyOwnerType ownerType) {
        PolicyConflictDetector.requireValidPolicy(policies);
        this.id = Objects.requireNonNull(policyId, "policy id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.policyName = Objects.requireNonNull(policyName, "policy name must not be null");
        this.scope = Objects.requireNonNull(scope, "policy scope must not be null");
        this.ownerType = Objects.requireNonNull(ownerType, "policy ownerType must not be null");
        this.policy = new AndPolicy(policies);
        requireValidOwnerAndScope(this.ownerType, this.scope);

    }

    public PurchasePolicy(PurchasePolicyId policyId, CompanyId companyId, String policyName, PolicyScope scope,
            List<IPolicy> policies) {
        PolicyConflictDetector.requireValidPolicy(policies);
        this.id = Objects.requireNonNull(policyId, "policy id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "company id must not be null");
        this.policyName = Objects.requireNonNull(policyName, "policy name must not be null");
        this.scope = Objects.requireNonNull(scope, "policy scope must not be null");

        this.policy = new AndPolicy(policies);

    }

    public PurchasePolicy(CompanyId companyId, String policyName, PolicyScope scope, IPolicy policy) {
        this(PurchasePolicyId.random(), companyId, policyName, scope, policy);
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
        requireMutableScope();
        this.scope = Objects.requireNonNull(newScope, "policy scope must not be null");
    }

    public void setCompanyWide() {
        Set<EventId> affectedEvents = scope.eventIds();
        requireMutableScope();
        this.scope = new PolicyScope(true, affectedEvents);
    }

    public void deactivateCompanyWide() {
        requireMutableScope();
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(false, affectedEvents);
    }

    public void activateForEvent(EventId eventId) {
        requireMutableScope();
        Objects.requireNonNull(eventId, "eventId must not be null");

        Set<EventId> affectedEvents = new HashSet<>(scope.eventIds());
        affectedEvents.add(eventId);

        this.scope = new PolicyScope(scope.isCompanyWide(), affectedEvents);
    }

    public void deactivateForEvent(EventId eventId) {
        requireMutableScope();
        Objects.requireNonNull(eventId, "eventId must not be null");

        Set<EventId> affectedEvents = new HashSet<>(scope.eventIds());
        affectedEvents.remove(eventId);

        this.scope = new PolicyScope(scope.isCompanyWide(), affectedEvents);
    }

    public IPolicy policy() {
        return policy;
    }

    public static PurchasePolicy emptyScope(CompanyId companyId, String policyName, IPolicy policy) {
        return new PurchasePolicy(PurchasePolicyId.random(), companyId, policyName, PolicyScope.clearScope(), policy);
    }

    public static PurchasePolicy emptyScope(CompanyId companyId, String policyName, List<IPolicy> policies) {
        return new PurchasePolicy(PurchasePolicyId.random(), companyId, policyName, PolicyScope.clearScope(), policies);
    }

    public static PurchasePolicy newAllowAllPolicy(CompanyId companyId, String policyName) {
        return PurchasePolicy.emptyScope(companyId, policyName, AlwaysTruePolicy.INSTANCE);
    }

    public static PurchasePolicy newNeverAllowedPolicy(CompanyId companyId, String policyName) {
        return PurchasePolicy.emptyScope(companyId, policyName, NeverAllowPolicy.INSTANCE);
    }

    public static PurchasePolicy newMaxTicketPolicy(CompanyId companyId, String policyName, int maxAllowedTickets) {
        return PurchasePolicy.emptyScope(companyId, policyName, new MaxTicketPolicy(maxAllowedTickets));
    }

    public static PurchasePolicy companyPolicy(CompanyId companyId, String policyName, PolicyScope scope,
            IPolicy policy) {
        return new PurchasePolicy(PurchasePolicyId.random(), companyId, policyName,
                                    scope, policy, PolicyOwnerType.COMPANY);
    }

    public static PurchasePolicy eventPolicy(CompanyId companyId, EventId eventId, String policyName, IPolicy policy) {
        return new PurchasePolicy(PurchasePolicyId.random(), companyId, policyName,
                                    PolicyScope.forSingleEvent(eventId), policy, PolicyOwnerType.EVENT);
    }

    public boolean isPurchaseAllowedInContext(PurchaseContext context) {
        return evaluate(context).isSuccess();
    }

    public void requirePurchasePolicy(PurchaseContext context) {
        PolicyValidationResult result = evaluate(context);

        if (!result.isSuccess()) {
            throw new PurchasePolicyException(
                    "Purchase policy violation: " + result.reason());
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

    public boolean isSingleEventPolicy() {
        return !scope.isCompanyWide() && scope.eventIds().size() == 1;
    }

    public boolean isSpecificFor(EventId eventId) {
        return !scope.isCompanyWide() && scope.eventIds().size() == 1 && scope.isListedIn(eventId);
    }

    public boolean isCompanyPolicy() {
        return ownerType == PolicyOwnerType.COMPANY;
    }

    public boolean isEventPolicy() {
        return ownerType == PolicyOwnerType.EVENT;
    }

    public boolean appliesTo(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!companyId.equals(context.companyId())) {
            return false;
        }

        return scope.appliesTo(context.eventId());
    }

    private void requireMutableScope() {
        if (isEventPolicy()) {
            throw new PurchasePolicyException("Event-owned purchase policy scope cannot be changed");
        }
    }

    private static void requireValidOwnerAndScope(PolicyOwnerType ownerType, PolicyScope scope) {
        Objects.requireNonNull(ownerType, "policy owner type must not be null");
        Objects.requireNonNull(scope, "policy scope must not be null");

        if ((ownerType == PolicyOwnerType.EVENT) && !scope.isForSingleEvent()) {
            throw new PurchasePolicyException(
                    "Event-owned purchase policy must be scoped to exactly one event");
        }
    }
}
