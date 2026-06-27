package com.eventsystem.application.company;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.member.MemberId;

public interface ICompanyPermissionServicePort {

    boolean canManageEvents(MemberId actorId, CompanyId companyId);

    boolean canConfigureVenue(MemberId actorId, CompanyId companyId);

    boolean canViewPurchaseHistory(MemberId actorId, CompanyId companyId);

    boolean canGenerateSalesReport(MemberId actorId, CompanyId companyId);
    
    boolean canManageDiscountPolicies(MemberId actorId, CompanyId companyId);

    boolean canManagePurchasePolicies(MemberId actorId, CompanyId companyId);

    String getCompanyName(CompanyId companyId);

}
