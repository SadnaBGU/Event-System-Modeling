package com.eventsystem.application.purchaserecorddto;

import com.eventsystem.domain.purchaserecord.DiscountSnapshot;
import com.eventsystem.domain.shared.Money;

public record DiscountSnapshotDTO(String discountName, Money discountAmount) {
    public static DiscountSnapshotDTO fromDomain(
            DiscountSnapshot discount) {
        
        return new DiscountSnapshotDTO(
            discount.discountName(),
            discount.discountAmount()
        );
    }
}