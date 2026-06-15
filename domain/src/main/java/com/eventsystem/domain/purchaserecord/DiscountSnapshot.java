package com.eventsystem.domain.purchaserecord;

import com.eventsystem.domain.shared.Money;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public record DiscountSnapshot(
    String discountName,
    Money discountAmount
) {}