package com.eventsystem.domain.order;

public record BuyerReference(BuyerType type, String sessionId, String memberId) {}
