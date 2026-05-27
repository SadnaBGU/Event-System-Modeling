package com.eventsystem.domain.policy;

import java.util.List;
import java.util.Objects;

import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.domainexceptions.PurchasePolicyException;
import com.eventsystem.domain.policy.composite.AndPolicy;
import com.eventsystem.domain.policy.basic.AlwaysTruePolicy;
import com.eventsystem.domain.policy.basic.NeverAllowPolicy;



public class PurchasePolicy{

    private final IPolicy policy;

    public PurchasePolicy(List<IPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new PurchasePolicyException("Purcahse Policy cannot be empty or null");
        }
        if (policies.stream().anyMatch(Objects::isNull)) {
            throw new PurchasePolicyException("PurchasePolicy cannot contain null policies");
        }
        this.policy = new AndPolicy(policies);
    }

    public PurchasePolicy(IPolicy policy) {
        if (policy == null) {
            throw new PurchasePolicyException("Purcahse Policy cannot be empty or null");
        }
        
        this.policy = policy;
    }

    public static PurchasePolicy AllowAll() {
        return new PurchasePolicy(AlwaysTruePolicy.INSTANCE);
    }

    public static PurchasePolicy NotAllowed() {
        return new PurchasePolicy(NeverAllowPolicy.INSTANCE);
    }

    public boolean isPurchaseAllowedInContext(PurchaseContext context) {
        return policy.validate(context);
    }

    public void requirePurchasePolicy(PurchaseContext context) {
        try {
            policy.require(context);
        } catch (Exception e) {
            throw new PurchasePolicyException(String.format("Purchase Policy Vioalation: %s",e.getMessage()));
        }
    }

}
