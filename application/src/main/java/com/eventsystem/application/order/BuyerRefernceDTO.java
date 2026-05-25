package com.eventsystem.application.order;

import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;

public record BuyerRefernceDTO(
    BuyerType type,
    String sessionId,
    String memberId
) {

    public static BuyerRefernceDTO fromDomain(BuyerReference buyerRef) {
        return new BuyerRefernceDTO(buyerRef.type(), buyerRef.sessionId(), buyerRef.memberId());
    }
}
