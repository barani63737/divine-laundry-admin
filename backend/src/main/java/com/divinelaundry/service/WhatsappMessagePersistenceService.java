package com.divinelaundry.service;

import com.divinelaundry.domain.WhatsappMessage;
import com.divinelaundry.repository.WhatsappMessageRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
public class WhatsappMessagePersistenceService {
    private final WhatsappMessageRepository messages;

    public WhatsappMessagePersistenceService(WhatsappMessageRepository messages) {
        this.messages = messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsappMessage createIfAbsent(String deduplicationKey, Supplier<WhatsappMessage> factory) {
        return messages.findByDeduplicationKey(deduplicationKey)
                .orElseGet(() -> {
                    try {
                        return messages.saveAndFlush(factory.get());
                    } catch (DataIntegrityViolationException duplicate) {
                        return messages.findByDeduplicationKey(deduplicationKey)
                                .orElseThrow(() -> duplicate);
                    }
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WhatsappMessage save(WhatsappMessage message) {
        WhatsappMessage saved = messages.saveAndFlush(message);
        WhatsappMessage reloaded = messages.findById(saved.getId()).orElse(saved);
        Hibernate.initialize(reloaded.getOrder());
        reloaded.getOrder().getOrderNumber();
        reloaded.getOrder().getInvoiceNumber();
        return reloaded;
    }
}
