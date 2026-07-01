package com.eventsystem.application.order;

import java.math.BigDecimal;

public record OrderPricingPreviewDTO(
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        String currency) {
}
