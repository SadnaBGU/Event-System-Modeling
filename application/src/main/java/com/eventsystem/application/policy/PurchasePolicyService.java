package com.eventsystem.application.policy;

import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.zone.ZoneId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.PolicyScope;
import com.eventsystem.domain.policy.PolicyValidationResult;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.policy.PurchasePolicy;
import com.eventsystem.domain.policy.PurchasePolicyId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PurchasePolicyService implements IPurchasePolicyValidationPort,IPurchasePolicyManagementPort {

    private static final Logger logger = LoggerFactory.getLogger(PurchasePolicyService.class);

    private final IPurchasePolicyRepository purchasePolicyRepository;
    private final ICompanyPermissionServicePort permissionChecker;
    private final IEventManagementPort eventOwnershipChecker;
    private final IMemberInformationPort memberInfoPort;
    


    public PurchasePolicyService(IPurchasePolicyRepository purchasePolicyRepository,
                                 ICompanyPermissionServicePort permissionChecker,
                                 IEventManagementPort eventOwnershipChecker,
                                 IMemberInformationPort memberInfoPort
                                ) {
        this.purchasePolicyRepository = Objects.requireNonNull(
                purchasePolicyRepository,
                "purchasePolicyRepository must not be null"
        );
        this.permissionChecker = Objects.requireNonNull(
                permissionChecker,
                "permissionChecker must not be null"
        );
        this.eventOwnershipChecker = Objects.requireNonNull(
                eventOwnershipChecker,
                "eventOwnershipChecker must not be null"
        );
        this.memberInfoPort = Objects.requireNonNull(
                memberInfoPort,
                "memberInfoPort must not be null"
        );
    }

    public void savePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicy purchasePolicy) {
        requireManagePurchasePoliciesPermission(actorId, companyId);
        Objects.requireNonNull(purchasePolicy, "purchasePolicy must not be null");

        if (!purchasePolicy.companyId().equals(companyId)) {
            logger.warn(
                    "Cannot save purchase policy for different company. requestedCompanyId={}, policyCompanyId={}, policyId={}",
                    companyId,
                    purchasePolicy.companyId(),
                    purchasePolicy.id()
            );

            throw new SecurityException("Cannot save purchase policy for another company");
        }

        for (EventId eventId : purchasePolicy.scope().eventIds()) {
            requireCompanyOwnsEvent(companyId, eventId);
        }

        logger.debug(
                "Saving purchase policy. policyId={}, companyId={}, active={}",
                purchasePolicy.id(),
                purchasePolicy.companyId(),
                purchasePolicy.isActive()
        );

        purchasePolicyRepository.save(purchasePolicy);

        logger.info(
                "Purchase policy saved. policyId={}, companyId={}",
                purchasePolicy.id(),
                purchasePolicy.companyId()
        );
    }

    public Optional<PurchasePolicy> findById(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.debug("Finding purchase policy by id. policyId={}", policyId);

        return purchasePolicyRepository.findById(policyId);
    }

    public PurchasePolicy getByIdOrThrow(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return purchasePolicyRepository.findById(policyId)
                .orElseThrow(() -> {
                    logger.warn("Purchase policy not found. policyId={}", policyId);
                    return new PolicyException("Purchase policy not found: " + policyId);
                });
    }

    public List<PurchasePolicy> findByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        logger.debug("Finding purchase policies by company. companyId={}", companyId);

        return purchasePolicyRepository.findByCompanyId(companyId);
    }

    public List<PurchasePolicy> findActiveByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        logger.debug("Finding active purchase policies by company. companyId={}", companyId);

        return purchasePolicyRepository.findActiveByCompanyId(companyId);
    }

    public List<PurchasePolicy> findApplicableToEvent(EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug("Finding purchase policies applicable to event. eventId={}", eventId.value());

        return purchasePolicyRepository.findApplicableToEvent(eventId);
    }

    public List<PurchasePolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug(
                "Finding purchase policies applicable to purchase. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );

        return purchasePolicyRepository.findApplicableToPurchase(companyId, eventId);
    }

    public void deletePurchasePolicy(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);

        logger.info("Deleting purchase policy. policyId={}", policyId);

        purchasePolicyRepository.deleteById(policyId);

        logger.info("Purchase policy deleted. policyId={}", policyId);
    }

    public boolean existsById(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        boolean exists = purchasePolicyRepository.existsById(policyId);

        logger.debug("Checked purchase policy existence. policyId={}, exists={}", policyId, exists);

        return exists;
    }

    public void clearAllPurchasePoliciesOfCompany(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        logger.info("Clearing all purchase policies of company. companyId={}", companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            purchasePolicyRepository.deleteById(policy.id());

            logger.debug(
                    "Deleted purchase policy as part of company clear. companyId={}, policyId={}",
                    companyId,
                    policy.id()
            );
        }

        logger.info(
                "Finished clearing all purchase policies of company. companyId={}, deletedCount={}",
                companyId,
                companyPolicies.size()
        );
    }

    public void deactivateAllCompanyPurchasePolicies(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        logger.info("Deactivating all purchase policies of company. companyId={}", companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            if (policy.isActive()) {
                policy.setScopeTo(PolicyScope.clearScope());
                purchasePolicyRepository.save(policy);

                logger.debug(
                        "Deactivated purchase policy as part of company deactivation. companyId={}, policyId={}",
                        companyId,
                        policy.id()
                );
            }
        }

        logger.info("Finished deactivating company purchase policies. companyId={}", companyId);
    }

    public void removeEventFromAllPurchasePolicyScopes(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        logger.info(
                "Removing event from all purchase policy scopes. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            if (policy.scope().eventIds().contains(eventId)) {
                policy.deactivateForEvent(eventId);
                purchasePolicyRepository.save(policy);
            }
        }

        logger.info(
                "Event removed from all purchase policy scopes. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );
    }

    public void clearEventsFromAllPurchasePolicies(MemberId actorId, CompanyId companyId) {
        requireManagePurchasePoliciesPermission(actorId, companyId);

        logger.info("Clearing all explicit event scopes from company purchase policies. companyId={}", companyId);

        List<PurchasePolicy> companyPolicies = purchasePolicyRepository.findByCompanyId(companyId);

        for (PurchasePolicy policy : companyPolicies) {
            policy.setScopeTo(new PolicyScope(policy.scope().isCompanyWide(), java.util.Set.of()));
            purchasePolicyRepository.save(policy);
        }

        logger.info("Finished clearing explicit event scopes from company purchase policies. companyId={}", companyId);
    }

    public void modifyPurchasePolicyScope(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId, PolicyScope newScope) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(newScope, "newScope must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);

        for (EventId eventId : newScope.eventIds()) {
            requireCompanyOwnsEvent(companyId, eventId);
        }

        logger.info("Changing purchase policy scope. policyId={}, companyId={}", policyId, companyId);

        PurchasePolicy policy = getByIdOrThrow(policyId);
        policy.setScopeTo(newScope);
        purchasePolicyRepository.save(policy);

        logger.info("Purchase policy scope changed. policyId={}, active={}", policyId, policy.isActive());
    }

    public void setToCompanyWide(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);

        logger.info("Changing purchase policy scope to COMPANY WIDE. policyId={}", policyId);

        PurchasePolicy policy = getByIdOrThrow(policyId);
        policy.setCompanyWide();
        purchasePolicyRepository.save(policy);

        logger.info("Purchase policy scope set to COMPANY WIDE. policyId={}", policyId);
    }

    public void setToNotCompanyWide(MemberId actorId, CompanyId companyId, PurchasePolicyId policyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);

        logger.info("Changing purchase policy scope to NOT COMPANY WIDE. policyId={}", policyId);

        PurchasePolicy policy = getByIdOrThrow(policyId);
        policy.deactivateCompanyWide();
        purchasePolicyRepository.save(policy);

        logger.info("Purchase policy scope set to NOT COMPANY WIDE. policyId={}", policyId);
    }

    public void addEventToPolicy(MemberId actorId,
                                 CompanyId companyId,
                                 PurchasePolicyId policyId,
                                 EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        logger.info(
                "Adding event to purchase policy. policyId={}, eventId={}",
                policyId,
                eventId
        );

        PurchasePolicy policy = getByIdOrThrow(policyId);
        policy.activateForEvent(eventId);
        purchasePolicyRepository.save(policy);

        logger.info(
                "Event added to purchase policy. policyId={}, eventId={}",
                policyId,
                eventId
        );
    }

    public void removeEventFromPolicy(MemberId actorId,
                                      CompanyId companyId,
                                      PurchasePolicyId policyId,
                                      EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);
        requireCompanyOwnsEvent(companyId, eventId);

        logger.info(
                "Removing event from purchase policy. policyId={}, eventId={}",
                policyId,
                eventId
        );

        PurchasePolicy policy = getByIdOrThrow(policyId);
        policy.deactivateForEvent(eventId);
        purchasePolicyRepository.save(policy);

        logger.info(
                "Event removed from purchase policy. policyId={}, eventId={}",
                policyId,
                eventId
        );
    }

    public void renamePurchasePolicy(MemberId actorId,
                                     CompanyId companyId,
                                     PurchasePolicyId policyId,
                                     String newName) {
        Objects.requireNonNull(newName, "newName must not be null");

        requireManagePurchasePoliciesPermission(actorId, companyId);
        requireCompanyOwnsPurchasePolicy(policyId, companyId);

        logger.info("Renaming purchase policy. policyId={}", policyId);

        PurchasePolicy policy = getByIdOrThrow(policyId);
        policy.setNameTo(newName);
        purchasePolicyRepository.save(policy);

        logger.info("Purchase policy renamed. policyId={}, newName={}", policyId, newName);
    }

    @Override
    public void requirePurchasePolicyFor(PurchaseContext context) {
        PolicyValidationResult result = evaluatePurchasePolicyFor(context);

        if (!result.isSuccess()) {
            String reason = result.failureReason().orElse("Purchase violates purchase policy");

            logger.warn(
                    "Purchase policy validation failed. eventId={}, companyId={}, reason={}",
                    context.eventId().value(),
                    context.companyId(),
                    reason
            );

            throw new OrderViolatesPolicyException(reason);
        }
    }

    @Override
    public boolean validatePurchasePolicyFor(PurchaseContext context) {
        return evaluatePurchasePolicyFor(context).isSuccess();
    }

    @Override
    public PolicyValidationResult evaluatePurchasePolicyFor(PurchaseContext context) {
        Objects.requireNonNull(context, "context must not be null");

        CompanyId companyId = context.companyId();
        EventId eventId = context.eventId();

        logger.debug(
                "Evaluating purchase policies. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );

        List<PurchasePolicy> applicablePolicies =
                purchasePolicyRepository.findApplicableToPurchase(companyId, eventId);

        if (applicablePolicies.isEmpty()) {
            logger.debug(
                    "No applicable purchase policies found. Purchase allowed. companyId={}, eventId={}",
                    companyId,
                    eventId.value()
            );

            return PolicyValidationResult.success();
        }

        for (PurchasePolicy policy : applicablePolicies) {
            PolicyValidationResult result = policy.evaluate(context);

            if (!result.isSuccess()) {
                String reason = result.failureReason()
                        .orElse("Purchase violates purchase policy");

                logger.info(
                        "Purchase policy failed. policyId={}, companyId={}, eventId={}, reason={}",
                        policy.id(),
                        companyId,
                        eventId.value(),
                        reason
                );

                return PolicyValidationResult.failure(
                        "Purchase policy '" + policy.policyName() + "' failed: " + reason
                );
            }
        }

        logger.debug(
                "Purchase passed all applicable policies. companyId={}, eventId={}, policyCount={}",
                companyId,
                eventId.value(),
                applicablePolicies.size()
        );

        return PolicyValidationResult.success();
    }

    /**
     * Temporary legacy compatibility method.
     *
     * This method cannot fully validate the new policy model because it does not receive
     * companyId, buyer birth date, zones as value objects, or discount code.
     *
     * Prefer validatePurchasePolicyFor(PurchaseContext).
     */
    @Override
    @Deprecated
    public boolean validatePurchasePolicy(String eventId, BuyerReference buyer, List<OrderItem> items) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyer, "buyer must not be null");
        Objects.requireNonNull(items, "items must not be null");

        logger.warn(
                "Using deprecated validatePurchasePolicy(eventId, buyer, items). " +
                        "Prefer validatePurchasePolicyFor(PurchaseContext). eventId={}",
                eventId
        );

        /*
         * We cannot build a correct PurchaseContext here because this old signature
         * does not include companyId or buyer birth date.
         *
         * So the safest compatibility behavior is:
         * - if there are no active policies for the event, allow;
         * - if there are active policies, fail closed unless the full context is provided.
         *
         * If existing legacy tests expect this method to allow by default, you can temporarily
         * return true here, but the correct migration path is to remove usage of this method.
         */
        List<PurchasePolicy> applicablePolicies = purchasePolicyRepository.findApplicableToEvent(new EventId(eventId));

        return applicablePolicies.isEmpty();
    }

    private void requireManagePurchasePoliciesPermission(MemberId actorId, CompanyId companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManagePurchasePolicies(actorId, companyId)) {
            logger.warn(
                    "Permission denied for purchase-policy management. actorId={}, companyId={}",
                    actorId,
                    companyId
            );

            throw new SecurityException(
                    "actor is not allowed to manage purchase policies for company: " + companyId
            );
        }
    }

    private void requireCompanyOwnsPurchasePolicy(PurchasePolicyId policyId, CompanyId companyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        PurchasePolicy policy = getByIdOrThrow(policyId);

        if (!policy.companyId().equals(companyId)) {
            logger.warn(
                    "Cannot modify purchase policy of another company. requestedCompanyId={}, policyCompanyId={}, policyId={}",
                    companyId,
                    policy.companyId(),
                    policyId
            );

            throw new SecurityException("Cannot modify purchase policies of other companies");
        }
    }

    private void requireCompanyOwnsEvent(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        if (!eventOwnershipChecker.isEventByCompany(eventId, companyId)) {
            logger.warn(
                    "Event is not owned by company. eventId={}, companyId={}",
                    eventId,
                    companyId
            );

            throw new SecurityException(
                    "event does not belong to company. eventId=" + eventId + ", companyId=" + companyId
            );
        }
    }

    @Override
    public void createNewAllowAllPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireCompanyOwnsEvent(companyId, eventId);
        requireManagePurchasePoliciesPermission(actorId, companyId);
        PurchasePolicy policy  = PurchasePolicy.AllowAll(PurchasePolicyId.random(), companyId, "AllowAll");
        policy.activateForEvent(eventId);
        purchasePolicyRepository.save(policy);
    }

    @Override
    public void setNotAllowedPurchasePolicy(MemberId actorId, CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        requireCompanyOwnsEvent(companyId, eventId);
        requireManagePurchasePoliciesPermission(actorId, companyId);
        PurchasePolicy policy  = PurchasePolicy.NotAllowed(PurchasePolicyId.random(), companyId, "NotAllowed");
        policy.activateForEvent(eventId);
        purchasePolicyRepository.save(policy);
    }

    @Override
    public boolean doesHaveActivePurchasePolicy(EventId eventId, CompanyId companyId) {
      return (purchasePolicyRepository.findActiveByCompanyId(companyId).size() > 0
              || purchasePolicyRepository.findApplicableToEvent(eventId).size() > 0 );
    }

    @Override
    public PurchaseContext createPurchaseContext(EventId eventId, BuyerReference buyerRef, List<OrderItem> items) {
        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);
        List<ZoneId> zonesOfEachTicket = eventOwnershipChecker.getZonesOfTicketsForEvent(eventId,items);
        LocalDate buyerBirthday = memberInfoPort.getMemberBirthdate(new MemberId(buyerRef.memberId()));
        return new PurchaseContext(eventId, companyId, zonesOfEachTicket, buyerBirthday, null);
    }

}