package com.eventsystem.domain.purchaserecord;

import jakarta.persistence.Embeddable;

@Embeddable
public record BuyerSnapshot(
    String displayName
) {}