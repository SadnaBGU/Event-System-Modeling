package com.eventsystem.domain.purchaserecord;

import java.math.BigDecimal;

import com.eventsystem.domain.shared.Money;

public record DiscountSnapshot(String discountName, Money discountAmount) {}