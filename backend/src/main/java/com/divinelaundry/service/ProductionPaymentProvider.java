package com.divinelaundry.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Profile("!local & !demo & !test")
@ConditionalOnProperty(name = "razorpay.enabled", havingValue = "false", matchIfMissing = true)
public class ProductionPaymentProvider implements PaymentProvider {

    private static final String NOT_CONFIGURED_MESSAGE =
            "Real payment provider not configured. Payment requests and verification must be handled by a configured gateway.";

    @Override
    public ProviderPaymentResponse createPaymentRequest(PaymentRequestContext context) {
        throw missingProvider("createPaymentRequest");
    }

    @Override
    public ProviderPaymentResponse verifyPayment(String providerReference, BigDecimal amount, String currency) {
        throw missingProvider("verifyPayment");
    }

    @Override
    public ProviderPaymentResponse cancelPaymentRequest(String providerReference) {
        throw missingProvider("cancelPaymentRequest");
    }

    @Override
    public ProviderPaymentResponse getPaymentStatus(String providerReference) {
        throw missingProvider("getPaymentStatus");
    }

    private IllegalStateException missingProvider(String operation) {
        return new IllegalStateException(operation + " failed: " + NOT_CONFIGURED_MESSAGE);
    }
}
