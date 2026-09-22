package com.divinelaundry.service;

import com.divinelaundry.config.WhatsappProviderProperties;
import com.divinelaundry.domain.LaundryOrder;
import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.domain.WhatsappMessage;
import com.divinelaundry.repository.LaundryOrderRepository;
import com.divinelaundry.repository.WhatsappMessageRepository;
import com.divinelaundry.repository.PaymentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class WhatsappService {
    private final WhatsappMessageRepository messages;
    private final LaundryOrderRepository orders;
    private final DocumentService documents;
    private final InvoicePaymentImageService imageService;
    private final PdfInvoiceService pdfInvoices;
    private final PaymentReceiptService paymentReceipts;
    private final PdfReceiptService pdfReceipts;
    private final WhatsappCloudApiClient provider;
    private final WhatsappProviderProperties properties;
    private final WhatsappMessageClaimService claims;
    private final WhatsappMessagePersistenceService persistence;
    private final PaymentRequestRepository paymentRequests;
    private final PaymentService paymentService;

    public WhatsappService(
            WhatsappMessageRepository messages,
            LaundryOrderRepository orders,
            DocumentService documents,
            InvoicePaymentImageService imageService,
            PdfInvoiceService pdfInvoices,
            PaymentReceiptService paymentReceipts,
            PdfReceiptService pdfReceipts,
            WhatsappCloudApiClient provider,
            WhatsappProviderProperties properties,
            WhatsappMessageClaimService claims,
            WhatsappMessagePersistenceService persistence) {
            this(messages, orders, documents, imageService, pdfInvoices, paymentReceipts, pdfReceipts,
                provider, properties, claims, persistence, null, null);
            }

            @Autowired
            public WhatsappService(
                WhatsappMessageRepository messages,
                LaundryOrderRepository orders,
                DocumentService documents,
                InvoicePaymentImageService imageService,
                PdfInvoiceService pdfInvoices,
                PaymentReceiptService paymentReceipts,
                PdfReceiptService pdfReceipts,
                WhatsappCloudApiClient provider,
                WhatsappProviderProperties properties,
                WhatsappMessageClaimService claims,
                WhatsappMessagePersistenceService persistence,
                PaymentRequestRepository paymentRequests,
                PaymentService paymentService) {
        this.messages = messages;
        this.orders = orders;
        this.documents = documents;
        this.imageService = imageService;
        this.pdfInvoices = pdfInvoices;
        this.paymentReceipts = paymentReceipts;
        this.pdfReceipts = pdfReceipts;
        this.provider = provider;
        this.properties = properties;
        this.claims = claims;
        this.persistence = persistence;
        this.paymentRequests = paymentRequests;
        this.paymentService = paymentService;
    }

    public WhatsappMessage queueInvoice(String orderNumber) {
        LaundryOrder order = requiredInvoicedOrder(orderNumber);
        return deliver(order, "INVOICE_IMAGE:" + order.getInvoiceNumber());
    }

    public WhatsappMessage sendPaymentUpdate(String orderNumber, String paymentNumber) {
        LaundryOrder order = requiredInvoicedOrder(orderNumber);
        PaymentReceiptService.PaymentReceiptDocument receipt = paymentReceipts.document(orderNumber, paymentNumber);
        return deliverDocument(order, "RECEIPT_PDF:" + paymentNumber,
            pdfReceipts.render(receipt), paymentNumber + ".pdf",
            new WhatsappCloudApiClient.TemplateValues(
                receipt.customerName(), receipt.receiptNumber(), receipt.orderNumber(),
                receipt.orderTotal(), receipt.amountReceived(), receipt.remainingOutstanding()));
        }

    public WhatsappMessage sendPaymentConfirmation(String orderNumber, String paymentNumber) {
        LaundryOrder order = requiredInvoicedOrder(orderNumber);
        PaymentReceiptService.PaymentReceiptDocument receipt = paymentReceipts.document(orderNumber, paymentNumber);
        return deliverText(order, "PAYMENT_CONFIRMATION:" + paymentNumber, paymentConfirmation(receipt));
    }

    public WhatsappMessage sendPaymentRequest(PaymentRequest request, BigDecimal amountPaidBefore) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request is required");
        }
        LaundryOrder order = requiredInvoicedOrder(request.getOrderNumber());
        BigDecimal balanceAfter = order.getTotal().subtract(amountPaidBefore)
                .subtract(request.getRequestedAmount()).max(BigDecimal.ZERO);
        return deliverText(order, "PAYMENT_REQUEST:" + request.getIdempotencyKey(),
                paymentRequestMessage(order, request, amountPaidBefore, balanceAfter));
    }

    public WhatsappMessage queueInvoicePdf(String orderNumber) {
        LaundryOrder order = requiredInvoicedOrder(orderNumber);
        DocumentService.DocumentBundle document = documents.document(orderNumber);
        return deliverDocument(order, "INVOICE_PDF:" + order.getInvoiceNumber(),
            pdfInvoices.render(document), order.getInvoiceNumber() + ".pdf",
            new WhatsappCloudApiClient.TemplateValues(
                order.getCustomer().getName(), order.getInvoiceNumber(), order.getOrderNumber(),
                order.getTotal(), document.paymentSummary().amountPaid(), document.paymentSummary().balance()));
    }

    public WhatsappMessage retry(String orderNumber, Long messageId) {
        WhatsappMessage message = messages.findByIdAndOrder_OrderNumber(messageId, orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp message not found for this order"));
        if (message.getDeliveryStatus() != com.divinelaundry.domain.WhatsappDeliveryStatus.FAILED) {
            throw new IllegalStateException("Only failed WhatsApp messages can be retried");
        }
        String key = message.getDeduplicationKey();
        if (key.startsWith("INVOICE_IMAGE:")) return queueInvoice(orderNumber);
        if (key.startsWith("INVOICE_PDF:")) return queueInvoicePdf(orderNumber);
        if (key.startsWith("RECEIPT_PDF:")) return sendPaymentUpdate(orderNumber, key.substring("RECEIPT_PDF:".length()));
        if (key.startsWith("PAYMENT_CONFIRMATION:")) {
            return sendPaymentConfirmation(orderNumber, key.substring("PAYMENT_CONFIRMATION:".length()));
        }
        if (key.startsWith("PAYMENT_REQUEST:")) {
            if (paymentRequests == null) throw new IllegalStateException("Payment request retry is unavailable");
            if (paymentService == null) throw new IllegalStateException("Payment request retry is unavailable");
            PaymentRequest request = paymentRequests.findByIdempotencyKey(
                key.substring("PAYMENT_REQUEST:".length()))
                .orElseThrow(() -> new IllegalArgumentException("Payment request not found"));
            return sendPaymentRequest(request, paymentService.summary(orderNumber).amountPaid());
        }
        throw new IllegalStateException("Unsupported WhatsApp message type");
    }

    private WhatsappMessage deliverText(LaundryOrder order, String deduplicationKey, String text) {
        String phone = order.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            throw new IllegalStateException("Customer phone number is required for WhatsApp delivery");
        }
        WhatsappMessage message = getOrCreate(deduplicationKey,
                () -> new WhatsappMessage(deduplicationKey, order, phone, "text", "TEXT"));
        if (message.isDeliveredOrSent()) return message;
        if (!provider.isConfigured()) {
            message.waitingForProvider(provider.configurationMessage());
            return persistence.save(message);
        }
        java.util.Optional<WhatsappMessage> claimed = claims.claim(deduplicationKey);
        if (claimed.isEmpty()) return messages.findByDeduplicationKey(deduplicationKey).orElse(message);
        message = claimed.get();
        if (message.getDeliveryStatus() != com.divinelaundry.domain.WhatsappDeliveryStatus.PENDING) return message;
        try {
            WhatsappCloudApiClient.DeliveryResult result = provider.sendText(phone, text);
            message.markSent(null, result.providerMessageId());
        } catch (RuntimeException error) {
            message.markFailed(failureDescription(error));
        }
        return persistence.save(message);
    }

    private static String paymentRequestMessage(LaundryOrder order, PaymentRequest request,
            BigDecimal amountPaidBefore, BigDecimal balanceAfter) {
        return "Divine Laundry\n\nHello " + order.getCustomer().getName() + ",\n\n"
                + "Payment request for Order " + order.getOrderNumber() + "\n\n"
                + "Invoice Total: ₹" + money(order.getTotal()) + "\n"
                + "Paid: ₹" + money(amountPaidBefore) + "\n"
                + "Requested Now: ₹" + money(request.getRequestedAmount()) + "\n"
                + "Balance After This Payment: ₹" + money(balanceAfter) + "\n\n"
                + "Please pay exactly ₹" + money(request.getRequestedAmount())
                + " using the secure payment link:\n" + request.getPaymentUrl() + "\n\n"
                + "Order: " + order.getOrderNumber() + "\n"
                + "Invoice: " + order.getInvoiceNumber() + "\n\nThank you,\nDivine Laundry";
    }

    private static String paymentConfirmation(PaymentReceiptService.PaymentReceiptDocument receipt) {
        String reference = receipt.transactionReference() == null || receipt.transactionReference().isBlank()
                ? receipt.paymentNumber() : receipt.transactionReference();
        return "Payment Received - " + receipt.business().name() + "\n\nHello " + receipt.customerName() + ",\n\n"
                + "We received your payment of ₹" + money(receipt.amountReceived()) + ".\n\n"
                + "Order: " + receipt.orderNumber() + "\nInvoice: " + receipt.invoiceNumber() + "\n"
                + "Payment Reference: " + reference + "\n\n"
                + "Total Paid: ₹" + money(receipt.orderTotal().subtract(receipt.remainingOutstanding())) + "\n"
                + "Outstanding: ₹" + money(receipt.remainingOutstanding()) + "\n"
                + "Payment Status: " + receipt.paymentStatus() + "\n\nThank you,\n" + receipt.business().name();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private WhatsappMessage deliver(LaundryOrder order, String deduplicationKey) {
        String phone = order.getCustomer().getPhone();
        if (phone == null || phone.isBlank()) {
            throw new IllegalStateException("Customer phone number is required for WhatsApp delivery");
        }
        WhatsappMessage message = getOrCreate(deduplicationKey,
            () -> new WhatsappMessage(deduplicationKey, order, phone, properties.templateName()));
        if (message.isDeliveredOrSent()) return message;

        if (!provider.isConfigured()) {
            message.waitingForProvider(provider.configurationMessage());
            return persistence.save(message);
        }

        java.util.Optional<WhatsappMessage> claimed = claims.claim(deduplicationKey);
        if (claimed.isEmpty()) {
            return messages.findByDeduplicationKey(deduplicationKey).orElse(message);
        }
        message = claimed.get();
        if (message.getDeliveryStatus() != com.divinelaundry.domain.WhatsappDeliveryStatus.PENDING) return message;
        try {
            DocumentService.DocumentBundle bundle = documents.document(order.getOrderNumber());
            byte[] png = imageService.render(bundle);
            WhatsappCloudApiClient.DeliveryResult result = provider.sendInvoiceAndPaymentImage(
                    phone,
                    png,
                    order.getInvoiceNumber() + ".png",
                    new WhatsappCloudApiClient.TemplateValues(
                            order.getCustomer().getName(),
                            order.getInvoiceNumber(),
                            order.getOrderNumber(),
                            order.getTotal(),
                            bundle.paymentSummary().amountPaid(),
                            bundle.paymentSummary().balance()));
            message.markSent(result.mediaId(), result.providerMessageId());
        } catch (RuntimeException error) {
            message.markFailed(failureDescription(error));
        }
        return persistence.save(message);
    }

        private WhatsappMessage deliverDocument(LaundryOrder order, String deduplicationKey,
            byte[] pdf, String filename,
            WhatsappCloudApiClient.TemplateValues values) {
        WhatsappMessage message = getOrCreate(deduplicationKey,
            () -> new WhatsappMessage(deduplicationKey, order, order.getCustomer().getPhone(),
                properties.documentTemplateName(), "DOCUMENT"));
        if (message.isDeliveredOrSent()) return message;
        if (!provider.isDocumentConfigured()) {
            message.waitingForProvider(provider.configurationMessage());
            return persistence.save(message);
        }
        java.util.Optional<WhatsappMessage> claimed = claims.claim(deduplicationKey);
        if (claimed.isEmpty()) {
            return messages.findByDeduplicationKey(deduplicationKey).orElse(message);
        }
        message = claimed.get();
        if (message.getDeliveryStatus() != com.divinelaundry.domain.WhatsappDeliveryStatus.PENDING) return message;
        try {
            WhatsappCloudApiClient.DeliveryResult result = provider.sendInvoiceAndPaymentDocument(
                    order.getCustomer().getPhone(), pdf, filename, values);
            message.markSent(result.mediaId(), result.providerMessageId());
        } catch (RuntimeException error) {
            message.markFailed(failureDescription(error));
        }
        return persistence.save(message);
    }

    private WhatsappMessage getOrCreate(String deduplicationKey,
            java.util.function.Supplier<WhatsappMessage> factory) {
        return persistence.createIfAbsent(deduplicationKey, factory);
    }

    private static String failureDescription(RuntimeException error) {
        if (error instanceof WhatsappCloudApiClient.WhatsappProviderException providerError) {
            return providerError.classification().name();
        }
        return WhatsappFailureClassification.UNKNOWN_FAILURE.name();
    }

    private LaundryOrder requiredInvoicedOrder(String orderNumber) {
        LaundryOrder order = orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (order.getInvoiceNumber() == null) {
            throw new IllegalStateException("Create the invoice before WhatsApp delivery");
        }
        return order;
    }
}
