package com.eventsystem.domain.policy.discount;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.rule.IPolicy;
import com.eventsystem.domain.policy.rule.composite.ICompositePolicy;
import com.eventsystem.domain.policy.rule.composite.ZoneSpecificPolicy;
import com.eventsystem.domain.policy.shared.PolicyOwnerType;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "discount_policies")
public final class DiscountPolicy {

    @EmbeddedId
    private DiscountPolicyId id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "company_id", nullable = false))
    })
    private CompanyId companyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_scope", columnDefinition = "jsonb")
    private PolicyScope scope;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discount_policy_discounts", joinColumns = @JoinColumn(name = "discount_policy_id"))
    private List<Discount> discounts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private PolicyOwnerType ownerType = PolicyOwnerType.COMPANY;

    private boolean stackable;
    private boolean active;

    @Version
    private Long version;

    protected DiscountPolicy() {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DiscountPolicy(
            DiscountPolicyId id,
            CompanyId companyId,
            PolicyScope scope,
            List<Discount> discounts,
            boolean isStackable,
            boolean isActive,
            PolicyOwnerType ownerType
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>(Objects.requireNonNull(discounts, "discounts must not be null"));

        if (discounts.stream().anyMatch(Objects::isNull)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }

        this.stackable = isStackable;
        this.active = isActive;
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType must not be null");

        requireValidOwnerAndScope(ownerType, scope);
        requireValidDiscountPolicy();
    }

    public DiscountPolicy(
            CompanyId companyId,
            PolicyScope scope,
            List<Discount> discounts,
            boolean isStackable,
            boolean isActive
    ) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>(Objects.requireNonNull(discounts, "discounts must not be null"));

        if (discounts.stream().anyMatch(Objects::isNull)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }

        this.id = DiscountPolicyId.random();
        this.stackable = isStackable;
        this.active = isActive;
        this.ownerType = PolicyOwnerType.COMPANY;
    }

    public DiscountPolicy(
            DiscountPolicyId id,
            CompanyId companyId,
            EventId specificEventId,
            PolicyOwnerType ownerType
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = PolicyScope.forSingleEvent(specificEventId);
        this.discounts = new ArrayList<>();
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType must not be null");
        this.stackable = false;
        this.active = false;

        requireValidOwnerAndScope(ownerType, scope);
    }

    public DiscountPolicy(DiscountPolicyId id, CompanyId companyId, PolicyScope scope) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>();
        this.stackable = false;
        this.active = false;
        this.ownerType = PolicyOwnerType.COMPANY;
    }

    public DiscountPolicy(CompanyId cid) {
        this(DiscountPolicyId.random(), cid, PolicyScope.clearScope());
    }

    public static DiscountPolicy clearScope(CompanyId companyId) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, PolicyScope.clearScope());
    }

    public static DiscountPolicy inactiveCompanyWide(CompanyId companyId) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, PolicyScope.companyWideScope());
    }

    public static DiscountPolicy inactiveForEvents(CompanyId companyId, Set<EventId> eventIds) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, PolicyScope.forEvents(eventIds));
    }

    public static DiscountPolicy inactiveEventOwnedPolicy(CompanyId companyId, EventId eventId) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, eventId, PolicyOwnerType.EVENT);
    }

    public static DiscountPolicy eventPolicy(
            CompanyId companyId,
            EventId eventId,
            List<Discount> discounts,
            boolean stackable,
            boolean active
    ) {
        return new DiscountPolicy(
                DiscountPolicyId.random(),
                companyId,
                PolicyScope.forSingleEvent(eventId),
                discounts,
                stackable,
                active,
                PolicyOwnerType.EVENT
        );
    }

    public static DiscountPolicy companyPolicy(
            CompanyId companyId,
            PolicyScope scope,
            List<Discount> discounts,
            boolean stackable,
            boolean active
    ) {
        return new DiscountPolicy(
                DiscountPolicyId.random(),
                companyId,
                scope,
                discounts,
                stackable,
                active,
                PolicyOwnerType.COMPANY
        );
    }

    public DiscountPolicyId id() {
        return id;
    }

    public CompanyId companyId() {
        return companyId;
    }

    public PolicyScope scope() {
        return scope;
    }

    public boolean isStackable() {
        return stackable;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        requireValidDiscountPolicy();
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isCompanyPolicy() {
        return ownerType == PolicyOwnerType.COMPANY;
    }

    public boolean isEventPolicy() {
        return ownerType == PolicyOwnerType.EVENT;
    }

    private void requireMutableScope() {
        if (isEventPolicy()) {
            throw new DiscountPolicyException("Event-owned discount policy scope cannot be changed");
        }
    }

    private static void requireValidOwnerAndScope(PolicyOwnerType ownerType, PolicyScope scope) {
        Objects.requireNonNull(ownerType, "policy owner type must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        if (ownerType == PolicyOwnerType.EVENT && !scope.isForSingleEvent()) {
            throw new DiscountPolicyException(
                    "Event-owned discount policy must be scoped to exactly one event");
        }
    }

    public void changeScope(PolicyScope scope) {
        requireMutableScope();
        this.scope = Objects.requireNonNull(scope, "scope must not be null");

        if (!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public void allowStacking() {
        this.stackable = true;
    }

    public void disallowStacking() {
        this.stackable = false;
    }

    public void addDiscount(Discount discount) {
        discounts.add(Objects.requireNonNull(discount, "discount must not be null"));
    }

    public boolean appliesTo(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!active) {
            return false;
        }

        if (!companyId.equals(context.companyId())) {
            return false;
        }

        return scope.appliesTo(context.eventId());
    }

    public void setCompanyWide() {
        requireMutableScope();
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(true, affectedEvents);
    }

    public void deactivateCompanyWide() {
        requireMutableScope();
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(false, affectedEvents);

        if (!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
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

        if (!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public void clearAllEventsFromScope() {
        requireMutableScope();
        this.scope = new PolicyScope(scope.isCompanyWide(), Set.of());

        if (!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public List<Discount> discounts() {
        return List.copyOf(discounts);
    }

    public List<Discount> visibleDiscounts() {
        return visibleDiscounts(LocalDate.now());
    }

    public List<Discount> visibleDiscounts(LocalDate now) {
        return discounts.stream()
                .filter(Discount::isVisible)
                .filter(discount -> !discount.isExpired(now))
                .toList();
    }

    public List<Discount> expiredDiscounts() {
        return discounts.stream()
                .filter(Discount::isExpired)
                .toList();
    }

    public List<Discount> nonExpiredDiscounts() {
        return discounts.stream()
                .filter(discount -> !discount.isExpired())
                .toList();
    }

    public List<Discount> applicableDiscounts(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!appliesTo(context)) {
            return List.of();
        }

        return discounts.stream()
                .filter(discount -> !discount.isExpired(context.purchaseDate()))
                .filter(discount -> discount.validateDiscount(context))
                .toList();
    }

    public boolean doesHaveVisibleDiscounts() {
        return !visibleDiscounts().isEmpty();
    }

    public List<Discount> visibleDiscounts(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!appliesTo(context)) {
            return List.of();
        }

        return discounts.stream()
                .filter(Discount::isVisible)
                .filter(discount -> !discount.isExpired(context.purchaseDate()))
                .filter(discount -> discount.validateDiscount(context))
                .toList();
    }

    public Set<EventId> discountedEventIds() {
        return scope.eventIds();
    }

    public boolean isValidDiscountPolicy() {
        return scope.isScopedToEventsOrCompany() && !discounts.isEmpty();
    }

    public void requireValidDiscountPolicy() {
        if (!scope.isScopedToEventsOrCompany()) {
            throw new DiscountPolicyException("Discount is not related to an Event or Company wide");
        }

        if (discounts.isEmpty()) {
            throw new DiscountPolicyException("No Discounts are related to this Discount Policy");
        }
    }

    public boolean isPurchaseEligibleForSpecificDiscount(PurchaseContext context, String discountName) {
        if (!appliesTo(context)) {
            return false;
        }

        Discount foundDiscount = null;

        for (Discount discount : discounts) {
            if (discount.getDiscountName().equals(discountName)) {
                foundDiscount = discount;
                break;
            }
        }

        if (foundDiscount == null) {
            return false;
        }

        return !foundDiscount.isExpired(context.purchaseDate()) && foundDiscount.validateDiscount(context);
    }

    public boolean isPurchaseEligibleForDiscount(PurchaseContext context) {
        return evaluateIfDiscountApplyForPurchase(context).isSuccess();
    }

    public PolicyValidationResult evaluateIfDiscountApplyForPurchase(PurchaseContext context) {
        if (!appliesTo(context)) {
            return PolicyValidationResult.failure("No discount apply to the current purchase context");
        }

        for (Discount discount : discounts) {
            PolicyValidationResult validationResult = discount.evaluateDiscount(context);

            if (validationResult.isSuccess()
                    && discount.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                return PolicyValidationResult.success();
            }
        }

        return PolicyValidationResult.failure("No discount apply to the current purchase context");
    }

    private DiscountSummary getBestDiscountSummary(PurchaseContext context) {
        BigDecimal best = BigDecimal.ZERO;
        ArrayList<String> bestDiscountName = new ArrayList<>();
        ArrayList<BigDecimal> bestDiscountPercent = new ArrayList<>();
        ArrayList<BigDecimal> actualAmountPlaceholder = new ArrayList<>();

        for (Discount discount : discounts) {
            BigDecimal current = discount.getDiscountPercentForContext(context);

            if (current.compareTo(best) > 0) {
                best = current;

                if (bestDiscountName.isEmpty()) {
                    bestDiscountName.add(discount.getDiscountName());
                    bestDiscountPercent.add(current);
                    actualAmountPlaceholder.add(BigDecimal.ZERO);
                } else {
                    bestDiscountName.set(0, discount.getDiscountName());
                    bestDiscountPercent.set(0, current);
                }

                if (best.compareTo(HUNDRED) > 0) {
                    break;
                }
            }
        }

        return new DiscountSummary(
                bestDiscountName,
                bestDiscountPercent,
                actualAmountPlaceholder,
                BigDecimal.ZERO,
                DiscountSummary.shouldCapDiscountAt100(bestDiscountPercent)
        );
    }

    private DiscountSummary getStackedDiscountSummary(PurchaseContext context) {
        BigDecimal totalPercents = BigDecimal.ZERO;
        ArrayList<String> appliedDiscountNames = new ArrayList<>();
        ArrayList<BigDecimal> appliedDiscountPercents = new ArrayList<>();
        ArrayList<BigDecimal> actualAmountPlaceholder = new ArrayList<>();

        for (Discount discount : discounts) {
            BigDecimal current = discount.getDiscountPercentForContext(context);

            if (current.compareTo(BigDecimal.ZERO) > 0) {
                totalPercents = totalPercents.add(current);
                appliedDiscountNames.add(discount.getDiscountName());
                appliedDiscountPercents.add(current);
                actualAmountPlaceholder.add(BigDecimal.ZERO);

                if (totalPercents.compareTo(HUNDRED) > 0) {
                    break;
                }
            }
        }

        return new DiscountSummary(
                appliedDiscountNames,
                appliedDiscountPercents,
                actualAmountPlaceholder,
                BigDecimal.ZERO,
                DiscountSummary.shouldCapDiscountAt100(appliedDiscountPercents)
        );
    }

    public DiscountSummary getFullDiscountSummary(PurchaseContext context, Money baseCost) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseCost, "baseCost must not be null");

        if (!appliesTo(context)) {
            return DiscountSummary.noDiscountSummary();
        }

        List<DiscountApplicationCandidate> candidates = discounts.stream()
                .map(discount -> toDiscountCandidate(discount, context, baseCost))
                .filter(candidate -> candidate.actualAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (candidates.isEmpty()) {
            return DiscountSummary.noDiscountSummary();
        }

        List<DiscountApplicationCandidate> appliedCandidates = stackable
                ? candidates
                : List.of(bestActualAmountCandidate(candidates));

        BigDecimal totalActualDiscount = appliedCandidates.stream()
                .map(DiscountApplicationCandidate::actualAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(baseCost.amount());

        return new DiscountSummary(
                appliedCandidates.stream()
                        .map(DiscountApplicationCandidate::discountName)
                        .toList(),
                appliedCandidates.stream()
                        .map(DiscountApplicationCandidate::discountPercent)
                        .toList(),
                appliedCandidates.stream()
                        .map(DiscountApplicationCandidate::actualAmount)
                        .toList(),
                totalActualDiscount,
                DiscountSummary.shouldCapDiscountAt100(
                        appliedCandidates.stream()
                                .map(DiscountApplicationCandidate::discountPercent)
                                .toList()
                )
        );
    }

    public BigDecimal getFullDiscountPercent(PurchaseContext context) {
        if (!appliesTo(context)) {
            return BigDecimal.ZERO;
        }

        DiscountSummary summary = stackable
                ? getStackedDiscountSummary(context)
                : getBestDiscountSummary(context);

        return summary.appliedDiscountPercents()
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(HUNDRED);
    }

    public static DiscountSnapshot discountSnapshotFromSummary(DiscountSummary summary, Money baseCost) {
        return summary.generateDiscountSnapshot(baseCost);
    }

    public DiscountSnapshot generateDiscountSnapshot(PurchaseContext context, Money baseCost) {
        DiscountSummary summary = getFullDiscountSummary(context, baseCost);
        return discountSnapshotFromSummary(summary, baseCost);
    }

    public List<DiscountInfo> getDiscountInfos() {
        return discounts.stream()
                .map(Discount::info)
                .toList();
    }

    public List<DiscountInfo> activeVisibleDiscountsInfo() {
        return !active
                ? List.of()
                : visibleDiscounts().stream()
                        .map(Discount::info)
                        .toList();
    }

    public LocalDate dateOfLatestActiveDiscount(boolean requireVisible) {
        if (discounts.isEmpty()) {
            throw new PolicyException("No discounts in current discount policy");
        }

        List<Discount> toCheck = requireVisible
                ? discounts.stream().filter(Discount::isVisible).toList()
                : discounts();

        if (toCheck.isEmpty()) {
            throw new PolicyException("No matching discounts in current discount policy");
        }

        LocalDate latest = LocalDate.of(0, 1, 1);

        for (Discount discount : toCheck) {
            LocalDate endDate = discount.getEndDate();

            if (endDate == null) {
                return null;
            }

            if (endDate.isAfter(latest)) {
                latest = endDate;
            }
        }

        return latest;
    }

    private record DiscountApplicationCandidate(
            String discountName,
            BigDecimal discountPercent,
            BigDecimal actualAmount
    ) {
    }

    private DiscountApplicationCandidate toDiscountCandidate(
            Discount discount,
            PurchaseContext context,
            Money baseCost
    ) {
        BigDecimal discountPercent = discount.getDiscountPercentForContext(context);

        if (discountPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return new DiscountApplicationCandidate(
                    discount.getDiscountName(),
                    discountPercent,
                    BigDecimal.ZERO
            );
        }

        BigDecimal eligibleSubtotal = eligibleSubtotalForDiscount(discount, context, baseCost);

        if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new DiscountApplicationCandidate(
                    discount.getDiscountName(),
                    discountPercent,
                    BigDecimal.ZERO
            );
        }

        BigDecimal actualAmount = eligibleSubtotal
                .multiply(discountPercent)
                .divide(HUNDRED);

        return new DiscountApplicationCandidate(
                discount.getDiscountName(),
                discountPercent,
                actualAmount
        );
    }

    private BigDecimal eligibleSubtotalForDiscount(
            Discount discount,
            PurchaseContext context,
            Money baseCost
    ) {
        Set<ZoneId> targetZones = extractTargetZones(discount.policy());

        if (targetZones.isEmpty()) {
            return baseCost.amount();
        }

        return context.subtotalForZones(targetZones).amount();
    }

    private Set<ZoneId> extractTargetZones(IPolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        if (policy instanceof ZoneSpecificPolicy zoneSpecificPolicy) {
            return zoneSpecificPolicy.affectedZones();
        }

        if (policy instanceof ICompositePolicy compositePolicy) {
            return compositePolicy.children()
                    .stream()
                    .flatMap(child -> extractTargetZones(child).stream())
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    private DiscountApplicationCandidate bestActualAmountCandidate(
            List<DiscountApplicationCandidate> candidates
    ) {
        return candidates.stream()
                .max(Comparator.comparing(DiscountApplicationCandidate::actualAmount))
                .orElseThrow();
    }
}