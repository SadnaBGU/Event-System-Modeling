package com.eventsystem.domain.policy.discount;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;

import java.math.BigDecimal;

public record DiscountSummary(List<String> appliedDiscountsNames,
                             List<BigDecimal> appliedDiscountPercents,
                              List<BigDecimal> actualDiscountAmount,
                               BigDecimal totalDiscount,
                                boolean cappedAt100Percent) {

    private static BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public DiscountSummary {
        Objects.requireNonNull(appliedDiscountsNames, "appliedDiscountsNames must not be null");
        Objects.requireNonNull(appliedDiscountPercents, "appliedDiscountPercents must not be null");
        Objects.requireNonNull(actualDiscountAmount, "actualDiscountAmount must not be null");
        Objects.requireNonNull(totalDiscount, "totalDiscount must not be null");

        if (appliedDiscountsNames.size() != appliedDiscountPercents.size()
                || appliedDiscountsNames.size() != actualDiscountAmount.size()) {
            throw new IllegalArgumentException("Discount summary lists must have the same size");
        }

        appliedDiscountsNames = List.copyOf(appliedDiscountsNames);
        appliedDiscountPercents = List.copyOf(appliedDiscountPercents);
        actualDiscountAmount = List.copyOf(actualDiscountAmount);
        cappedAt100Percent = shouldCapDiscountAt100(appliedDiscountPercents);
    }

    public String getSpecificDiscountPurchaseSummary(int i) {
        if (i < 0 || i >= appliedDiscountsNames.size()) {
            throw new IndexOutOfBoundsException("Invalid discount index: " + i);
        }

        return String.format("%s : %s : %s",
                appliedDiscountsNames.get(i),
                appliedDiscountPercents.get(i),
                actualDiscountAmount.get(i));
    }

    public String getSpecificDiscountPurchaseSummary(String discountName) {
        int i = appliedDiscountsNames.indexOf(discountName);

        if (i < 0) {
            throw new DiscountPolicyException("Discount was not applied: " + discountName);
        }

        return getSpecificDiscountPurchaseSummary(i);
    }

    public boolean isDiscountCappedAt100() {
        BigDecimal totalDiscountPercent = BigDecimal.ZERO;
        for (BigDecimal bigDecimal : appliedDiscountPercents) {
            totalDiscountPercent = totalDiscountPercent.add(bigDecimal);
        }
        return totalDiscountPercent.compareTo(HUNDRED) >= 0;
    }

    public static boolean shouldCapDiscountAt100(List<BigDecimal> percents) {
        BigDecimal totalDiscountPercent = BigDecimal.ZERO;
        for (BigDecimal bigDecimal : percents) {
            totalDiscountPercent = totalDiscountPercent.add(bigDecimal);
        }
        return totalDiscountPercent.compareTo(HUNDRED) >= 0;
    }

    public DiscountSnapshot generateDiscountSnapshot(Money baseCost) {

        Objects.requireNonNull(baseCost, "baseCost must not be null");
        Money discountMoneyAmount = new Money(totalDiscount(), baseCost.currency());
        int discountAmount = appliedDiscountsNames().size();
        String discountNames = "";
        for (int i = 0; i < discountAmount; i++) {
            discountNames = discountNames.concat(appliedDiscountsNames().get(i));
            if (i < discountAmount - 1) {
                discountNames = discountNames.concat(" ; ");
            }
        }
        if (discountNames.isEmpty())
        {
            discountNames = "No Discount";
        }
        
        return new DiscountSnapshot(discountNames, discountMoneyAmount);
    }

    public static DiscountSummary noDiscountSummary() {
        return new DiscountSummary(new ArrayList<String>(), 
                                    new ArrayList<BigDecimal>(),
                                     new ArrayList<BigDecimal>(),
                                      BigDecimal.ZERO,false);
    }

    public DiscountSummary replaceActualAmounts(List<BigDecimal> actualAmounts, BigDecimal totalActualDiscount) {
        return new DiscountSummary(this.appliedDiscountsNames, 
                                    this.appliedDiscountPercents,
                                     actualAmounts,
                                      totalActualDiscount, isDiscountCappedAt100());
    }


}