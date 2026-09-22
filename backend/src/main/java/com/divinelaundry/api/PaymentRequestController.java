package com.divinelaundry.api;

import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.service.PaymentRequestService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class PaymentRequestController {
    private final PaymentRequestService paymentRequestService;

    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{orderNumber}/payment-requests")
    public PaymentRequestResponse create(
            @PathVariable String orderNumber,
            @Valid @RequestBody CreateRequest request,
            Principal principal) {
        String actor = principal == null ? "system" : principal.getName();
        return PaymentRequestResponse.from(paymentRequestService.createPaymentRequest(
                orderNumber,
                request.amount(),
                actor,
                request.idempotencyKey()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{orderNumber}/payment-requests", consumes = "application/x-www-form-urlencoded")
    public PaymentRequestResponse createForm(
            @PathVariable String orderNumber,
            @Valid CreateRequest request,
            Principal principal) {
        String actor = principal == null ? "system" : principal.getName();
        return PaymentRequestResponse.from(paymentRequestService.createPaymentRequest(
                orderNumber, request.amount(), actor, request.idempotencyKey()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{orderNumber}/payment-requests")
    public List<PaymentRequestResponse> list(@PathVariable String orderNumber) {
        return paymentRequestService.findByOrderNumber(orderNumber).stream()
                .map(PaymentRequestResponse::from)
                .toList();
    }

    public record CreateRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank String idempotencyKey) {}

    public record PaymentRequestResponse(
            String orderNumber,
            String provider,
            String providerReference,
            String providerPaymentId,
            BigDecimal requestedAmount,
            String currency,
            String status,
            String paymentUrl,
            String qrPayload,
            String failureReason,
            Instant expiresAt) {
        static PaymentRequestResponse from(PaymentRequest request) {
            return new PaymentRequestResponse(
                    request.getOrderNumber(),
                    request.getProvider(),
                    request.getProviderReference(),
                    request.getProviderPaymentId(),
                    request.getRequestedAmount(),
                    request.getCurrency(),
                    request.getStatus().name(),
                    request.getPaymentUrl(),
                    request.getQrPayload(),
                    request.getFailureReason(),
                    request.getExpiresAt());
        }
    }
}
