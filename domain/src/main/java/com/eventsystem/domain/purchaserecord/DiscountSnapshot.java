package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;

public record DiscountSnapshot(String discountName, BigDecimal discountAmount) {}