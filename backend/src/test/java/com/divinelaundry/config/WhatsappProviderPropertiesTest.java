package com.divinelaundry.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import com.divinelaundry.service.WhatsappProviderPreflight;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappProviderPropertiesTest {
    @Test
    void localYamlMapsWhatsappEnabledToDocumentedEnvironmentVariable() throws Exception {
        var sources = new YamlPropertySourceLoader().load(
                "application-local", new ClassPathResource("application-local.yml"));

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getProperty("app.whatsapp.enabled"))
                .isEqualTo("${WHATSAPP_ENABLED:false}");
    }

    @Test
    void disabledModeDoesNotRequireCredentials() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                false, "", "", "", "", "", "", "", "");

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.configurationMessage()).isEqualTo("WhatsApp automatic sending is disabled");
        assertThat(properties.configurationState()).isEqualTo("DISABLED");
    }

    @Test
    void enabledButIncompleteModeIsNotReportedAsDisabled() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, "https://graph.example", "v1", "123", "token", "image", "en");

        assertThat(properties.isDocumentConfigured()).isFalse();
        assertThat(properties.configurationState()).isEqualTo("ENABLED_CONFIGURATION_INCOMPLETE");
        assertThat(properties.configurationMessage()).doesNotContain("disabled");
    }

    @Test
    void enabledModeRejectsInvalidUrlAndPhoneIdSafely() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, "not-a-url?token=secret", "v1", "123/456", "token-value", "image", "en",
                "document", "en");

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.configurationMessage()).isEqualTo("WHATSAPP_GRAPH_BASE_URL must be an HTTP(S) URL");
        assertThat(properties.configurationMessage()).doesNotContain("token-value", "secret");
    }

    @Test
    void surroundingWhitespaceIsTrimmedFromConfigurationValues() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, " https://graph.example ", " v1 ", " 123 ", " token ", " image ", " en ",
                " document ", " en ");

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.endpoint("media")).isEqualTo("https://graph.example/v1/123/media");
    }

    @Test
    void preflightValidatesConfigurationLocallyWithoutCallingMeta() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, "https://graph.example", "v1", "123", "token", "image", "en", "document", "en");

        var result = new WhatsappProviderPreflight(properties, "PT5M").validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.state()).isEqualTo("READY");
        assertThat(result.message()).isEqualTo("Pending timeout PT5M");
    }

    @Test
    void preflightRejectsInvalidTimeoutWithoutExposingSecrets() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, "https://graph.example", "v1", "123", "secret-token", "image", "en", "document", "en");

        var result = new WhatsappProviderPreflight(properties, "PT0S").validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("WHATSAPP_PENDING_TIMEOUT");
        assertThat(result.message()).doesNotContain("secret-token");
    }
}
