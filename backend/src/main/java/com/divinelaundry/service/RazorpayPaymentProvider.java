package com.divinelaundry.service;

import com.divinelaundry.domain.PaymentRequestStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Profile("!local & !demo & !test")
@Primary
@ConditionalOnProperty(name = "razorpay.enabled", havingValue = "true")
public class RazorpayPaymentProvider implements PaymentProvider {
    private static final long PAYMENT_LINK_EXPIRY_SECONDS = 1800L;
    private static final int REFERENCE_ID_MAX_LENGTH = 40;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String keyId;
    private final String keySecret;
    private final ObjectMapper objectMapper;

    @Autowired
    public RazorpayPaymentProvider(
            @Value("${razorpay.base-url:https://api.razorpay.com}") String baseUrl,
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret,
            ObjectMapper objectMapper) {
        this(new RestTemplate(), baseUrl, keyId, keySecret, objectMapper);
    }

    RazorpayPaymentProvider(RestTemplate restTemplate,
                            String baseUrl,
                            String keyId,
                            String keySecret,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.razorpay.com" : baseUrl;
        this.keyId = keyId == null ? null : keyId.trim();
        this.keySecret = keySecret == null ? null : keySecret.trim();
        if (!StringUtils.hasText(this.keyId) || !StringUtils.hasText(this.keySecret)) {
            throw new IllegalStateException("Razorpay is enabled but RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must both be configured.");
        }
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderPaymentResponse createPaymentRequest(PaymentRequestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Payment request context is required");
        }
        String referenceId = normalizeReferenceId(StringUtils.hasText(context.idempotencyKey()) ? context.idempotencyKey() : context.orderNumber());
        long amountInPaise = toPaise(context.requestedAmount());
        Instant expiresAt = buildExpiryInstant();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", amountInPaise);
        payload.put("currency", "INR");
        payload.put("accept_partial", false);
        payload.put("reference_id", referenceId);
        payload.put("expire_by", expiresAt.getEpochSecond());
        payload.put("description", "Divine Laundry - " + context.orderNumber() + (StringUtils.hasText(context.invoiceNumber()) ? " / " + context.invoiceNumber() : ""));

        Map<String, Object> customer = new LinkedHashMap<>();
        if (StringUtils.hasText(context.customerName())) {
            customer.put("name", context.customerName());
        }
        if (!customer.isEmpty()) {
            payload.put("customer", customer);
        }

        JsonNode node = callJson(HttpMethod.POST, "/v1/payment_links", payload);
        String providerReference = node.path("id").asText(null);
        String returnedReference = node.path("reference_id").asText(null);
        String shortUrl = node.path("short_url").asText(null);
        String returnedCurrency = node.path("currency").asText(null);
        long returnedAmount = node.path("amount").asLong(-1L);

        if (!StringUtils.hasText(providerReference)) {
            throw new IllegalStateException("Razorpay did not return a payment link ID");
        }
        if (!StringUtils.hasText(returnedReference)) {
            throw new IllegalStateException("Razorpay did not return a payment-link reference_id");
        }
        if (!referenceId.equals(returnedReference)) {
            throw new IllegalStateException("Razorpay reference_id mismatch for payment request");
        }
        if (returnedAmount != amountInPaise) {
            throw new IllegalStateException("Razorpay amount mismatch for payment request");
        }
        if (!"INR".equalsIgnoreCase(returnedCurrency)) {
            throw new IllegalStateException("Currency mismatch for payment request");
        }

        return new ProviderPaymentResponse(
                "razorpay",
                providerReference,
                null,
                context.requestedAmount(),
                BigDecimal.ZERO,
                "INR",
                PaymentRequestStatus.CREATED,
                shortUrl,
                null,
                null,
                expiresAt);
    }

