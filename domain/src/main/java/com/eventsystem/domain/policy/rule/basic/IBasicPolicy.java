package com.eventsystem.domain.policy.rule.basic;

import com.eventsystem.domain.policy.rule.IPolicy;
/**
 * Policies that be evaled on their own based on input given
 * 
 */
public interface IBasicPolicy extends IPolicy{

    public default boolean isComposite() {
        return false;
    }

    public default boolean isValidPolicy() {
        return true;
    }
    
    
}
