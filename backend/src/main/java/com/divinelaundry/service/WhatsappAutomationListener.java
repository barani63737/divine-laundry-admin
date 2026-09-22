package com.divinelaundry.service;

import com.divinelaundry.domain.WhatsappMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.repository.PaymentRequestRepository;

@Component
public class WhatsappAutomationListener {
    private static final Logger log = LoggerFactory.getLogger(WhatsappAutomationListener.class);

    private final WhatsappService whatsapp;
    private final PaymentRequestRepository paymentRequests;

    public WhatsappAutomationListener(WhatsappService whatsapp, PaymentRequestRepository paymentRequests) {
        this.whatsapp = whatsapp;
        this.paymentRequests = paymentRequests;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invoiceFinalised(InvoiceFinalisedEvent event) {
        try {
            whatsapp.queueInvoice(event.orderNumber());
        } catch (RuntimeException error) {
            log.error("Automatic WhatsApp invoice send failed for {}", event.orderNumber(), error);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void paymentRecorded(PaymentRecordedEvent event) {
        try {
            whatsapp.sendPaymentUpdate(event.orderNumber(), event.paymentNumber());
            whatsapp.sendPaymentConfirmation(event.orderNumber(), event.paymentNumber());
        } catch (RuntimeException error) {
            log.error("Automatic WhatsApp payment update failed for {}", event.orderNumber(), error);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void paymentRequestCreated(PaymentRequestCreatedEvent event) {
        try {
            PaymentRequest request = paymentRequests.findByIdempotencyKey(event.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Payment request not found after commit"));
            whatsapp.sendPaymentRequest(request, event.amountPaidBefore());
        } catch (RuntimeException error) {
            log.error("Automatic WhatsApp payment request send failed for {}", event.orderNumber(), error);
        }
    }
}
