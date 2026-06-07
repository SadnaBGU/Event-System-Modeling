package com.eventsystem.application.policy;

import com.eventsystem.application.appexceptions.OrderViolatesPolicyException;
import com.eventsystem.application.event.IEventManagementPort;
import com.eventsystem.application.member.IMemberInformationPort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.OrderItem;
import com.eventsystem.domain.policy.purchase.IPurchasePolicyRepository;
import com.eventsystem.domain.policy.purchase.PurchasePolicy;
import com.eventsystem.domain.policy.purchase.PurchasePolicyId;
import com.eventsystem.domain.policy.shared.PolicyValidationResult;
import com.eventsystem.domain.policy.shared.PurchaseContext;
import com.eventsystem.domain.zone.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PurchasePolicyValidationService implements IPurchasePolicyValidationPort {

    private static final Logger logger = LoggerFactory.getLogger(PurchasePolicyValidationService.class);

    private final IPurchasePolicyRepository purchasePolicyRepository;
    private final IEventManagementPort eventOwnershipChecker;
    private final IMemberInformationPort memberInfoPort;

    public PurchasePolicyValidationService(
            IPurchasePolicyRepository purchasePolicyRepository,
            IEventManagementPort eventOwnershipChecker,
            IMemberInformationPort memberInfoPort
    ) {
        this.purchasePolicyRepository = Objects.requireNonNull(
                purchasePolicyRepository,
                "purchasePolicyRepository must not be null"
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

    public boolean existsById(PurchasePolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        boolean exists = purchasePolicyRepository.existsById(policyId);

        logger.debug("Checked purchase policy existence. policyId={}, exists={}", policyId, exists);

        return exists;
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

        List<PurchasePolicy> applicablePolicies =
                purchasePolicyRepository.findApplicableToEvent(new EventId(eventId));

        return applicablePolicies.isEmpty();
    }

    @Override
    public PurchaseContext createPurchaseContext(
            EventId eventId,
            BuyerReference buyerRef,
            List<OrderItem> items
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(buyerRef, "buyerRef must not be null");
        Objects.requireNonNull(items, "items must not be null");

        CompanyId companyId = eventOwnershipChecker.companyOfEvent(eventId);
        List<ZoneId> zonesOfEachTicket = eventOwnershipChecker.getZonesOfTicketsForEvent(eventId, items);
        LocalDate buyerBirthday = memberInfoPort.getMemberBirthdate(new MemberId(buyerRef.memberId()));

        return new PurchaseContext(eventId, companyId, zonesOfEachTicket, buyerBirthday, null);
    }
}