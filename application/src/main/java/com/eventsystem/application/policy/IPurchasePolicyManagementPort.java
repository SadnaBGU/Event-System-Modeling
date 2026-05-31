package com.eventsystem.application.policy;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;

import com.eventsystem.domain.company.CompanyId;


public interface IPurchasePolicyManagementPort {

    boolean doesHaveActivePurchasePolicy(EventId eventId, CompanyId companyId);
    
    void createNewAllowAllPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId);

    void setNotAllowedPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId);
}
