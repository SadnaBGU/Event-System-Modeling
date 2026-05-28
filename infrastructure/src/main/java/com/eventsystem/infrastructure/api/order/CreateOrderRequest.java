package com.eventsystem.infrastructure.api.order;

public class CreateOrderRequest {
    public String buyerType; // GUEST | MEMBER
    public String sessionId;
    public String memberId;
    public String eventId;
    public String lotteryCode;

    public CreateOrderRequest() {}
}
