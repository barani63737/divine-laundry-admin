package com.divinelaundry.service;

import com.divinelaundry.domain.*;
import com.divinelaundry.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentRequestServiceTest {

    @Test
    void validRequestCreatesPaymentRequestAndUsesActorFromPrincipal() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000001", new BigDecimal("1725.00"));
        when(orders.findByOrderNumber("SO-2026-000001")).thenReturn(Optional.of(order));
        when(paymentService.summary("SO-2026-000001")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("1725.00"), List.of()));
        when(requests.findByIdempotencyKey("req-1")).thenReturn(Optional.empty());
        when(requests.findByOrderIdAndStatusInOrderByCreatedAtDesc(order.getId(), List.of(PaymentRequestStatus.CREATED, PaymentRequestStatus.PENDING))).thenReturn(List.of());
        when(requests.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(provider.createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class)))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse(
                        "mock-local",
                        "MOCK-REF-1",
                        null,
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO,
                        "INR",
                        PaymentRequestStatus.CREATED,
                        null,
                        "mock-local://payment/MOCK-REF-1",
                        null,
                        Instant.now().plusSeconds(60)
                ));

        PaymentRequest result = service.createPaymentRequest("SO-2026-000001", new BigDecimal("1000.00"), "admin", "req-1");

        assertThat(result.getOrderNumber()).isEqualTo("SO-2026-000001");
        assertThat(result.getRequestedAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getCreatedBy()).isEqualTo("admin");
        verify(requests, atLeastOnce()).save(any(PaymentRequest.class));
        verify(provider).createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class));
    }

        @Test
        void exactOneHundredRupeeRequestIsPreservedWithoutConversion() {
                PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
                LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
                PaymentService paymentService = mock(PaymentService.class);
                PaymentProvider provider = mock(PaymentProvider.class);
                PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);
                LaundryOrder order = order("SO-2026-000002", new BigDecimal("100.00"));
                when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
                when(paymentService.summary(order.getOrderNumber())).thenReturn(
                                new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("100.00"), List.of()));
                when(requests.findByIdempotencyKey("req-100")).thenReturn(Optional.empty());
                when(requests.findByOrderIdAndStatusInOrderByCreatedAtDesc(eq(order.getId()), anyList())).thenReturn(List.of());
                when(requests.save(any(PaymentRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(provider.createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class)))
                                .thenAnswer(invocation -> {
                                        PaymentProvider.PaymentRequestContext context = invocation.getArgument(0);
                                        return new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-100", null,
                                                        context.requestedAmount(), BigDecimal.ZERO, "INR", PaymentRequestStatus.CREATED,
                                                        "mock-local://payment/MOCK-REF-100", null, null, Instant.now().plusSeconds(600));
                                });

                PaymentRequest result = service.createPaymentRequest(order.getOrderNumber(), new BigDecimal("100.00"), "admin", "req-100");

                assertThat(result.getRequestedAmount()).isEqualByComparingTo("100.00");
                ArgumentCaptor<PaymentProvider.PaymentRequestContext> context = ArgumentCaptor.forClass(PaymentProvider.PaymentRequestContext.class);
                verify(provider).createPaymentRequest(context.capture());
                assertThat(context.getValue().requestedAmount()).isEqualByComparingTo("100.00");
        }

    @Test
    void zeroAmountRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000003", new BigDecimal("100.00"));
        when(service.orders.findByOrderNumber("SO-2026-000003")).thenReturn(Optional.of(order));
        when(service.paymentService.summary("SO-2026-000003")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("100.00"), List.of()));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000003", BigDecimal.ZERO, "admin", "req-zero"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void negativeAmountRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000004", new BigDecimal("100.00"));
        when(service.orders.findByOrderNumber("SO-2026-000004")).thenReturn(Optional.of(order));
        when(service.paymentService.summary("SO-2026-000004")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("100.00"), List.of()));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000004", new BigDecimal("-1.00"), "admin", "req-neg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void moreThanTwoDecimalPlacesRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000005", new BigDecimal("100.00"));
        when(service.orders.findByOrderNumber("SO-2026-000005")).thenReturn(Optional.of(order));
        when(service.paymentService.summary("SO-2026-000005")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("100.00"), List.of()));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000005", new BigDecimal("10.001"), "admin", "req-precise"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
    }

    @Test
    void amountAboveOutstandingRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000006", new BigDecimal("100.00"));
        when(service.orders.findByOrderNumber("SO-2026-000006")).thenReturn(Optional.of(order));
        when(service.paymentService.summary("SO-2026-000006")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("100.00"), List.of()));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000006", new BigDecimal("200.00"), "admin", "req-over"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outstanding");
    }

    @Test
    void cancelledOrderRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000007", new BigDecimal("100.00"));
        order.changeStatus(OrderStatus.CANCELLED);
        when(service.orders.findByOrderNumber("SO-2026-000007")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000007", new BigDecimal("50.00"), "admin", "req-cancelled"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void zeroOutstandingRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000008", new BigDecimal("100.00"));
        when(service.orders.findByOrderNumber("SO-2026-000008")).thenReturn(Optional.of(order));
        when(service.paymentService.summary("SO-2026-000008")).thenReturn(new PaymentService.PaymentSummary(order, new BigDecimal("100.00"), BigDecimal.ZERO, List.of()));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000008", new BigDecimal("1.00"), "admin", "req-paid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outstanding");
    }

    @Test
    void progressiveCollectionScenarioFor1725Total() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000009", new BigDecimal("1725.00"));
        when(orders.findByOrderNumber("SO-2026-000009")).thenReturn(Optional.of(order));
        when(requests.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(requests.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.summary("SO-2026-000009")).thenReturn(
                new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("1725.00"), List.of()),
                new PaymentService.PaymentSummary(order, new BigDecimal("1000.00"), new BigDecimal("725.00"), List.of()),
                new PaymentService.PaymentSummary(order, new BigDecimal("1725.00"), BigDecimal.ZERO, List.of()));

        when(requests.findByOrderIdAndStatusInOrderByCreatedAtDesc(eq(order.getId()), anyList()))
                .thenReturn(List.of());
        when(provider.createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class)))
                .thenReturn(
                        new PaymentProvider.ProviderPaymentResponse(
                                "mock-local",
                                "MOCK-REF-1000",
                                null,
                                new BigDecimal("1000.00"),
                                BigDecimal.ZERO,
                                "INR",
                                PaymentRequestStatus.CREATED,
                                null,
                                "mock-local://payment/MOCK-REF-1000",
                                null,
                                Instant.now().plusSeconds(600)
                        ),
                        new PaymentProvider.ProviderPaymentResponse(
                                "mock-local",
                                "MOCK-REF-725",
                                null,
                                new BigDecimal("725.00"),
                                BigDecimal.ZERO,
                                "INR",
                                PaymentRequestStatus.CREATED,
                                null,
                                "mock-local://payment/MOCK-REF-725",
                                null,
                                Instant.now().plusSeconds(600)
                        ));

        PaymentRequest first = service.createPaymentRequest("SO-2026-000009", new BigDecimal("1000.00"), "admin", "req-1000");
        PaymentRequest second = service.createPaymentRequest("SO-2026-000009", new BigDecimal("725.00"), "admin", "req-725");

        assertThat(first.getRequestedAmount()).isEqualByComparingTo("1000.00");
        assertThat(second.getRequestedAmount()).isEqualByComparingTo("725.00");
        assertThat(first.getProviderReference()).isEqualTo("MOCK-REF-1000");
        assertThat(second.getProviderReference()).isEqualTo("MOCK-REF-725");
    }

    @Test
    void duplicateIdempotencyKeyReturnsExistingRequest() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000009", new BigDecimal("1725.00"));
        PaymentRequest existing = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-dup");
        existing.markPending("MOCK-REF-EXISTING", Instant.now().plusSeconds(600));
        when(orders.findByOrderNumber("SO-2026-000009")).thenReturn(Optional.of(order));
        when(requests.findByIdempotencyKey("req-dup")).thenReturn(Optional.of(existing));

        PaymentRequest result = service.createPaymentRequest("SO-2026-000009", new BigDecimal("1000.00"), "admin", "req-dup");

        assertThat(result).isSameAs(existing);
        verify(requests, never()).save(any(PaymentRequest.class));
    }

    @Test
    void duplicateIdempotencyKeyDifferentAmountIsRejected() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000009-A", new BigDecimal("1725.00"));
        PaymentRequest existing = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-dup-amount");
        when(orders.findByOrderNumber("SO-2026-000009-A")).thenReturn(Optional.of(order));
        when(requests.findByIdempotencyKey("req-dup-amount")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000009-A", new BigDecimal("800.00"), "admin", "req-dup-amount"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different order or amount");
    }

    @Test
    void failedProviderCreationIsMarkedFailedAndRetryReusesSameIdempotencyKey() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000009-B", new BigDecimal("1725.00"));
        when(orders.findByOrderNumber("SO-2026-000009-B")).thenReturn(Optional.of(order));
        when(paymentService.summary("SO-2026-000009-B")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("1725.00"), List.of()));
        when(requests.findByIdempotencyKey("req-retry")).thenReturn(Optional.empty());
        when(requests.findByOrderIdAndStatusInOrderByCreatedAtDesc(eq(order.getId()), anyList())).thenReturn(List.of());
        when(requests.save(any(PaymentRequest.class))).thenAnswer(inv -> {
            PaymentRequest saved = inv.getArgument(0);
            setEntityId(saved, 555L);
            return saved;
        });
        when(provider.createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class)))
                .thenThrow(new IllegalStateException("provider temporarily unavailable"))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse(
                        "mock-local",
                        "MOCK-REF-RETRY",
                        null,
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO,
                        "INR",
                        PaymentRequestStatus.CREATED,
                        null,
                        "mock-local://payment/MOCK-REF-RETRY",
                        null,
                        Instant.now().plusSeconds(600)
                ));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000009-B", new BigDecimal("1000.00"), "admin", "req-retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider temporarily unavailable");

        ArgumentCaptor<PaymentRequest> failedRequest = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(requests, atLeastOnce()).save(failedRequest.capture());
        assertThat(failedRequest.getValue().getStatus()).isEqualTo(PaymentRequestStatus.FAILED);
        assertThat(failedRequest.getValue().getFailureReason()).isEqualTo("Payment request creation failed");

        PaymentRequest existingFailed = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-retry");
        setEntityId(existingFailed, 555L);
        existingFailed.markFailed("Payment request creation failed");
        when(requests.findByIdempotencyKey("req-retry")).thenReturn(Optional.of(existingFailed));

        PaymentRequest retried = service.createPaymentRequest("SO-2026-000009-B", new BigDecimal("1000.00"), "admin", "req-retry");

        assertThat(retried.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(retried.getProviderReference()).isEqualTo("MOCK-REF-RETRY");
        verify(provider, times(2)).createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class));
    }

    @Test
    void duplicateActiveIdenticalRequestRejected() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000010", new BigDecimal("1725.00"));
        PaymentRequest active = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-active");
        when(orders.findByOrderNumber("SO-2026-000010")).thenReturn(Optional.of(order));
        when(paymentService.summary("SO-2026-000010")).thenReturn(new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("1725.00"), List.of()));
        when(requests.findByIdempotencyKey("req-new")).thenReturn(Optional.empty());
        when(requests.findByOrderIdAndStatusInOrderByCreatedAtDesc(order.getId(), List.of(PaymentRequestStatus.CREATED, PaymentRequestStatus.PENDING))).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.createPaymentRequest("SO-2026-000010", new BigDecimal("1000.00"), "admin", "req-new"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void successfulPaymentMarksRequestPaidAndUsesPaymentService() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000011", new BigDecimal("1725.00"));
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-paid");
        request.markPending("MOCK-REF-1", Instant.now().plusSeconds(600));
        when(requests.findByProviderReference("MOCK-REF-1")).thenReturn(Optional.of(request));
        when(requests.findByProviderPaymentId("MOCK-PAY-1")).thenReturn(Optional.empty());
        when(provider.verifyPayment("MOCK-REF-1", new BigDecimal("1000.00"), "INR"))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse(
                        "mock-local", "MOCK-REF-1", "MOCK-PAY-1",
                        new BigDecimal("1000.00"), new BigDecimal("1000.00"), "INR",
                        PaymentRequestStatus.PAID, null, null, null, Instant.now()));
        when(paymentService.record(any(PaymentService.RecordPaymentCommand.class)))
                .thenReturn(new PaymentService.PaymentSummary(order, new BigDecimal("1000.00"), new BigDecimal("725.00"), List.of()));

        PaymentRequest result = service.confirmVerifiedPayment("SO-2026-000011", "MOCK-REF-1", new BigDecimal("1000.00"), "INR", "admin");

        assertThat(result.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(result.getProviderPaymentId()).isEqualTo("MOCK-PAY-1");
        verify(paymentService).record(any(PaymentService.RecordPaymentCommand.class));
    }

    @Test
    void duplicateConfirmationIsIdempotent() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000011-A", new BigDecimal("1725.00"));
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-paid-again");
        request.markPending("MOCK-REF-10", Instant.now().plusSeconds(600));
        request.markPaid("MOCK-PAY-10");

        when(requests.findByProviderReference("MOCK-REF-10")).thenReturn(Optional.of(request));

        PaymentRequest result = service.confirmVerifiedPayment("SO-2026-000011-A", "MOCK-REF-10", new BigDecimal("1000.00"), "INR", "admin");

        assertThat(result).isSameAs(request);
        assertThat(result.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        verify(provider, never()).verifyPayment(anyString(), any(BigDecimal.class), anyString());
        verify(paymentService, never()).record(any(PaymentService.RecordPaymentCommand.class));
    }

    @Test
    void exact1725FlowCompletesToZeroOutstanding() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000015", new BigDecimal("1725.00"));
        when(orders.findByOrderNumber("SO-2026-000015")).thenReturn(Optional.of(order));
        when(requests.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(requests.findByOrderIdAndStatusInOrderByCreatedAtDesc(eq(order.getId()), anyList())).thenReturn(List.of());
        when(requests.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(provider.createPaymentRequest(any(PaymentProvider.PaymentRequestContext.class)))
                .thenReturn(
                        new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-1000", null, new BigDecimal("1000.00"), BigDecimal.ZERO, "INR", PaymentRequestStatus.CREATED, null, "mock-local://payment/MOCK-REF-1000", null, Instant.now().plusSeconds(600)),
                        new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-725", null, new BigDecimal("725.00"), BigDecimal.ZERO, "INR", PaymentRequestStatus.CREATED, null, "mock-local://payment/MOCK-REF-725", null, Instant.now().plusSeconds(600)));
        when(paymentService.summary("SO-2026-000015")).thenReturn(
                new PaymentService.PaymentSummary(order, BigDecimal.ZERO, new BigDecimal("1725.00"), List.of()),
                new PaymentService.PaymentSummary(order, new BigDecimal("1000.00"), new BigDecimal("725.00"), List.of()),
                new PaymentService.PaymentSummary(order, new BigDecimal("1725.00"), BigDecimal.ZERO, List.of()));

        PaymentRequest first = service.createPaymentRequest("SO-2026-000015", new BigDecimal("1000.00"), "admin", "req-1000");
        assertThat(first.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(first.getRequestedAmount()).isEqualByComparingTo("1000.00");

        PaymentRequest firstConfirmed = new PaymentRequest(order, new BigDecimal("1000.00"), "INR", "mock-local", "admin", "req-1000-paid");
        firstConfirmed.markPending("MOCK-REF-1000", Instant.now().plusSeconds(600));
        when(requests.findByProviderReference("MOCK-REF-1000")).thenReturn(Optional.of(firstConfirmed));
        when(provider.verifyPayment("MOCK-REF-1000", new BigDecimal("1000.00"), "INR")).thenReturn(
                new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-1000", "MOCK-PAY-1000", new BigDecimal("1000.00"), new BigDecimal("1000.00"), "INR", PaymentRequestStatus.PAID, null, null, null, Instant.now()));
        when(paymentService.record(any(PaymentService.RecordPaymentCommand.class))).thenReturn(
                new PaymentService.PaymentSummary(order, new BigDecimal("1000.00"), new BigDecimal("725.00"), List.of()));
        PaymentRequest resultOne = service.confirmVerifiedPayment("SO-2026-000015", "MOCK-REF-1000", new BigDecimal("1000.00"), "INR", "admin");
        assertThat(resultOne.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(resultOne.getProviderPaymentId()).isEqualTo("MOCK-PAY-1000");

        PaymentRequest second = service.createPaymentRequest("SO-2026-000015", new BigDecimal("725.00"), "admin", "req-725");
        assertThat(second.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
        assertThat(second.getRequestedAmount()).isEqualByComparingTo("725.00");

        PaymentRequest secondConfirmed = new PaymentRequest(order, new BigDecimal("725.00"), "INR", "mock-local", "admin", "req-725-paid");
        secondConfirmed.markPending("MOCK-REF-725", Instant.now().plusSeconds(600));
        when(requests.findByProviderReference("MOCK-REF-725")).thenReturn(Optional.of(secondConfirmed));
        when(provider.verifyPayment("MOCK-REF-725", new BigDecimal("725.00"), "INR")).thenReturn(
                new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-725", "MOCK-PAY-725", new BigDecimal("725.00"), new BigDecimal("725.00"), "INR", PaymentRequestStatus.PAID, null, null, null, Instant.now()));
        when(paymentService.record(any(PaymentService.RecordPaymentCommand.class))).thenReturn(
                new PaymentService.PaymentSummary(order, new BigDecimal("1725.00"), BigDecimal.ZERO, List.of()));
        PaymentRequest resultTwo = service.confirmVerifiedPayment("SO-2026-000015", "MOCK-REF-725", new BigDecimal("725.00"), "INR", "admin");
        assertThat(resultTwo.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(resultTwo.getProviderPaymentId()).isEqualTo("MOCK-PAY-725");

        assertThat(resultOne.getRequestedAmount()).isEqualByComparingTo("1000.00");
        assertThat(resultTwo.getRequestedAmount()).isEqualByComparingTo("725.00");
    }

    @Test
    void providerAmountMismatchRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000012", new BigDecimal("100.00"));
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("80.00"), "INR", "mock-local", "admin", "req-mismatch");
        request.markPending("MOCK-REF-2", Instant.now().plusSeconds(600));
        when(service.requests.findByProviderReference("MOCK-REF-2")).thenReturn(Optional.of(request));
        when(service.provider.verifyPayment("MOCK-REF-2", new BigDecimal("90.00"), "INR"))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-2", "MOCK-PAY-2",
                        new BigDecimal("80.00"), new BigDecimal("90.00"), "INR", PaymentRequestStatus.PAID, null, null, "Amount mismatch", Instant.now()));

        assertThatThrownBy(() -> service.confirmVerifiedPayment("SO-2026-000012", "MOCK-REF-2", new BigDecimal("90.00"), "INR", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Amount");
    }

    @Test
    void providerCurrencyMismatchRejected() {
        PaymentRequestService service = createService();
        LaundryOrder order = order("SO-2026-000013", new BigDecimal("100.00"));
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("80.00"), "INR", "mock-local", "admin", "req-currency");
        request.markPending("MOCK-REF-3", Instant.now().plusSeconds(600));
        when(service.requests.findByProviderReference("MOCK-REF-3")).thenReturn(Optional.of(request));
        when(service.provider.verifyPayment("MOCK-REF-3", new BigDecimal("80.00"), "USD"))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-3", "MOCK-PAY-3",
                        new BigDecimal("80.00"), new BigDecimal("80.00"), "USD", PaymentRequestStatus.PAID, null, null, "Currency mismatch", Instant.now()));

        assertThatThrownBy(() -> service.confirmVerifiedPayment("SO-2026-000013", "MOCK-REF-3", new BigDecimal("80.00"), "USD", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Currency");
    }

    @Test
    void duplicateProviderPaymentIdIsRejected() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000014", new BigDecimal("100.00"));
        PaymentRequest existing = new PaymentRequest(order, new BigDecimal("80.00"), "INR", "mock-local", "admin", "req-existing-dupe");
        existing.markPending("MOCK-REF-EXISTING", Instant.now().plusSeconds(600));
        existing.markPaid("MOCK-PAY-DUPLICATE");
        setEntityId(existing, 42L);

        PaymentRequest request = new PaymentRequest(order, new BigDecimal("80.00"), "INR", "mock-local", "admin", "req-new-dupe");
        request.markPending("MOCK-REF-NEW", Instant.now().plusSeconds(600));
        setEntityId(request, 43L);
        when(requests.findByProviderReference("MOCK-REF-NEW")).thenReturn(Optional.of(request));
        when(requests.findByProviderPaymentId("MOCK-PAY-DUPLICATE")).thenReturn(Optional.of(existing));
        when(provider.verifyPayment("MOCK-REF-NEW", new BigDecimal("80.00"), "INR"))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-NEW", "MOCK-PAY-DUPLICATE",
                        new BigDecimal("80.00"), new BigDecimal("80.00"), "INR", PaymentRequestStatus.PAID, null, null, null, Instant.now()));

        assertThatThrownBy(() -> service.confirmVerifiedPayment("SO-2026-000014", "MOCK-REF-NEW", new BigDecimal("80.00"), "INR", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void paymentServiceFailureDoesNotMarkRequestPaid() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        PaymentRequestService service = new PaymentRequestService(requests, orders, paymentService, provider);

        LaundryOrder order = order("SO-2026-000014", new BigDecimal("100.00"));
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("80.00"), "INR", "mock-local", "admin", "req-failure");
        request.markPending("MOCK-REF-4", Instant.now().plusSeconds(600));
        when(requests.findByProviderReference("MOCK-REF-4")).thenReturn(Optional.of(request));
        when(provider.verifyPayment("MOCK-REF-4", new BigDecimal("80.00"), "INR"))
                .thenReturn(new PaymentProvider.ProviderPaymentResponse("mock-local", "MOCK-REF-4", "MOCK-PAY-4",
                        new BigDecimal("80.00"), new BigDecimal("80.00"), "INR", PaymentRequestStatus.PAID, null, null, null, Instant.now()));
        when(paymentService.record(any(PaymentService.RecordPaymentCommand.class)))
                .thenThrow(new IllegalStateException("payment failed"));

        assertThatThrownBy(() -> service.confirmVerifiedPayment("SO-2026-000014", "MOCK-REF-4", new BigDecimal("80.00"), "INR", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment failed");
        assertThat(request.getStatus()).isEqualTo(PaymentRequestStatus.PENDING);
    }

    private PaymentRequestService createService() {
        PaymentRequestRepository requests = mock(PaymentRequestRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        PaymentProvider provider = mock(PaymentProvider.class);
        return new PaymentRequestService(requests, orders, paymentService, provider);
    }

    private static void setEntityId(PaymentRequest request, Long id) {
        try {
            java.lang.reflect.Field field = PaymentRequest.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(request, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set payment request id for test", e);
        }
    }

    private LaundryOrder order(String orderNumber, BigDecimal total) {
        Customer customer = new Customer("Test Customer", "9876543210", null, "Trichy");
        LaundryServiceItem item = new LaundryServiceItem("WASH_IRON_KG", "Wash & Iron", "Laundry by KG", PricingUnit.KG, new BigDecimal("120.00"));
        LaundryOrder order = new LaundryOrder("order-request-" + orderNumber, customer, null, null, "admin");
        order.addItem(new OrderItem(item, BigDecimal.ONE, 1, false));
        order.calculateTotals(BigDecimal.ZERO, BigDecimal.ZERO);
        order.assignOrderNumber(orderNumber);
        order.finalizeInvoice("INV-" + orderNumber);
        return order;
    }
}
