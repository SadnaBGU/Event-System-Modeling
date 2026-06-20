package com.eventsystem.domain.policy.discount;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.shared.PolicyOwnerType;
import com.eventsystem.domain.policy.shared.PolicyScope;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "discount_policies")
public final class DiscountPolicy {

    @EmbeddedId
    private DiscountPolicyId id;

    // מגדיר אוטומטית את העמודה כדי שלא יתנגש עם שמות אחרים
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value", column = @Column(name = "company_id", nullable = false))
    })
    private CompanyId companyId;

    // מכיוון שעוד לא ראיתי את קוד ה-Scope, אפשר לשמור גם אותו כ-JSON
    // או כ-Embedded אם זה מתאים
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_scope", columnDefinition = "jsonb")
    private PolicyScope scope;

    // טבלת קשר מיוחדת שתחזיק את כל ההנחות שקשורות לפוליסה הזו
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discount_policy_discounts", joinColumns = @JoinColumn(name = "discount_policy_id"))
    private List<Discount> discounts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private PolicyOwnerType ownerType = PolicyOwnerType.COMPANY;

    private boolean stackable;
    private boolean active;

    // הבונוס: מונע בעיות ביצועים בשמירה ומונע דריסת נתונים ממספר שרתים במקביל
    @Version
    private Long version;

    // חובה עבור JPA
    protected DiscountPolicy() {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DiscountPolicy(DiscountPolicyId id, CompanyId companyId, PolicyScope scope,
            List<Discount> discounts, boolean isStackable, boolean isActive, PolicyOwnerType ownerType) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>(Objects.requireNonNull(discounts, "discounts must not be null"));
        if (discounts.stream().anyMatch(discount -> discount == null)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }
        this.stackable = isStackable;
        this.active = isActive;
        this.ownerType = ownerType;
        requireValidDiscountPolicy();
    }

    private DiscountPolicy(DiscountPolicyId id, CompanyId companyId, PolicyScope scope,
            List<Discount> discounts, boolean isStackable, boolean isActive) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>(Objects.requireNonNull(discounts, "discounts must not be null"));
        if (discounts.stream().anyMatch(discount -> discount == null)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }
        this.stackable = isStackable;
        this.active = isActive; // inactive by default
    }

    public DiscountPolicy(CompanyId companyId, PolicyScope scope,
            List<Discount> discounts, boolean isStackable, boolean isActive) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>(Objects.requireNonNull(discounts, "discounts must not be null"));
        if (discounts.stream().anyMatch(discount -> discount == null)) {
            throw new DiscountPolicyException("Given discounts cannot be null");
        }
        this.id = DiscountPolicyId.random();
        this.stackable = isStackable;
        this.active = isActive; // inactive by default
    }

    public DiscountPolicy(DiscountPolicyId id, CompanyId companyId, EventId specificEventId,
            PolicyOwnerType ownerType) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = PolicyScope.forSingleEvent(specificEventId);
        this.discounts = new ArrayList<>();
        this.ownerType = ownerType;
        this.stackable = false;
        this.active = false; // inactive by default
        requireValidOwnerAndScope(ownerType, scope);

    }

    public DiscountPolicy(DiscountPolicyId id, CompanyId companyId, PolicyScope scope) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>();
        this.stackable = false;
        this.active = false; // inactive by default
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

    public static DiscountPolicy inactiveEventPolicy(CompanyId companyId, EventId eventId) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, eventId, PolicyOwnerType.EVENT);
    }

    public static DiscountPolicy eventPolicy(CompanyId companyId, EventId eventId, List<Discount> discounts,
                                            boolean stackable, boolean active) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, PolicyScope.forSingleEvent(eventId),
                                    discounts, stackable, active, PolicyOwnerType.EVENT);
    }

    public static DiscountPolicy companyPolicy(CompanyId companyId, PolicyScope scope, List<Discount> discounts,
                                            boolean stackable, boolean active) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, scope,
                                    discounts, stackable, active, PolicyOwnerType.COMPANY);
    }

    public static DiscountPolicy withDiscounts(DiscountPolicy discountPolicy, List<Discount> toAdd) {
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");
        Objects.requireNonNull(toAdd, "discounts to add must not be null");

        List<Discount> joinedDiscounts = new ArrayList<>(discountPolicy.discounts());
        joinedDiscounts.addAll(toAdd);

        return new DiscountPolicy(
                discountPolicy.id(),
                discountPolicy.companyId,
                discountPolicy.scope(),
                joinedDiscounts,
                discountPolicy.isStackable(),
                discountPolicy.isActive());
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

        return scope.isListedIn(context.eventId());
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

        return !foundDiscount.isExpired() && foundDiscount.validateDiscount(context);
    }

    private BigDecimal capAt100(BigDecimal percent) {
        return percent.min(HUNDRED);
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
            if (validationResult.isSuccess() && discount.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
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

        DiscountSummary noActualAmounts = new DiscountSummary(bestDiscountName,
                bestDiscountPercent,
                actualAmountPlaceholder,
                BigDecimal.ZERO,
                DiscountSummary.shouldCapDiscountAt100(bestDiscountPercent));
        return noActualAmounts;
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

        DiscountSummary noActualAmounts = new DiscountSummary(appliedDiscountNames,
                appliedDiscountPercents,
                actualAmountPlaceholder,
                BigDecimal.ZERO, DiscountSummary.shouldCapDiscountAt100(appliedDiscountPercents));
        return noActualAmounts;
    }

    public DiscountSummary getFullDiscountSummary(PurchaseContext context, Money baseCost) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseCost, "baseCost must not be null");

        if (!appliesTo(context)) {
            return DiscountSummary.noDiscountSummary();
        }

        DiscountSummary noActualAmounts = stackable ? getStackedDiscountSummary(context)
                : getBestDiscountSummary(context);

        if (noActualAmounts.appliedDiscountsNames().isEmpty()) {
            return DiscountSummary.noDiscountSummary();
        }

        List<BigDecimal> newActualAmounts = new ArrayList<>();
        BigDecimal totalPercent = BigDecimal.ZERO;

        for (int i = 0; i < noActualAmounts.appliedDiscountsNames().size(); i++) {
            BigDecimal currPercent = noActualAmounts.appliedDiscountPercents().get(i);
            totalPercent = capAt100(totalPercent.add(currPercent));
            BigDecimal mult = currPercent.divide(HUNDRED);
            newActualAmounts.add(mult.multiply(baseCost.amount()));
        }

        BigDecimal temp = totalPercent.divide(HUNDRED);

        return noActualAmounts.replaceActualAmounts(newActualAmounts, temp.multiply(baseCost.amount()));
    }

    public BigDecimal getFullDiscountPercent(PurchaseContext context) {
        if (!appliesTo(context)) {
            return BigDecimal.ZERO;
        }

        DiscountSummary summary = stackable ? getStackedDiscountSummary(context) : getBestDiscountSummary(context);

        return summary.appliedDiscountPercents()
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(HUNDRED);
    }

    public DiscountSnapshot generateDiscountSnapshot(DiscountSummary summary, Money baseCost) {

        Objects.requireNonNull(baseCost, "baseCost must not be null");
        Money discountMoneyAmount = new Money(summary.totalDiscount(), baseCost.currency());
        int discountAmount = summary.appliedDiscountsNames().size();
        String discountNames = "";
        for (int i = 0; i < discountAmount; i++) {
            discountNames = discountNames.concat(summary.appliedDiscountsNames().get(i));
            if (i < discountAmount - 1) {
                discountNames = discountNames.concat(" ; ");
            }
        }
        return new DiscountSnapshot(discountNames, discountMoneyAmount);
    }

    public DiscountSnapshot generateDiscountSnapshot(PurchaseContext context, Money baseCost) {
        DiscountSummary summary = getFullDiscountSummary(context, baseCost);
        return generateDiscountSnapshot(summary, baseCost);
    }

    public List<DiscountInfo> getDiscountInfos() {
        return discounts.stream().map(Discount::info).toList();
    }

    public List<DiscountInfo> activeVisibleDiscountsInfo() {
        return !active
                ? List.of()
                : visibleDiscounts().stream().map(Discount::info).toList();
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

    public boolean isSpecificFor(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return !scope.isCompanyWide() && scope.eventIds().contains(eventId) && scope.eventIds().size() == 1;
    }

    public boolean isSingleEventPolicy() {
        return !scope.isCompanyWide() && scope.eventIds().size() == 1;
    }

}
