package com.eventsystem.domain.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;


import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.math.BigDecimal;



public final class DiscountPolicy {

    private final DiscountPolicyId id;
    private final CompanyId companyId;
    private DiscountPolicyScope scope;
    private final List<Discount> discounts;
    private boolean stackable;
    private boolean active;

    private static final BigDecimal HUNDREAD = BigDecimal.valueOf(100);

        public DiscountPolicy(DiscountPolicyId id, CompanyId companyId, DiscountPolicyScope scope) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.discounts = new ArrayList<>();
        this.stackable = false;
        this.active = false; //inactive by default
    }

    public DiscountPolicy(CompanyId cid) {
        this(DiscountPolicyId.random(), cid, DiscountPolicyScope.clearScope());
    }

    public static DiscountPolicy clearScope(CompanyId companyId) {
        return new DiscountPolicy( DiscountPolicyId.random(), companyId, DiscountPolicyScope.clearScope());
    }

    public static DiscountPolicy inactiveCompanyWide(CompanyId companyId) {
        return new DiscountPolicy( DiscountPolicyId.random(), companyId, DiscountPolicyScope.companyWideScope());
    }

    public static DiscountPolicy inactiveForEvents(CompanyId companyId, Set<EventId> eventIds) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, DiscountPolicyScope.forEvents(eventIds));
    }

    public static DiscountPolicy inactiveForSingleEvent(CompanyId companyId, EventId eventId) {
        return new DiscountPolicy(DiscountPolicyId.random(), companyId, DiscountPolicyScope.forSingleEvent(eventId));
    }

    public DiscountPolicyId id() {
        return id;
    }

    public CompanyId companyId() {
        return companyId;
    }

    public DiscountPolicyScope scope() {
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

    public void changeScope(DiscountPolicyScope scope) {
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
        this.scope = new DiscountPolicyScope(true, affectedEvents);
    }

    public void deactivateCompanyWide() {
        Set<EventId> affectedEvents = scope.eventIds();
        this.scope = new DiscountPolicyScope(false, affectedEvents);
        if(!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public void activateForEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Set<EventId> affectedEvents = new HashSet<>(scope.eventIds());
        affectedEvents.add(eventId);

        this.scope = new DiscountPolicyScope(scope.isCompanyWide(), affectedEvents);
    }

    public void deactivateForEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        Set<EventId> affectedEvents = new HashSet<>(scope.eventIds());
        affectedEvents.remove(eventId);

        this.scope = new DiscountPolicyScope(scope.isCompanyWide(), affectedEvents);
        if(!scope.isScopedToEventsOrCompany()) {
            deactivate();
        }
    }

    public List<Discount> discounts() {
        return List.copyOf(discounts);
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

        return foundDiscount.validateDiscount(context);
    }

    private BigDecimal capAt100(BigDecimal percent) {
        return percent.min(HUNDREAD);
    }

    public boolean isPurchaseEligibleForDiscount(PurchaseContext context) {
        if (!appliesTo(context)) {
            return false;
        }
        for (Discount discount : discounts) {
            if (discount.validateDiscount(context) && discount.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    private DiscountSummary getBestDiscountSummary(PurchaseContext context) {
        BigDecimal best = BigDecimal.ZERO;
        ArrayList<String> bestDiscountName = new ArrayList<>();
        ArrayList<BigDecimal> bestDiscountPrecent = new ArrayList<>();
        ArrayList<BigDecimal> actualAmountPlaceholder = new ArrayList<>();


        for (Discount discount : discounts) {
            BigDecimal current = discount.getValidDiscountAmount(context);

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

                if (best.compareTo(HUNDREAD) > 0) {
                    break;
                }
            }
        }


        DiscountSummary noActualAmounts = new DiscountSummary(bestDiscountName,
                                                             bestDiscountPrecent,
                                                             actualAmountPlaceholder,
                                                             BigDecimal.ZERO);
        return noActualAmounts;
    }

    private DiscountSummary getStackedDiscountSummary(PurchaseContext context) {
        BigDecimal totalPercents = BigDecimal.ZERO;
        ArrayList<String> bestDiscountName = new ArrayList<>();
        ArrayList<BigDecimal> bestDiscountPrecent = new ArrayList<>();
        ArrayList<BigDecimal> actualAmountPlaceholder = new ArrayList<>();

        for (Discount discount : discounts) {
            BigDecimal current = discount.getValidDiscountAmount(context);

            if (current.compareTo(BigDecimal.ZERO) > 0) {
                totalPercents = totalPercents.add(current);
                bestDiscountName.add(discount.getDiscountName());
                bestDiscountPrecent.add(current);
                actualAmountPlaceholder.add(BigDecimal.ZERO);

                if (totalPercents.compareTo(HUNDREAD) > 0) {
                    break;
                }
            }
        }

        DiscountSummary noActualAmounts = new DiscountSummary(bestDiscountName,
                                                             bestDiscountPrecent,
                                                             actualAmountPlaceholder,
                                                             BigDecimal.ZERO);
        return noActualAmounts;
    }

    public DiscountSummary getFullDiscountSummary(PurchaseContext context, Money baseCost) {
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
            BigDecimal mult = currPercent.divide(HUNDREAD);
            newActualAmounts.add(mult.multiply(baseCost.amount()));
        }

        BigDecimal temp =  totalPercent.divide(HUNDREAD);

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
                .min(HUNDREAD);
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
