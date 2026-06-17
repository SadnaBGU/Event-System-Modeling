package com.eventsystem.infrastructure.persistence.springrepos;

import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.discount.*;
import java.util.List;
import java.util.Optional;

public class PostgresDiscountPolicyRepository implements IDiscountPolicyRepository {

    private final SpringDataDiscountPolicyRepository jpaRepository;

    public PostgresDiscountPolicyRepository(SpringDataDiscountPolicyRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DiscountPolicy> findById(DiscountPolicyId id) { return jpaRepository.findById(id); }

    @Override
    public List<DiscountPolicy> findByCompanyId(CompanyId id) { return jpaRepository.findByCompanyId(id); }

    @Override
    public List<DiscountPolicy> findActive() { 
        return jpaRepository.findAll().stream().filter(DiscountPolicy::isActive).toList(); 
    }

    @Override
    public List<DiscountPolicy> findActiveWithVisibleDiscounts() {
        return jpaRepository.findAll().stream().filter(DiscountPolicy::isActive)
                .filter(DiscountPolicy::doesHaveVisibleDiscounts).toList();
    }

    @Override
    public List<DiscountPolicy> findActiveByCompanyId(CompanyId id) {
        return jpaRepository.findByCompanyId(id).stream().filter(DiscountPolicy::isActive).toList();
    }

    @Override
    public List<DiscountPolicy> findApplicableToEvent(EventId id) {
        return jpaRepository.findAll().stream()
                .filter(p -> p.isActive() && p.scope().appliesTo(id))
                .toList();
    }

    @Override
    public List<DiscountPolicy> findApplicableToPurchase(CompanyId cId, EventId eId) {
        return jpaRepository.findByCompanyId(cId).stream()
                .filter(p -> p.isActive() && p.scope().appliesTo(eId))
                .toList();
    }

    @Override
    public void save(DiscountPolicy p) { jpaRepository.save(p); }

    @Override
    public void deleteById(DiscountPolicyId id) { jpaRepository.deleteById(id); }

    @Override
    public boolean existsById(DiscountPolicyId id) { return jpaRepository.existsById(id); }
}
