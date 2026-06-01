package com.eventsystem.domain.policy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;


public final class Discount {

    private final String discountName;
    private final BigDecimal discountPrecent;
    private final IPolicy discountPolicy;


    public Discount(String discountName, BigDecimal discountPercent, IPolicy policy) {
        PolicyConflictDetector.requireValidPolicy(policy);
        if (!isValidDiscountPercent(discountPercent)) {
            throw new DiscountPolicyException("Discount percent must be between 0 and 100");
        }
        if (!isValidDiscountName(discountName)) {
            throw new DiscountPolicyException("Discount Name must be non null and non blank/empty");
        }
        this.discountName = Objects.requireNonNull(discountName, "Discount Name must not be null");
        this.discountPrecent = discountPercent;
        this.discountPolicy = Objects.requireNonNull(policy, "Discount policies must not be null");
    }


    public Discount(String discountName, BigDecimal discountPercent, List<IPolicy> policies) {
        this(discountName, discountPercent, new AndPolicy(policies));
    }
    
    public boolean validateDiscount(PurchaseContext context) {
        Objects.requireNonNull(context, "Purchase Context cannot be null");
        return this.discountPolicy.validate(context);
    }

    public BigDecimal getValidDiscountAmount(PurchaseContext context) {
       BigDecimal discount =  validateDiscount(context) ? discountPrecent :  BigDecimal.ZERO;
       return discount;
    }

    public IPolicy policy() {
        return discountPolicy;
    }

    public BigDecimal getDiscountPercent() {
        return discountPrecent;
    }

    public String getDiscountName() {
        return discountName;
    }

    private boolean isValidDiscountPercent(BigDecimal discountPercent) {
        return !(discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0 
                                    || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0);
    }

    private boolean isValidDiscountName(String discountName) {
        return !(discountName == null || discountName.isBlank() || discountName.isEmpty());
    }
    

    public static Discount GeneralDiscount(String discountName, BigDecimal discountPercent) {
        IPolicy truePolicy = AlwaysTruePolicy.INSTANCE;
        return new Discount(discountName, discountPercent, truePolicy);
    }
}
