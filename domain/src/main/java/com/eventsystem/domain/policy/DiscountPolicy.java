package com.eventsystem.domain.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
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

    private final CompanyId companyId;
    private final List<Discount> discounts;
    private final Set<String> discountedEventsIds;
    private boolean stackable;
    private boolean companyWide;

    public DiscountPolicy(CompanyId companyId) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.discounts = new ArrayList<>();
        this.discountedEventsIds = new HashSet<>();
        this.stackable = false;
        this.companyWide = false;
    }

    public void activateCompanyWide() {
        this.companyWide = true;
    }

    public void deactivateCompanyWide() {
        this.companyWide = false;
    }

    public void activateForEvent(String eventId) {
        discountedEventsIds.add(Objects.requireNonNull(eventId, "eventId must not be null"));
    }

    public void deactivateForEvent(String eventId) {
        discountedEventsIds.remove(Objects.requireNonNull(eventId, "eventId must not be null"));
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

        if (!companyId.equals(context.companyId())) {
            return false;
        }

        return companyWide || discountedEventsIds.contains(context.getEventId());
    }

    public boolean doesDiscountApplyForPurchase(PurchaseContext context) {
        return getDiscountPercent(context).compareTo(BigDecimal.ZERO) > 0;
    }

    private DiscountInfo getDiscountInfo(PurchaseContext context) {
        if (!appliesTo(context)) {
            return new DiscountInfo("None" ,BigDecimal.ZERO);
        }

        return stackable
                ? getStackedDiscountInfo(context)
                : getBestDiscountInfo(context);
    }


    public BigDecimal getDiscountPercent(PurchaseContext context) {
        return getDiscountInfo(context).discountPercent();
    }


    private DiscountInfo getBestDiscountInfo(PurchaseContext context) {
        BigDecimal best = BigDecimal.ZERO;
        String bestDiscountName = "";

        for (Discount discount : discounts) {
            BigDecimal current = discount.getValidDiscountAmount(context);

            if (current.compareTo(best) > 0) {
                best = current;
                bestDiscountName = discount.getDiscountName();
                if (best.compareTo(BigDecimal.valueOf(100)) > 0) {
                    break;
                }
            }
        }

        return new DiscountInfo(bestDiscountName ,capAt100(best));
    }

    private DiscountInfo getStackedDiscountInfo(PurchaseContext context) {
        List <DiscountInfo> validDiscounts= new ArrayList<>();

        for (Discount discount : discounts) {
            if (discount.getValidDiscountAmount(context).compareTo(BigDecimal.ZERO) > 0) {
                validDiscounts.add(new DiscountInfo(discount.getDiscountName(), discount.getDiscountPercent()));
            }
        }
        
        BigDecimal total = BigDecimal.ZERO;
        String stackedDiscountsNames = "";
        for (DiscountInfo discountInfo : validDiscounts) {
            total = total.add(discountInfo.discountPercent());
            stackedDiscountsNames = stackedDiscountsNames.concat(discountInfo.discountName() +"; ");
            if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
                break;
            }
        }

        return new DiscountInfo(stackedDiscountsNames ,  capAt100(total));
    }

    private BigDecimal capAt100(BigDecimal percent) {
        return percent.min(BigDecimal.valueOf(100));
    }

    private record DiscountInfo(String discountName, BigDecimal discountPercent) {}

    public DiscountSnapshot getDiscountSnapshot(PurchaseContext context, Money baseCost) {
        DiscountInfo info = getDiscountInfo(context);
        BigDecimal discountPercent = info.discountPercent();
        BigDecimal multiplier = discountPercent.divide(BigDecimal.valueOf(100));
        Money discountMoneyAmount = new Money(baseCost.amount().multiply(multiplier), baseCost.currency());

        return new DiscountSnapshot(info.discountName(), discountMoneyAmount);
    }
}