    @Override
    public ProviderPaymentResponse verifyPayment(String providerReference, BigDecimal amount, String currency) {
        if (!StringUtils.hasText(providerReference)) {
            throw new IllegalArgumentException("Provider reference is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }

        JsonNode link = callJson(HttpMethod.GET, "/v1/payment_links/" + providerReference, null);
        String linkId = link.path("id").asText(null);
        if (StringUtils.hasText(linkId) && !providerReference.equals(linkId)) {
            throw new IllegalStateException("Payment link identity mismatch for verification");
        }

        long expectedPaise = toPaise(amount);
        long returnedAmount = link.path("amount").asLong(-1L);
        if (returnedAmount != expectedPaise) {
            throw new IllegalStateException("Amount mismatch: expected " + amount + " and got " + fromPaise(returnedAmount));
        }

        String paymentLinkStatus = link.path("status").asText(null);
        if (!"paid".equalsIgnoreCase(paymentLinkStatus)) {
            throw new IllegalStateException("Payment link status is not paid: " + paymentLinkStatus);
        }

        String returnedCurrency = link.path("currency").asText(null);
        if (!"INR".equalsIgnoreCase(returnedCurrency) || !"INR".equalsIgnoreCase(currency)) {
            throw new IllegalStateException("Currency mismatch for verified Razorpay payment");
        }

        String capturedPaymentId = findCapturedPaymentId(link, providerReference);
        if (!StringUtils.hasText(capturedPaymentId)) {
            throw new IllegalStateException("Razorpay payment is not captured yet");
        }

        JsonNode payment = callJson(HttpMethod.GET, "/v1/payments/" + capturedPaymentId, null);
        String paymentId = payment.path("id").asText(null);
        if (!StringUtils.hasText(paymentId) || !capturedPaymentId.equals(paymentId)) {
            throw new IllegalStateException("Captured payment ID mismatch for Razorpay payment link");
        }

        String paymentStatus = payment.path("status").asText(null);
        boolean isCaptured = "captured".equalsIgnoreCase(paymentStatus) || payment.path("captured").asBoolean(false);
        if (!isCaptured) {
            throw new IllegalStateException("Razorpay payment is not captured");
        }

        long paymentAmount = payment.path("amount").asLong(-1L);
        if (paymentAmount != expectedPaise) {
            throw new IllegalStateException("Amount mismatch for captured Razorpay payment");
        }

        String paymentCurrency = payment.path("currency").asText(null);
        if (!"INR".equalsIgnoreCase(paymentCurrency)) {
            throw new IllegalStateException("Currency mismatch for captured Razorpay payment");
        }

        String expectedReference = link.path("reference_id").asText(null);
        String paymentReference = payment.path("reference_id").asText(null);
        if (StringUtils.hasText(expectedReference) && StringUtils.hasText(paymentReference)
                && !expectedReference.equals(paymentReference)) {
            throw new IllegalStateException("Captured payment reference does not match the expected Razorpay payment link");
        }

        return new ProviderPaymentResponse(
                "razorpay",
                providerReference,
                paymentId,
                amount,
                fromPaise(paymentAmount),
                "INR",
                PaymentRequestStatus.PAID,
                link.path("short_url").asText(null),
                null,
                null,
                Instant.now());
    }

    @Override
    public ProviderPaymentResponse cancelPaymentRequest(String providerReference) {
        JsonNode response = callJson(HttpMethod.POST, "/v1/payment_links/" + providerReference + "/cancel", null);
        return new ProviderPaymentResponse(
                "razorpay",
                providerReference,
                response.path("payment_id").asText(null),
                fromPaise(response.path("amount").asLong(0L)),
                BigDecimal.ZERO,
                response.path("currency").asText("INR"),
                PaymentRequestStatus.CANCELLED,
                response.path("short_url").asText(null),
                null,
                null,
                Instant.now());
    }

    @Override
    public ProviderPaymentResponse getPaymentStatus(String providerReference) {
        JsonNode link = callJson(HttpMethod.GET, "/v1/payment_links/" + providerReference, null);
        String statusText = link.path("status").asText("created");
        PaymentRequestStatus status = switch (statusText.toLowerCase(Locale.ROOT)) {
            case "paid", "captured" -> PaymentRequestStatus.PAID;
            case "cancelled" -> PaymentRequestStatus.CANCELLED;
            case "failed" -> PaymentRequestStatus.FAILED;
            default -> PaymentRequestStatus.PENDING;
        };
        JsonNode capturedPayment = findCapturedPayment(link);
        return new ProviderPaymentResponse(
                "razorpay",
                providerReference,
                capturedPayment == null || capturedPayment.isMissingNode() ? null : capturedPayment.path("id").asText(null),
                fromPaise(link.path("amount").asLong(0L)),
                status == PaymentRequestStatus.PAID ? fromPaise(link.path("amount").asLong(0L)) : BigDecimal.ZERO,
                link.path("currency").asText("INR"),
                status,
                link.path("short_url").asText(null),
                null,
                null,
                Instant.now());
    }

    private Instant buildExpiryInstant() {
        return Instant.now().plusSeconds(PAYMENT_LINK_EXPIRY_SECONDS);
    }

    private JsonNode callJson(HttpMethod method, String uri, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(keyId) && StringUtils.hasText(keySecret)) {
            headers.setBasicAuth(keyId, keySecret);
        }
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        String endpoint = baseUrl + uri;
        try {
            ResponseEntity<String> response = restTemplate.exchange(endpoint, method, request, String.class);
            if (response.getStatusCode() != HttpStatus.OK && response.getStatusCode() != HttpStatus.CREATED) {
                throw new IllegalStateException("Razorpay request failed with status " + response.getStatusCode());
            }
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException ex) {
            throw new IllegalStateException("Razorpay request failed: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Razorpay response could not be parsed", ex);
        }
    }

    private String normalizeReferenceId(String raw) {
        String candidate = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(candidate)) {
            candidate = "laundry";
        }
        if (candidate.length() <= REFERENCE_ID_MAX_LENGTH) {
            return candidate;
        }

        String suffix = Integer.toHexString(candidate.hashCode()).toUpperCase(Locale.ROOT);
        int prefixLength = REFERENCE_ID_MAX_LENGTH - suffix.length() - 1;
        if (prefixLength <= 0) {
            return suffix.substring(0, Math.min(REFERENCE_ID_MAX_LENGTH, suffix.length()));
        }
        return candidate.substring(0, prefixLength) + "-" + suffix;
    }

