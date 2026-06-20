package com.eventsystem.application.policy;

import java.util.List;

import com.eventsystem.application.policy.policybuilder.DiscountCommand;
import com.eventsystem.application.policy.policybuilder.DiscountPolicyCommand;
import com.eventsystem.application.policy.policybuilder.PurchasePolicyCommand;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.policy.discount.Discount;
import com.eventsystem.domain.policy.discount.DiscountPolicy;
import com.eventsystem.domain.policy.discount.DiscountPolicyId;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.shared.PolicyScope;

public interface IPolicyManagementPort {

    // ---------------------------------------------------------------------
    // Purchase policy management
    // ---------------------------------------------------------------------

    PurchasePolicyId createPurchasePolicy(PurchasePolicyCommand command);

    void savePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicy purchasePolicy);

    void modifyPurchasePolicyScope(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId, PolicyScope newScope);

    void setPurchasePolicyCompanyWide(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId);

    void setPurchasePolicyNotCompanyWide( MemberId actorId, CompanyId companyId, PurchasePolicyId policyId);

    void addEventToPurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId, EventId eventId);

    void removeEventFromPurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId, EventId eventId);

    void renamePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId, String newName);

    void deletePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId);

    void clearAllPurchasePoliciesOfCompany(MemberId actorId, CompanyId companyId);

    void deactivateAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId);

    void removeEventFromAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId, EventId eventId);

    void clearEventsFromAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId);

    void createNewAllowAllPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId );

    void createNotAllowedPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId );

    boolean doesHaveActivePurchasePolicy(EventId eventId, CompanyId companyId);

    // ---------------------------------------------------------------------
    // Discount policy management
    // ---------------------------------------------------------------------

    List<EventId> getEventIdsWithActiveVisibleDiscounts();

    DiscountPolicyId createDiscountPolicy(DiscountPolicyCommand command);

    void saveDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicy discountPolicy);

    void activateDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId);

    void deactivateDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId);

    void modifyDiscountPolicyScope(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId, PolicyScope newScope);

    void setDiscountPolicyCompanyWide( MemberId actorId, CompanyId companyId, DiscountPolicyId policyId );

    void setDiscountPolicyNotCompanyWide( MemberId actorId, CompanyId companyId, DiscountPolicyId policyId);

    void addEventToDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId, EventId eventId );

    void removeEventFromDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId, EventId eventId);

    void addDiscountToPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId,DiscountCommand command);

    void addDiscountToPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId, Discount discount);

    void deleteDiscountPolicy(MemberId actorId, CompanyId companyId, DiscountPolicyId policyId);

    void clearAllDiscountsOfCompany(MemberId actorId, CompanyId companyId);

    void deactivateAllCompanyDiscounts(MemberId actorId, CompanyId companyId);

    void removeEventFromAllCompanyDiscountScopes(MemberId actorId, CompanyId companyId, EventId eventId);

}