package com.eventsystem.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;

import java.math.BigDecimal;

public record DiscountSummary(List<String> appliedDiscountsNames,
                             List<BigDecimal> appliedDiscountPercents,
                              List<BigDecimal> actualDiscountAmount,
                               BigDecimal totalDiscount) {

    private static BigDecimal HUNDREAD = BigDecimal.valueOf(100);

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
    }

    public String getSpecificDiscountInfo(int i) {
        if (i < 0 || i >= appliedDiscountsNames.size()) {
            throw new IndexOutOfBoundsException("Invalid discount index: " + i);
        }

        return String.format("%s : %s : %s",
                appliedDiscountsNames.get(i),
                appliedDiscountPercents.get(i),
                actualDiscountAmount.get(i));
    }

    public String getSpecificDiscountInfo(String discountName) {
        int i = appliedDiscountsNames.indexOf(discountName);

        if (i < 0) {
            throw new DiscountPolicyException("Discount was not applied: " + discountName);
        }

        return getSpecificDiscountInfo(i);
    }

    public boolean isDiscountCappedAt100() {
        BigDecimal totalDiscountPrecent = BigDecimal.ZERO;
        for (BigDecimal bigDecimal : appliedDiscountPercents) {
            totalDiscountPrecent = totalDiscountPrecent.add(bigDecimal);
        }
        return totalDiscountPrecent.compareTo(HUNDREAD) >= 0;
    }

    public static DiscountSummary NoDiscountSummary() {
        return new DiscountSummary(new ArrayList<String>(), 
                                    new ArrayList<BigDecimal>(),
                                     new ArrayList<BigDecimal>(),
                                      BigDecimal.ZERO);
    }

    public DiscountSummary ReplaceActualAmounts(List<BigDecimal> actualAmounts, BigDecimal totalActualDiscount) {
        return new DiscountSummary(this.appliedDiscountsNames, 
                                    this.appliedDiscountPercents,
                                     actualAmounts,
                                      totalActualDiscount);
    }

}