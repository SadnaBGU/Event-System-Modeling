package com.eventsystem.application.order;

public record RefundResult(boolean success, String errorMessage) {}