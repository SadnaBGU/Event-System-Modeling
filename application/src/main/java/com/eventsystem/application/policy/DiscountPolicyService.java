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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DiscountPolicyService {

    private static final Logger logger = LoggerFactory.getLogger(DiscountPolicyService.class);

    private final IDiscountPolicyRepository discountPolicyRepository;

    public DiscountPolicyService(IDiscountPolicyRepository discountPolicyRepository) {
        this.discountPolicyRepository = Objects.requireNonNull(
                discountPolicyRepository,
                "discountPolicyRepository must not be null"
        );
    }

    public void saveDiscountPolicy(DiscountPolicy discountPolicy) {
        Objects.requireNonNull(discountPolicy, "discountPolicy must not be null");

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

    public void activateDiscountPolicy(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.info("Activating discount policy. policyId={}", policyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.activate();
        discountPolicyRepository.save(policy);

        logger.info("Discount policy activated. policyId={}, companyId={}", policy.id(), policy.companyId());
    }

    public void deactivateDiscountPolicy(DiscountPolicyId policyId) {
        Objects.requireNonNull(policyId, "policyId must not be null");

        logger.info("Deactivating discount policy. policyId={}", policyId);

        DiscountPolicy policy = getByIdOrThrow(policyId);
        policy.deactivate();
        discountPolicyRepository.save(policy);

        logger.info("Discount policy deactivated. policyId={}, companyId={}", policy.id(), policy.companyId());
    }

    public void addDiscountToPolicy(DiscountPolicyId policyId, Discount discount) {
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

    public void deleteDiscountPolicy(DiscountPolicyId policyId) {
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

    public DiscountSummary calculateDiscountSummary(
            CompanyId companyId,
            EventId eventId,
            PurchaseContext context,
            Money baseCost
    ) {
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

    public DiscountSnapshot generateDiscountSnapshot(
            CompanyId companyId,
            EventId eventId,
            PurchaseContext context,
            Money baseCost
    ) {
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

    private DiscountSummary calculateBestSummary(
            List<DiscountPolicy> policies,
            PurchaseContext context,
            Money baseCost
    ) {
        DiscountSummary bestSummary = DiscountSummary.NoDiscountSummary();

        for (DiscountPolicy policy : policies) {
            DiscountSummary currentSummary = policy.getFullDiscountSummary(context, baseCost);

            if (currentSummary.totalDiscount().compareTo(bestSummary.totalDiscount()) > 0) {
                bestSummary = currentSummary;
            }
        }

        return bestSummary;
    }
}