package com.eventsystem.application.policy;

public interface IDiscountPermissionChecker {

    boolean canManagePolicies(String actorId, String companyId);
}