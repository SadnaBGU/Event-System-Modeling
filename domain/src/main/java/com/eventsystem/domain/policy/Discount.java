package com.eventsystem.domain.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.DiscountPolicyException;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.basic.CodePolicy;
import com.eventsystem.domain.policy.basic.UntilDatePolicy;
import com.eventsystem.domain.policy.composite.AndPolicy;


public final class Discount {

    private final String discountName;
    private final BigDecimal discountPrecent;
    private final IPolicy discountPolicy;
    private final DiscountVisibility visibility;
    private final LocalDate endDate;


    public Discount(String discountName, BigDecimal discountPercent, IPolicy policy, boolean isVisible, LocalDate endDate) {
        if (!isValidDiscountPercent(discountPercent)) {
            throw new DiscountPolicyException("Discount percent must be between 0 and 100");
        }
        if (!isValidDiscountName(discountName)) {
            throw new DiscountPolicyException("Discount Name must be non null and non blank/empty");
        }
        this.endDate = endDate;
        if (endDate != null) {
            policy = new AndPolicy(List.of(policy, new UntilDatePolicy(endDate)));
        }
        this.discountName = Objects.requireNonNull(discountName, "Discount Name must not be null");
        this.discountPrecent = discountPercent;
        this.visibility = isVisible ? DiscountVisibility.VISIBLE : DiscountVisibility.HIDDEN;
        PolicyConflictDetector.requireValidPolicy(policy);
        this.discountPolicy = Objects.requireNonNull(policy, "Discount policies must not be null");
    }

    public Discount(String discountName, BigDecimal discountPercent, List<IPolicy> policies, boolean isVisible, LocalDate endDate) {
        this(discountName, discountPercent, new AndPolicy(policies), isVisible, endDate);
    }

    public Discount(String discountName, BigDecimal discountPercent, IPolicy policy, boolean isVisible) {
        this(discountName, discountPercent, policy, isVisible, null);
    }

    public Discount(String discountName, BigDecimal discountPercent, List<IPolicy> policies, boolean isVisible) {
        this(discountName, discountPercent, new AndPolicy(policies), isVisible, null);
    }

    public Discount(String discountName, BigDecimal discountPercent, IPolicy policy) {
        this(discountName, discountPercent, policy, true, null);
    }

    public Discount(String discountName, BigDecimal discountPercent, List<IPolicy> policies) {
        this(discountName, discountPercent, new AndPolicy(policies),true, null);
    }
    
    public boolean validateDiscount(PurchaseContext context) {
        Objects.requireNonNull(context, "Purchase Context cannot be null");
        return this.discountPolicy.validate(context);
    }

    public PolicyValidationResult evaluateDiscount(PurchaseContext context) {
        Objects.requireNonNull(context, "Purchase Context cannot be null");
        if (isExpired()) {
            return PolicyValidationResult.failure("Discount has expired on date " + endDate.toString());
        }
        return discountPolicy.evaluate(context);
    }

    public BigDecimal getDiscountPercentForContext(PurchaseContext context) {
       BigDecimal discount = !isExpired() && validateDiscount(context) ? discountPrecent :  BigDecimal.ZERO;
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

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isVisible() {
        return this.visibility == DiscountVisibility.VISIBLE;
    }

    public boolean canExpire() {
        return endDate != null;
    }

    public boolean isExpired() {
        return endDate == null ? false : LocalDate.now().isAfter(endDate);
    }

    private boolean isValidDiscountPercent(BigDecimal discountPercent) {
        return !(discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0 
                                    || discountPercent.compareTo(BigDecimal.valueOf(100)) > 0);
    }

    private boolean isValidDiscountName(String discountName) {
        return !(discountName == null || discountName.isBlank() || discountName.isEmpty());
    }
    

    public static Discount GeneralDiscount(String discountName, BigDecimal discountPercent, LocalDate endDate) {
        IPolicy truePolicy = AlwaysTruePolicy.INSTANCE;
        return new Discount(discountName, discountPercent, truePolicy, true, endDate);
    }

    public static Discount CouponDiscount(String discountName, BigDecimal discountPercent, LocalDate endDate, String couponCode) {
        IPolicy couponPolicy = new CodePolicy(couponCode);
        return new Discount(discountName, discountPercent, couponPolicy, false,  endDate);
    }

    public static Discount toHidden(Discount discount) {
        return new Discount (discount.discountName, discount.getDiscountPercent(), discount.discountPolicy, false, discount.endDate);
    }

    public static Discount toVisible(Discount discount) {
        return new Discount (discount.discountName, discount.getDiscountPercent(), discount.discountPolicy, true, discount.endDate);
    }
}
