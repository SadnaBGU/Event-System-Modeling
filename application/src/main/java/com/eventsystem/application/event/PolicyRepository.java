package com.eventsystem.application.event;

import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.policy.CouponCode;
import com.eventsystem.domain.policy.DiscountPolicy;
import com.eventsystem.domain.policy.PurchasePolicy;
import java.util.Optional;

/**
 * PolicyRepository Interface
 * Port for persisting purchase and discount policies
 */
public interface PolicyRepository {

    void savePurchasePolicy(PurchasePolicy policy);

    Optional<PurchasePolicy> getPurchasePolicy(EventId eventId);

    void saveDiscountPolicy(DiscountPolicy policy);

    Optional<DiscountPolicy> getDiscountPolicy(EventId eventId);

    void saveCouponCode(CouponCode coupon);

    Optional<CouponCode> findCouponCode(String code, EventId eventId);

    void deleteCouponCode(String code, EventId eventId);

}
