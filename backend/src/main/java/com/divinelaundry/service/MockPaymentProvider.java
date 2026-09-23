package com.divinelaundry.service;

import com.divinelaundry.domain.PaymentRequestStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.math.RoundingMode.HALF_UP;

@Service
@Profile({"local", "demo", "test"})
public class MockPaymentProvider implements PaymentProvider {
    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, ProviderPaymentResponse> responses = new ConcurrentHashMap<>();

    @Override
    public ProviderPaymentResponse createPaymentRequest(PaymentRequestContext context) {
        String providerReference = "MOCK-REF-" + sequence.getAndIncrement();
        String paymentUrl = "mock-local://payment/" + providerReference;
        String qrPayload = buildQrPayload(context, providerReference);
        ProviderPaymentResponse response = new ProviderPaymentResponse(
                "mock-local",
                providerReference,
                null,
                context.requestedAmount(),
                BigDecimal.ZERO,
                context.currency(),
                PaymentRequestStatus.CREATED,
                paymentUrl,
                qrPayload,
                null,
                Instant.now().plus(Duration.ofMinutes(15)));
        responses.put(providerReference, response);
        return response;
    }

    public ProviderPaymentResponse completeForTest(String providerReference, BigDecimal amount, String currency) {
        ProviderPaymentResponse known = responses.get(providerReference);
        if (known == null) {
            throw new IllegalArgumentException("Unknown provider reference for test verification");
        }
        if (amount == null || amount.compareTo(known.requestedAmount()) != 0) {
            throw new IllegalArgumentException("Amount does not match the requested payment");
        }
        if (currency == null || !known.currency().equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Currency does not match the requested payment");
        }
        ProviderPaymentResponse verified = new ProviderPaymentResponse(
                known.provider(),
                known.providerReference(),
                "MOCK-PAY-" + sequence.getAndIncrement(),
                known.requestedAmount(),
                amount,
                known.currency(),
                PaymentRequestStatus.PAID,
                known.paymentUrl(),
                known.qrPayload(),
                null,
                Instant.now());
        responses.put(providerReference, verified);
        return verified;
    }

    @Override
    public ProviderPaymentResponse verifyPayment(String providerReference, BigDecimal amount, String currency) {
        ProviderPaymentResponse known = responses.get(providerReference);
        if (known == null) {
            return new ProviderPaymentResponse(
                    "mock-local",
                    providerReference,
                    "MOCK-PAY-" + sequence.getAndIncrement(),
                    amount,
                    amount,
                    currency,
                    PaymentRequestStatus.PAID,
                    null,
                    null,
                    null,
                    Instant.now());
        }
        if (amount.compareTo(known.requestedAmount()) != 0) {
            return new ProviderPaymentResponse(
                    known.provider(),
                    known.providerReference(),
                    "MOCK-PAY-" + sequence.getAndIncrement(),
                    known.requestedAmount(),
                    BigDecimal.ZERO,
                    known.currency(),
                    PaymentRequestStatus.FAILED,
                    null,
                    null,
                    "Amount mismatch",
                    Instant.now());
        }
        if (!known.currency().equalsIgnoreCase(currency)) {
            return new ProviderPaymentResponse(
                    known.provider(),
                    known.providerReference(),
                    "MOCK-PAY-" + sequence.getAndIncrement(),
                    known.requestedAmount(),
                    BigDecimal.ZERO,
                    known.currency(),
                    PaymentRequestStatus.FAILED,
                    null,
                    null,
                    "Currency mismatch",
                    Instant.now());
        }
        ProviderPaymentResponse verified = new ProviderPaymentResponse(
                known.provider(),
                known.providerReference(),
                "MOCK-PAY-" + sequence.getAndIncrement(),
                known.requestedAmount(),
                amount,
                known.currency(),
                PaymentRequestStatus.PAID,
                null,
                null,
                null,
                Instant.now());
        responses.put(providerReference, verified);
        return verified;
    }

    private String buildQrPayload(PaymentRequestContext context, String providerReference) {
        String customer = context.customerName() == null || context.customerName().isBlank()
                ? "Divine Laundry"
                : context.customerName();
        String amount = context.requestedAmount().setScale(2, HALF_UP).toPlainString();
        return "upi://pay?pa=mock%40divinelaundry&pn="
                + URLEncoder.encode(customer, StandardCharsets.UTF_8)
                + "&am=" + amount
                + "&cu=" + URLEncoder.encode(context.currency(), StandardCharsets.UTF_8)
                + "&tn=" + URLEncoder.encode("Order " + context.orderNumber() + " / " + providerReference, StandardCharsets.UTF_8);
    }

    @Override
    public ProviderPaymentResponse cancelPaymentRequest(String providerReference) {
        ProviderPaymentResponse existing = responses.get(providerReference);
        if (existing == null) {
            return new ProviderPaymentResponse("mock-local", providerReference, null, BigDecimal.ZERO, BigDecimal.ZERO, "INR", PaymentRequestStatus.CANCELLED, null, null, "Not found", Instant.now());
        }
        ProviderPaymentResponse cancelled = new ProviderPaymentResponse(
                existing.provider(),
                existing.providerReference(),
                existing.providerPaymentId(),
                existing.requestedAmount(),
                existing.paidAmount(),
                existing.currency(),
                PaymentRequestStatus.CANCELLED,
                null,
                null,
                null,
                Instant.now());
        responses.put(providerReference, cancelled);
        return cancelled;
    }

    @Override
    public ProviderPaymentResponse getPaymentStatus(String providerReference) {
        return responses.getOrDefault(providerReference,
                new ProviderPaymentResponse("mock-local", providerReference, null, BigDecimal.ZERO, BigDecimal.ZERO, "INR", PaymentRequestStatus.CREATED, null, null, null, Instant.now()));
    }
}
