package com.eventsystem.domain.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;


import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.math.BigDecimal;



public final class DiscountPolicy {

    private final DiscountPolicyId id;
    private final CompanyId companyId;
    private PolicyScope scope;
    private final List<Discount> discounts;
    private boolean stackable;
    private boolean active;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

        public DiscountPolicy(DiscountPolicyId id, CompanyId companyId, PolicyScope scope) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>();
        this.stackable = false;
        this.active = false; //inactive by default
    }

    public DiscountPolicy(CompanyId cid) {
        this(DiscountPolicyId.random(), cid, PolicyScope.clearScope());
    }

    public static DiscountPolicy clearScope(CompanyId companyId) {
        return new DiscountPolicy( DiscountPolicyId.random(), companyId, PolicyScope.clearScope());
    }

    public static DiscountPolicy inactiveCompanyWide(CompanyId companyId) {
        return new DiscountPolicy( DiscountPolicyId.random(), companyId, PolicyScope.companyWideScope());
    }

    public static DiscountPolicy inactiveForEvents(CompanyId companyId, Set<EventId> eventIds) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, PolicyScope.forEvents(eventIds));
    }

    public static DiscountPolicy inactiveForSingleEvent(CompanyId companyId, EventId eventId) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, PolicyScope.forSingleEvent(eventId));
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

    public void changeScope(PolicyScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        if(!scope.isScopedToEventsOrCompany()) {
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
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(true, affectedEvents);
    }

    public void deactivateCompanyWide() {
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new PolicyScope(false, affectedEvents);
        if(!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
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
        if(!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public void clearAllEventsFromScope() {
        this.scope = new PolicyScope(scope.isCompanyWide(), Set.of());
        if(!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public List<Discount> discounts() {
        return List.copyOf(discounts);
    }

    public List<Discount> visibleDiscounts() {
        return discounts.stream()
                .filter(Discount::isVisible)
                .filter(discount -> !discount.isExpired())
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
                .filter(discount -> !discount.isExpired())
                .filter(discount -> discount.validateDiscount(context))
                .toList();
    }

    public boolean doesHaveVisibleDiscounts() {
        return visibleDiscounts().size() > 0;
    }

    public List<Discount> visibleDiscounts(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!appliesTo(context)) {
            return List.of();
        }

        return discounts.stream()
                .filter(Discount::isVisible)
                .filter(discount -> !discount.isExpired())
                .filter(discount -> discount.validateDiscount(context))
                .toList();
    }

    public Set<String> discountedEventIds() {
        return Set.of(scope.eventIds().toString());
    }

    public boolean isValidDiscountPolicy() {
        return scope.isScopedToEventsOrCompany() && !discounts.isEmpty();
    }

    public void requireValidDiscountPolicy() {
        if(!scope.isScopedToEventsOrCompany()) {
            throw new DiscountPolicyException("Discount is not related to an Event or Company wide");
        }
        if(discounts.isEmpty()) {
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
        ArrayList<BigDecimal> bestDiscountPrecent = new ArrayList<>();
        ArrayList<BigDecimal> actualAmountPlaceholder = new ArrayList<>();


        for (Discount discount : discounts) {
            BigDecimal current = discount.getDiscountPercentForContext(context);

            if (current.compareTo(best) > 0) {
                best = current;
                if (bestDiscountName.isEmpty()) {
                    bestDiscountName.add(discount.getDiscountName());
                    bestDiscountPrecent.add(current);
                    actualAmountPlaceholder.add(BigDecimal.ZERO);
                }
                else {
                    bestDiscountName.set(0, discount.getDiscountName());
                    bestDiscountPrecent.set(0, current);
                }

                if (best.compareTo(HUNDRED) > 0) {
                    break;
                }
            }
        }


        DiscountSummary noActualAmounts = new DiscountSummary(bestDiscountName,
                                                             bestDiscountPrecent,
                                                             actualAmountPlaceholder,
                                                             BigDecimal.ZERO,
                                                             DiscountSummary.shouldCapDiscountAt100(bestDiscountPrecent));
        return noActualAmounts;
    }

    private DiscountSummary getStackedDiscountSummary(PurchaseContext context) {
        BigDecimal totalPercents = BigDecimal.ZERO;
        ArrayList<String> bestDiscountName = new ArrayList<>();
        ArrayList<BigDecimal> bestDiscountPrecent = new ArrayList<>();
        ArrayList<BigDecimal> actualAmountPlaceholder = new ArrayList<>();

        for (Discount discount : discounts) {
            BigDecimal current = discount.getDiscountPercentForContext(context);

            if (current.compareTo(BigDecimal.ZERO) > 0) {
                totalPercents = totalPercents.add(current);
                bestDiscountName.add(discount.getDiscountName());
                bestDiscountPrecent.add(current);
                actualAmountPlaceholder.add(BigDecimal.ZERO);

                if (totalPercents.compareTo(HUNDRED) > 0) {
                    break;
                }
            }
        }

        DiscountSummary noActualAmounts = new DiscountSummary(bestDiscountName,
                                                             bestDiscountPrecent,
                                                             actualAmountPlaceholder,
                                                             BigDecimal.ZERO, DiscountSummary.shouldCapDiscountAt100(bestDiscountPrecent)
                                                             );
        return noActualAmounts;
    }

    public DiscountSummary getFullDiscountSummary(PurchaseContext context, Money baseCost) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseCost, "baseCost must not be null");

        if (!appliesTo(context)) {
            return DiscountSummary.NoDiscountSummary();
        }

        DiscountSummary noActualAmounts =  stackable ? getStackedDiscountSummary(context) : getBestDiscountSummary(context);

        if (noActualAmounts.appliedDiscountsNames().isEmpty()) {
            return DiscountSummary.NoDiscountSummary();
        }

        List<BigDecimal> newActualAmounts = new ArrayList<>();
        BigDecimal totalPercent = BigDecimal.ZERO;

        for (int i = 0; i < noActualAmounts.appliedDiscountsNames().size(); i++) {
            BigDecimal currPercent = noActualAmounts.appliedDiscountPercents().get(i);
            totalPercent = capAt100(totalPercent.add(currPercent));
            BigDecimal mult = currPercent.divide(HUNDRED);
            newActualAmounts.add(mult.multiply(baseCost.amount()));
        }

        BigDecimal temp =  totalPercent.divide(HUNDRED);

        return noActualAmounts.ReplaceActualAmounts(newActualAmounts, temp.multiply(baseCost.amount()));
    }

    public BigDecimal getFullDiscountPercent(PurchaseContext context) {
        if (!appliesTo(context)) {
            return BigDecimal.ZERO;
        }

        DiscountSummary summary =
                stackable ? getStackedDiscountSummary(context) : getBestDiscountSummary(context);

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
                discountNames = discountNames.concat( " ; ");
            }
        }
        return new DiscountSnapshot(discountNames, discountMoneyAmount);
    }

    public DiscountSnapshot generateDiscountSnapshot(PurchaseContext context, Money baseCost) {
        DiscountSummary summary = getFullDiscountSummary(context, baseCost);
        return generateDiscountSnapshot(summary, baseCost);
    }

}
