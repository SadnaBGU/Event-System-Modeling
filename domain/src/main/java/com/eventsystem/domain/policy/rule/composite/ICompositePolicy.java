package com.eventsystem.domain.policy.rule.composite;

import java.util.List;

import com.eventsystem.domain.policy.rule.IPolicy;

/**
 * Policies that depend on other basic policies to be evald
 * 
 */
public interface ICompositePolicy extends IPolicy{

    List<IPolicy> children();

    public default boolean isComposite() {
        return true;
    }

    @Override
    public default boolean isValidPolicy() {
        List<IPolicy> innerPolicies = children();

        if (innerPolicies == null || innerPolicies.isEmpty()) {
            return false;
        }

        for (IPolicy policy : innerPolicies) {
            if (policy == null || !policy.isValidPolicy()) {
                return false;
            }
        }

        return true;
    }
    
}
