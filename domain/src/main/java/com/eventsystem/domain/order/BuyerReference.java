package com.eventsystem.domain.order;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record BuyerReference(@Enumerated(EnumType.STRING) BuyerType type, String sessionId, String memberId) {}
