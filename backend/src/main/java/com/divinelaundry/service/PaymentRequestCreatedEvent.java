package com.divinelaundry.service;

import java.math.BigDecimal;
public record PaymentRequestCreatedEvent(
        String orderNumber,
        String idempotencyKey,
        BigDecimal amountPaidBefore) {}
