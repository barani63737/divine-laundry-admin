package com.divinelaundry.web;

import com.divinelaundry.config.WhatsappProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;

class AdminWebControllerConfigurationTest {
    @Test
    void enabledWhatsappConfigurationIsNotReportedAsDisabledInAdminModel() {
        WhatsappProviderProperties properties = new WhatsappProviderProperties(
                true, "https://graph.example", "v1", "123", "token", "image", "en");
        AdminWebController controller = new AdminWebController(
                null, null, null, null, null, null, null, null, null, null,
                "UTC", "Divine Laundry", "", null, null, null, null, null, null,
                properties, null, null, null);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.common(model);

        assertThat(model.getAttribute("whatsappConfigurationState"))
                .isEqualTo("ENABLED_CONFIGURATION_INCOMPLETE");
        assertThat(model.getAttribute("whatsappConfigurationState"))
                .isNotEqualTo("DISABLED");
    }
}
