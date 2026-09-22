package com.divinelaundry.service;

import com.divinelaundry.config.WhatsappProviderProperties;
import com.divinelaundry.domain.Customer;
import com.divinelaundry.domain.LaundryOrder;
import com.divinelaundry.domain.LaundryServiceItem;
import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.domain.PaymentMode;
import com.divinelaundry.domain.PricingUnit;
import com.divinelaundry.domain.WhatsappMessage;
import com.divinelaundry.repository.LaundryOrderRepository;
import com.divinelaundry.repository.WhatsappMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappServiceTest {
    @Test
    void invoiceImageDeduplicationKeyRemainsUnchangedWhenClaimIsUnavailable() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        WhatsappCloudApiClient provider = mock(WhatsappCloudApiClient.class);
        WhatsappMessageClaimService claims = mock(WhatsappMessageClaimService.class);
        LaundryOrder order = invoicedOrder("image-send", "INV-2026-000002");
        WhatsappMessage message = new WhatsappMessage("INVOICE_IMAGE:INV-2026-000002", order,
                order.getCustomer().getPhone(), "image-template");
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(messages.findByDeduplicationKey(message.getDeduplicationKey())).thenReturn(Optional.of(message));
        when(provider.isConfigured()).thenReturn(true);
        when(claims.claim(message.getDeduplicationKey())).thenReturn(Optional.empty());

        new WhatsappService(messages, orders, mock(DocumentService.class), mock(InvoicePaymentImageService.class),
                mock(PdfInvoiceService.class), mock(PaymentReceiptService.class), mock(PdfReceiptService.class),
                provider, properties(), claims, new WhatsappMessagePersistenceService(messages))
                .queueInvoice(order.getOrderNumber());

        verify(claims).claim("INVOICE_IMAGE:INV-2026-000002");
        verify(provider, never()).sendInvoiceAndPaymentImage(anyString(), any(), anyString(), any());
    }

    @Test
    void invoicePdfDeduplicationKeyRemainsUnchangedWhenClaimIsUnavailable() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        DocumentService documents = mock(DocumentService.class);
        PdfInvoiceService invoices = mock(PdfInvoiceService.class);
        WhatsappCloudApiClient provider = mock(WhatsappCloudApiClient.class);
        WhatsappMessageClaimService claims = mock(WhatsappMessageClaimService.class);
        LaundryOrder order = invoicedOrder("pdf-send", "INV-2026-000003");
        WhatsappMessage message = new WhatsappMessage("INVOICE_PDF:INV-2026-000003", order,
                order.getCustomer().getPhone(), "document-template", "DOCUMENT");
        DocumentService.DocumentBundle document = new DocumentService.DocumentBundle(null, order,
                new PaymentService.PaymentSummary(order, BigDecimal.ZERO, order.getTotal(), List.of()), List.of());
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(documents.document(order.getOrderNumber())).thenReturn(document);
        when(invoices.render(document)).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(messages.findByDeduplicationKey(message.getDeduplicationKey())).thenReturn(Optional.of(message));
        when(provider.isDocumentConfigured()).thenReturn(true);
        when(claims.claim(message.getDeduplicationKey())).thenReturn(Optional.empty());

        new WhatsappService(messages, orders, documents, mock(InvoicePaymentImageService.class), invoices,
                mock(PaymentReceiptService.class), mock(PdfReceiptService.class), provider,
                properties(), claims, new WhatsappMessagePersistenceService(messages)).queueInvoicePdf(order.getOrderNumber());

        verify(claims).claim("INVOICE_PDF:INV-2026-000003");
        verify(provider, never()).sendInvoiceAndPaymentDocument(anyString(), any(), anyString(), any());
    }

    @Test
    void paymentEventPathSendsTheExactReceiptAsDocument() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        DocumentService documents = mock(DocumentService.class);
        InvoicePaymentImageService images = mock(InvoicePaymentImageService.class);
        PdfInvoiceService invoices = mock(PdfInvoiceService.class);
        PaymentReceiptService receipts = mock(PaymentReceiptService.class);
        PdfReceiptService receiptPdfs = mock(PdfReceiptService.class);
        WhatsappCloudApiClient provider = mock(WhatsappCloudApiClient.class);
        WhatsappMessageClaimService claims = mock(WhatsappMessageClaimService.class);
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, "https://graph.example", "v-test", "phone-id", "secret-token",
                "image-template", "en", "document-template", "en");
        WhatsappService service = new WhatsappService(messages, orders, documents, images, invoices,
                receipts, receiptPdfs, provider, properties, claims, new WhatsappMessagePersistenceService(messages));

        LaundryOrder order = new LaundryOrder("receipt-send", new Customer("Customer", "9876543210", null, "Trichy"), null, null, "admin");
        order.assignOrderNumber("SO-2026-000001");
        order.finalizeInvoice("INV-2026-000001");
        var receipt = new PaymentReceiptService.PaymentReceiptDocument(
                new PaymentReceiptService.BusinessDetails("Divine Laundry", "000", "Trichy", ""),
                "PAY-2026-000001", "PAY-2026-000001", Instant.parse("2026-09-20T10:00:00Z"),
                PaymentMode.CASH.name(), "REF-1", "admin", order.getOrderNumber(), order.getInvoiceNumber(),
                "Customer", "9876543210", null, "Trichy", new BigDecimal("1000.00"),
                new BigDecimal("1000.00"), new BigDecimal("400.00"), new BigDecimal("600.00"), "PARTIAL");
        byte[] pdf = new byte[]{'%', 'P', 'D', 'F'};
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(receipts.document(order.getOrderNumber(), receipt.paymentNumber())).thenReturn(receipt);
        when(receiptPdfs.render(receipt)).thenReturn(pdf);
        when(provider.isDocumentConfigured()).thenReturn(true);
        when(provider.sendInvoiceAndPaymentDocument(anyString(), eq(pdf), eq("PAY-2026-000001.pdf"), any()))
                .thenReturn(new WhatsappCloudApiClient.DeliveryResult("media-1", "wamid.1"));
        WhatsappMessage storedMessage = new WhatsappMessage("RECEIPT_PDF:PAY-2026-000001", order,
                order.getCustomer().getPhone(), "document-template", "DOCUMENT");
        when(messages.findByDeduplicationKey("RECEIPT_PDF:PAY-2026-000001")).thenReturn(Optional.of(storedMessage));
        when(claims.claim("RECEIPT_PDF:PAY-2026-000001")).thenAnswer(invocation -> {
            storedMessage.markPending();
            return Optional.of(storedMessage);
        });
        when(messages.save(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.saveAndFlush(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WhatsappMessage result = service.sendPaymentUpdate(order.getOrderNumber(), receipt.paymentNumber());

        assertThat(result.getDeduplicationKey()).isEqualTo("RECEIPT_PDF:PAY-2026-000001");
        assertThat(result.getMediaType()).isEqualTo("DOCUMENT");
        assertThat(result.getProviderMessageId()).isEqualTo("wamid.1");
        assertThat(result.getDeliveryStatus()).isEqualTo(com.divinelaundry.domain.WhatsappDeliveryStatus.SENT);
        verify(provider).sendInvoiceAndPaymentDocument(eq("9876543210"), eq(pdf),
                eq("PAY-2026-000001.pdf"), argThat(values -> values.invoiceNumber().equals("PAY-2026-000001")
                        && values.amountPaid().compareTo(new BigDecimal("400.00")) == 0));
        verifyNoInteractions(images, invoices, documents);
    }

    @Test
    void paymentRequestMessageUsesExactRequestedAndRemainingAmounts() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        WhatsappCloudApiClient provider = mock(WhatsappCloudApiClient.class);
        WhatsappMessageClaimService claims = mock(WhatsappMessageClaimService.class);
        LaundryOrder order = invoicedOrder("request-send", "INV-2026-000007");
        order.addItem(new com.divinelaundry.domain.OrderItem(
                new LaundryServiceItem("TEST", "Test service", "Test", PricingUnit.PIECE,
                        new BigDecimal("1725.00")), BigDecimal.ONE, 1, false));
        order.calculateTotals(BigDecimal.ZERO, BigDecimal.ZERO);
        PaymentRequest request = new PaymentRequest(order, new BigDecimal("1000.00"), "INR",
                "razorpay", "admin", "req-payment-whatsapp");
        request.markPending("plink_123", Instant.now().plusSeconds(1800));
        request.setPaymentUrl("https://razorpay.me/plink_123");
        WhatsappMessage message = new WhatsappMessage("PAYMENT_REQUEST:req-payment-whatsapp", order,
                order.getCustomer().getPhone(), "text", "TEXT");
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(messages.findByDeduplicationKey(message.getDeduplicationKey())).thenReturn(Optional.of(message));
        when(provider.isConfigured()).thenReturn(true);
        when(claims.claim(message.getDeduplicationKey())).thenAnswer(invocation -> {
            message.markPending();
            return Optional.of(message);
        });
        when(provider.sendText(eq("9876543210"), anyString()))
                .thenReturn(new WhatsappCloudApiClient.DeliveryResult(null, "wamid.request"));
        when(messages.save(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.saveAndFlush(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WhatsappService service = new WhatsappService(messages, orders, mock(DocumentService.class),
                mock(InvoicePaymentImageService.class), mock(PdfInvoiceService.class),
                mock(PaymentReceiptService.class), mock(PdfReceiptService.class), provider,
                properties(), claims, new WhatsappMessagePersistenceService(messages));

        WhatsappMessage result = service.sendPaymentRequest(request, new BigDecimal("0.00"));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(provider).sendText(eq("9876543210"), text.capture());
        assertThat(text.getValue()).contains("Invoice Total: ₹1725.00", "Paid: ₹0.00",
                "Requested Now: ₹1000.00", "Balance After This Payment: ₹725.00",
                "Please pay exactly ₹1000.00", "https://razorpay.me/plink_123",
                "Invoice: INV-2026-000007");
        assertThat(result.getDeliveryStatus()).isEqualTo(com.divinelaundry.domain.WhatsappDeliveryStatus.SENT);
    }

    @Test
    void classifiedProviderFailureIsPersistedSafelyAndFailedRetryCanSucceed() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        PaymentReceiptService receipts = mock(PaymentReceiptService.class);
        PdfReceiptService receiptPdfs = mock(PdfReceiptService.class);
        WhatsappCloudApiClient provider = mock(WhatsappCloudApiClient.class);
        WhatsappMessageClaimService claims = mock(WhatsappMessageClaimService.class);
        LaundryOrder order = invoicedOrder("retry-send", "INV-2026-000004");
        var receipt = new PaymentReceiptService.PaymentReceiptDocument(null, "PAY-4", "PAY-4",
                Instant.parse("2026-09-20T10:00:00Z"), "CASH", "REF", "admin", order.getOrderNumber(),
                order.getInvoiceNumber(), "Customer", "9876543210", null, "Trichy", BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, "PAID");
        WhatsappMessage message = new WhatsappMessage("RECEIPT_PDF:PAY-4", order,
                order.getCustomer().getPhone(), "document-template", "DOCUMENT");
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(receipts.document(order.getOrderNumber(), "PAY-4")).thenReturn(receipt);
        when(receiptPdfs.render(receipt)).thenReturn(new byte[]{'%', 'P', 'D', 'F'});
        when(provider.isDocumentConfigured()).thenReturn(true);
        when(messages.findByDeduplicationKey("RECEIPT_PDF:PAY-4")).thenReturn(Optional.of(message));
        when(messages.save(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.saveAndFlush(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.claim("RECEIPT_PDF:PAY-4")).thenAnswer(invocation -> {
            message.markPending();
            return Optional.of(message);
        });
        when(provider.sendInvoiceAndPaymentDocument(anyString(), any(), anyString(), any()))
                .thenThrow(new WhatsappCloudApiClient.WhatsappProviderException(
                        WhatsappFailureClassification.AUTHENTICATION_FAILURE, "HTTP 401"))
                .thenReturn(new WhatsappCloudApiClient.DeliveryResult("media-4", "wamid.4"));
        WhatsappService service = new WhatsappService(messages, orders, mock(DocumentService.class),
                mock(InvoicePaymentImageService.class), mock(PdfInvoiceService.class), receipts, receiptPdfs,
                provider, properties(), claims, new WhatsappMessagePersistenceService(messages));

        WhatsappMessage failed = service.sendPaymentUpdate(order.getOrderNumber(), "PAY-4");
        assertThat(failed.getDeliveryStatus()).isEqualTo(com.divinelaundry.domain.WhatsappDeliveryStatus.FAILED);
        assertThat(failed.getLastError()).isEqualTo("AUTHENTICATION_FAILURE");
        assertThat(failed.getLastError()).doesNotContain("HTTP 401", "Bearer", "token", "provider");

        WhatsappMessage sent = service.sendPaymentUpdate(order.getOrderNumber(), "PAY-4");
        assertThat(sent.getDeliveryStatus()).isEqualTo(com.divinelaundry.domain.WhatsappDeliveryStatus.SENT);
        verify(provider, times(2)).sendInvoiceAndPaymentDocument(anyString(), any(), anyString(), any());
    }

    @Test
    void retryRejectsTerminalAndPendingMessages() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrder order = invoicedOrder("retry-state", "INV-2026-000005");
        WhatsappMessage sent = new WhatsappMessage("INVOICE_IMAGE:INV-2026-000005", order,
                "9876543210", "template");
        sent.markPending();
        sent.markSent("media", "wamid");
        WhatsappMessage pending = new WhatsappMessage("INVOICE_PDF:INV-2026-000005", order,
                "9876543210", "template", "DOCUMENT");
        pending.markPending();
        when(messages.findByIdAndOrder_OrderNumber(1L, order.getOrderNumber())).thenReturn(Optional.of(sent));
        when(messages.findByIdAndOrder_OrderNumber(2L, order.getOrderNumber())).thenReturn(Optional.of(pending));
        WhatsappService service = new WhatsappService(messages, mock(LaundryOrderRepository.class),
                mock(DocumentService.class), mock(InvoicePaymentImageService.class), mock(PdfInvoiceService.class),
                mock(PaymentReceiptService.class), mock(PdfReceiptService.class), mock(WhatsappCloudApiClient.class),
                properties(), mock(WhatsappMessageClaimService.class), new WhatsappMessagePersistenceService(messages));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.retry(order.getOrderNumber(), 1L))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.retry(order.getOrderNumber(), 2L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionServiceUsesInjectedConfiguredClaimTimeout() {
        WhatsappMessageRepository messages = mock(WhatsappMessageRepository.class);
        LaundryOrderRepository orders = mock(LaundryOrderRepository.class);
        DocumentService documents = mock(DocumentService.class);
        InvoicePaymentImageService images = mock(InvoicePaymentImageService.class);
        WhatsappCloudApiClient provider = mock(WhatsappCloudApiClient.class);
        LaundryOrder order = invoicedOrder("configured-timeout", "INV-2026-000006");
        String key = "INVOICE_IMAGE:INV-2026-000006";
        WhatsappMessage message = new WhatsappMessage(key, order, "9876543210", "image-template");
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AtomicReference<Instant> staleBefore = new AtomicReference<>();
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(messages.findByDeduplicationKey(key)).thenReturn(Optional.of(message));
        when(messages.claimForDelivery(eq(key), eq(now), any())).thenAnswer(invocation -> {
            staleBefore.set(invocation.getArgument(2));
            message.markPending();
            return 1;
        });
        DocumentService.DocumentBundle document = new DocumentService.DocumentBundle(null, order,
                new PaymentService.PaymentSummary(order, BigDecimal.ZERO, order.getTotal(), List.of()), List.of());
        when(documents.document(order.getOrderNumber())).thenReturn(document);
        when(images.render(document)).thenReturn(new byte[]{1});
        when(provider.isConfigured()).thenReturn(true);
        when(provider.sendInvoiceAndPaymentImage(anyString(), any(), anyString(), any()))
                .thenReturn(new WhatsappCloudApiClient.DeliveryResult("media", "wamid"));
        when(messages.save(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.saveAndFlush(any(WhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WhatsappMessageClaimService configuredClaims = new WhatsappMessageClaimService(
                messages, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(5));
        WhatsappService service = new WhatsappService(messages, orders, documents, images,
                mock(PdfInvoiceService.class), mock(PaymentReceiptService.class), mock(PdfReceiptService.class),
                provider, properties(), configuredClaims, new WhatsappMessagePersistenceService(messages));

        WhatsappMessage result = service.queueInvoice(order.getOrderNumber());

        assertThat(staleBefore).hasValue(now.minus(Duration.ofMinutes(5)));
        assertThat(result.getDeliveryStatus()).isEqualTo(com.divinelaundry.domain.WhatsappDeliveryStatus.SENT);
        verify(provider).sendInvoiceAndPaymentImage(anyString(), any(), anyString(), any());
    }

        private static LaundryOrder invoicedOrder(String requestId, String invoiceNumber) {
                LaundryOrder order = new LaundryOrder(requestId, new Customer("Customer", "9876543210", null, "Trichy"),
                                null, null, "admin");
                order.assignOrderNumber("SO-2026-" + invoiceNumber.substring(invoiceNumber.length() - 6));
                order.finalizeInvoice(invoiceNumber);
                return order;
        }

        private static WhatsappProviderProperties properties() {
                return new WhatsappProviderProperties(true, "https://graph.example", "v-test", "phone-id", "secret-token",
                                "image-template", "en", "document-template", "en");
        }

}