    private String findCapturedPaymentId(JsonNode link, String providerReference) {
        JsonNode payments = link.path("payments");
        if (payments.isArray()) {
            for (JsonNode payment : payments) {
                String status = payment.path("status").asText("");
                if (!"captured".equalsIgnoreCase(status)) {
                    continue;
                }
                String paymentId = payment.path("payment_id").asText(null);
                if (!StringUtils.hasText(paymentId)) {
                    continue;
                }
                String plinkId = payment.path("plink_id").asText(null);
                if (StringUtils.hasText(plinkId) && !providerReference.equals(plinkId)) {
                    throw new IllegalStateException("Captured payment belongs to a different Razorpay payment link");
                }
                return paymentId;
            }
        }

        JsonNode payment = link.path("payment").path("entity");
        if (!payment.isMissingNode() && !payment.isNull()) {
            String status = payment.path("status").asText("");
            if ("captured".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
                String paymentId = payment.path("id").asText(null);
                String plinkId = payment.path("payment_link_id").asText(null);
                if (StringUtils.hasText(plinkId) && !providerReference.equals(plinkId)) {
                    throw new IllegalStateException("Captured payment belongs to a different Razorpay payment link");
                }
                return paymentId;
            }
        }
        return null;
    }

    private JsonNode findCapturedPayment(JsonNode link) {
        JsonNode payments = link.path("payments");
        if (payments.isArray()) {
            for (JsonNode payment : payments) {
                String status = payment.path("status").asText("");
                if ("captured".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
                    return payment;
                }
            }
        }
        JsonNode payment = link.path("payment").path("entity");
        if (!payment.isMissingNode() && !payment.isNull()) {
            String status = payment.path("status").asText("");
            if ("captured".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status)) {
                return payment;
            }
        }
        return null;
    }

    private static long toPaise(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }
        return amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}
