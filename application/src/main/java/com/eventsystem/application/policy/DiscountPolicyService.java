package com.eventsystem.application.policy;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.domainexceptions.PolicyException;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.Discount;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.DiscountPolicyId;
import com.eventsystem.domain.policy.DiscountSummary;
import com.eventsystem.domain.policy.PurchaseContext;
import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.domain.zone.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DiscountPolicyService {

    private static final Logger logger = LoggerFactory.getLogger(DiscountPolicyService.class);

    private final IDiscountPolicyRepository discountPolicyRepository;
    private final IDiscountPermissionChecker permissionChecker;

    public DiscountPolicyService(IDiscountPolicyRepository discountPolicyRepository, IDiscountPermissionChecker permissionChecker) {
        this.discountPolicyRepository = Objects.requireNonNull(discountPolicyRepository,"discountPolicyRepository must not be null");
        this.permissionChecker = Objects.requireNonNull(permissionChecker,"permisiionChecker must not be null");
    }

    public void saveDiscountPolicy(String actorId, String companyId, DiscountPolicy discountPolicy) {
        requireManageDiscountsPermission(actorId, companyId);
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

        CompanyId requestedCompanyId = new CompanyId(companyId);

        if (!discountPolicy.companyId().equals(requestedCompanyId)) {
            logger.warn(
                    "Cannot save discount policy for different company. requestedCompanyId={}, policyCompanyId={}, policyId={}",
                    requestedCompanyId,
                    discountPolicy.companyId(),
                    discountPolicy.id()
            );

            throw new SecurityException("Cannot save Discount policy for another company");
        }

        logger.info(
                "Saving discount policy. policyId={}, companyId={}, active={}",
                discountPolicy.id(),
                discountPolicy.companyId(),
                discountPolicy.isActive()
        );

        discountPolicyRepository.save(discountPolicy);

        logger.info(
                "Discount policy saved. policyId={}, companyId={}",
                discountPolicy.id(),
                discountPolicy.companyId()
        );
    }

    public Optional<DiscountPolicy> findById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.debug("Finding discount policy by id. policyId={}", policyId);

        return discountPolicyRepository.findById(policyId);
    }

    public DiscountPolicy getByIdOrThrow(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        return discountPolicyRepository.findById(policyId)
                .orElseThrow(() -> {
                    logger.warn("Discount policy not found. policyId={}", policyId);
                    return new PolicyException("Discount policy not found: " + policyId);
                });
    }

    public List<DiscountPolicy> findByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        logger.debug("Finding discount policies by company. companyId={}", companyId);

        return discountPolicyRepository.findByCompanyId(companyId);
    }

    public List<DiscountPolicy> findActiveByCompanyId(CompanyId companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");

        logger.debug("Finding active discount policies by company. companyId={}", companyId);

        return discountPolicyRepository.findActiveByCompanyId(companyId);
    }

    public List<DiscountPolicy> findApplicableToPurchase(CompanyId companyId, EventId eventId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        logger.debug(
                "Finding discount policies applicable to purchase. companyId={}, eventId={}",
                companyId,
                eventId.value()
        );

        return discountPolicyRepository.findApplicableToPurchase(companyId, eventId);
    }

    public void activateDiscountPolicy(String actorId, String companyId, DiscountPolicyId policyId) {
        requireManageDiscountsPermission(actorId,companyId);
        requireCompanyOwnsDiscountPolicy(policyId, new CompanyId(companyId));
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.info("Activating discount policy. policyId={}", policyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.activate();
        discountPolicyRepository.save(policy);

        logger.info("Discount policy activated. policyId={}, companyId={}", policy.id(), policy.companyId());
    }

    public void deactivateDiscountPolicy(String actorId, String companyId, DiscountPolicyId policyId) {
        requireManageDiscountsPermission(actorId,companyId);
        requireCompanyOwnsDiscountPolicy(policyId, new CompanyId(companyId));

        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.info("Deactivating discount policy. policyId={}", policyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.deactivate();
        discountPolicyRepository.save(policy);

        logger.info("Discount policy deactivated. policyId={}, companyId={}", policy.id(), policy.companyId());
    }

    public void addDiscountToPolicy(String actorId, String companyId, DiscountPolicyId policyId, Discount discount) {
        requireManageDiscountsPermission(actorId,companyId);
        requireCompanyOwnsDiscountPolicy(policyId, new CompanyId(companyId));

        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(discount, "discount must not be null");

        logger.info(
                "Adding discount to policy. policyId={}, discountName={}",
                policyId,
                discount.getDiscountName()
        );

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.addDiscount(discount);
        discountPolicyRepository.save(policy);

        logger.info(
                "Discount added to policy. policyId={}, discountName={}",
                policy.id(),
                discount.getDiscountName()
        );
    }

    public void deleteDiscountPolicy(String actorId, String companyId, DiscountPolicyId policyId) {
        requireManageDiscountsPermission(actorId, companyId);
        requireCompanyOwnsDiscountPolicy(policyId, new CompanyId(companyId));
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.info("Deleting discount policy. policyId={}", policyId);

        discountPolicyRepository.deleteById(policyId);

        logger.info("Discount policy deleted. policyId={}", policyId);
    }

    public boolean existsById(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        boolean exists = discountPolicyRepository.existsById(policyId);

        logger.debug("Checked discount policy existence. policyId={}, exists={}", policyId, exists);

        return exists;
    }

    public DiscountSummary calculateDiscountSummary(CompanyId companyId, EventId eventId, PurchaseContext context, Money baseCost) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseCost, "baseCost must not be null");

        logger.debug(
                "Calculating discount summary. companyId={}, eventId={}, baseCost={}",
                companyId,
                eventId.value(),
                baseCost
        );

        List<DiscountPolicy> applicablePolicies =
                discountPolicyRepository.findApplicableToPurchase(companyId, eventId);

        if (applicablePolicies.isEmpty()) {
            logger.info(
                    "No applicable discount policies found. companyId={}, eventId={}",
                    companyId,
                    eventId.value()
            );
            return DiscountSummary.NoDiscountSummary();
        }

        DiscountSummary bestSummary = calculateBestSummary(applicablePolicies, context, baseCost);

        logger.info(
                "Discount summary calculated. companyId={}, eventId={}, totalDiscount={}",
                companyId,
                eventId.value(),
                bestSummary.totalDiscount()
        );

        return bestSummary;
    }

    public DiscountSnapshot generateDiscountSnapshot(CompanyId companyId, EventId eventId,
                                                     PurchaseContext context, Money baseCost ) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(baseCost, "baseCost must not be null");

        DiscountSummary summary = calculateDiscountSummary(companyId, eventId, context, baseCost);

        DiscountSnapshot snapshot = new DiscountSnapshot(
                String.join(" ; ", summary.appliedDiscountsNames()),
                Money.of(summary.totalDiscount(), baseCost.currency())
        );

        logger.info(
                "Discount snapshot generated. companyId={}, eventId={}, discountAmount={}",
                companyId,
                eventId.value(),
                snapshot.discountAmount()
        );

        return snapshot;
    }

    private DiscountSummary calculateBestSummary( List<DiscountPolicy> policies, PurchaseContext context,Money baseCost) {
        DiscountSummary bestSummary = DiscountSummary.NoDiscountSummary();

        for (DiscountPolicy policy : policies) {
            DiscountSummary currentSummary = policy.getFullDiscountSummary(context, baseCost);

            if (currentSummary.totalDiscount().compareTo(bestSummary.totalDiscount()) > 0) {
                bestSummary = currentSummary;
            }
        }
        return bestSummary;
    }

    private void requireManageDiscountsPermission(String actorId, String companyId) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");

        if (!permissionChecker.canManagePolicies(actorId, companyId)) {
            logger.warn("Permission denied for Discount management. actorId={}, companyId={}",
                actorId, companyId);
            throw new SecurityException("actor is not allowed to manage events for company: " + companyId);
        }
    }

    private void requireCompanyOwnsDiscountPolicy(DiscountPolicyId dpId, CompanyId companyId) {
        Objects.requireNonNull(dpId, "policyId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        DiscountPolicy p;
        try {
            p = loadDiscountPolicy(dpId);

        } catch (Exception e) {
            return;
        }
        if(!(p.companyId().equals(companyId))) {
            throw new SecurityException("Cannot modify Discount policies of other comapnies");
        }
    }

    public PurchaseContext fromPurchaseInfo(EventId eventId, CompanyId compId, List<ZoneId> zoneOfEachTicket,
                                             LocalDate buyerBirthdate) {
        return new PurchaseContext(eventId, compId,zoneOfEachTicket ,buyerBirthdate, normalizeDiscountCode(null));
    }

    public PurchaseContext fromPurchaseInfo(EventId eventId, CompanyId compId, List<ZoneId> zoneOfEachTicket,
                                             LocalDate buyerBirthdate, String discountCode) {
        return new PurchaseContext(eventId, compId,zoneOfEachTicket ,buyerBirthdate, normalizeDiscountCode(discountCode));
    }

    private DiscountPolicy loadDiscountPolicy(DiscountPolicyId dpId) {
        return discountPolicyRepository.findById(dpId)
                .orElseThrow(() -> {
                    logger.warn("Discount Policy not found. eventId={}", dpId.value());
                    return new IllegalArgumentException("Discount Policy not found: " + dpId.value());
                });
    }


    private String normalizeDiscountCode(String discountCode) {
        return discountCode == null || discountCode.isBlank()
                ? null
                : discountCode.trim();
    }

    private LocalDate placeholderBuyerBirthDate() {
        /*
         * TODO:
         * Replace once checkout saga passes buyer birth date or member profile data.
         * This placeholder prevents null context and keeps current EventQueryPort working.
         *
         * Chosen as 18 years old so MinAgePolicy(18) can pass during placeholder integration.
         * This must not be treated as real production logic.
         */
        return LocalDate.now().minusYears(18);
    }
}