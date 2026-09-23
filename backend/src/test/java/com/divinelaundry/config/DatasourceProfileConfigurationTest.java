package com.divinelaundry.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceProfileConfigurationTest {
    private final PropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

        @Test
        void applicationDefaultsToLocalProfile() throws IOException {
                PropertySource<?> properties = load("application.yml");

                assertThat(properties.getProperty("spring.profiles.default")).isEqualTo("local");
        }

    @Test
    void localProfileUsesMysqlDefaultsAndExternalCredentials() throws IOException {
        PropertySource<?> properties = load("application-local.yml");

        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("${DB_URL:jdbc:mysql://127.0.0.1:3306/divine_laundry?connectionTimeZone=UTC}");
        assertThat(properties.getProperty("spring.datasource.username"))
                .isEqualTo("${DB_USERNAME:laundry_app}");
        assertThat(properties.getProperty("spring.datasource.password"))
                .isEqualTo("${DB_PASSWORD:}");
        assertThat(properties.getProperty("spring.flyway.user"))
                .isEqualTo("${FLYWAY_DB_USERNAME:laundry_migrator}");
        assertThat(properties.getProperty("spring.flyway.password"))
                .isEqualTo("${FLYWAY_DB_PASSWORD:}");
    }

    @Test
    void demoProfileRemainsSafeForLocalTesting() throws IOException {
        PropertySource<?> properties = load("application-demo.yml");

        assertThat(properties.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:file:./data/laundry-demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo(false);
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        List<PropertySource<?>> sources = yamlLoader.load(resourceName, new ClassPathResource(resourceName));
        assertThat(sources).hasSize(1);
        return sources.getFirst();
    }
}