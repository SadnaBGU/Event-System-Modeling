package com.eventsystem.infrastructure.persistence.entities;

import com.eventsystem.domain.order.BuyerType;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class BuyerReferenceEmbeddable {

    @Enumerated(EnumType.STRING)
    private BuyerType type;

    private String sessionId;

    private String memberId;

    protected BuyerReferenceEmbeddable() {}

    public BuyerReferenceEmbeddable(BuyerType type, String sessionId, String memberId) {
        this.type = type;
        this.sessionId = sessionId;
        this.memberId = memberId;
    }

    public BuyerType getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMemberId() {
        return memberId;
    }
}