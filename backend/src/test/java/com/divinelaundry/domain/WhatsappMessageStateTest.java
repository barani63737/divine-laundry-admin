package com.divinelaundry.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsappMessageStateTest {
    @Test
    void sentCannotRegressToFailedOrPending() {
        WhatsappMessage message = message();
        message.markPending();
        message.markSent("media-1", "wamid.1");

        assertThatThrownBy(() -> message.markFailed("PROVIDER_SERVER_ERROR"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(message::markPending)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deliveredCannotRegressToFailedOrPending() throws Exception {
        WhatsappMessage message = message();
        setStatus(message, WhatsappDeliveryStatus.DELIVERED);

        assertThatThrownBy(() -> message.markFailed("PROVIDER_SERVER_ERROR"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(message::markPending)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outcomesRequirePendingState() {
        WhatsappMessage message = message();
        assertThatThrownBy(() -> message.markSent("media-1", "wamid.1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> message.markFailed("UNKNOWN_FAILURE"))
                .isInstanceOf(IllegalStateException.class);
    }

        @Test
        void sentRequiresProviderMessageId() {
        WhatsappMessage message = message();
        message.markPending();

        assertThatThrownBy(() -> message.markSent("media-1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Provider message ID is required");
        assertThat(message.getDeliveryStatus()).isEqualTo(WhatsappDeliveryStatus.PENDING);
        }

    private static WhatsappMessage message() {
        return new WhatsappMessage("INVOICE_IMAGE:INV-1", null, "9876543210", "template");
    }

    private static void setStatus(WhatsappMessage message, WhatsappDeliveryStatus status) throws Exception {
        Field field = WhatsappMessage.class.getDeclaredField("deliveryStatus");
        field.setAccessible(true);
        field.set(message, status);
    }
}