package com.divinelaundry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPaymentProviderBeanTest {

    @Test
    void localAndTestProfilesUseMockProvider() {
        assertThat(MockPaymentProvider.class.getAnnotation(Profile.class).value())
                .containsExactly("local", "demo", "test");
        assertThat(ProductionPaymentProvider.class.getAnnotation(Profile.class).value())
                .containsExactly("!local & !demo & !test");
    }

    @Test
    void localProfileSelectsMockProvider() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("local");
        environment.getPropertySources().addFirst(new MapPropertySource("provider-test", Map.of("razorpay.enabled", "false")));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(TestConfig.class, ProductionPaymentProvider.class, RazorpayPaymentProvider.class, MockPaymentProvider.class);
            context.refresh();

            assertThat(context.getBeanProvider(MockPaymentProvider.class).getIfAvailable()).isNotNull();
            assertThat(context.getBeanProvider(ProductionPaymentProvider.class).getIfAvailable()).isNull();
            assertThat(context.getBeanProvider(RazorpayPaymentProvider.class).getIfAvailable()).isNull();
        }
    }

    @Test
    void razorpayProviderIsDisabledUntilConfigured() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        environment.getPropertySources().addFirst(new MapPropertySource("provider-test", Map.of("razorpay.enabled", "false")));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(TestConfig.class, ProductionPaymentProvider.class, RazorpayPaymentProvider.class, MockPaymentProvider.class);
            context.refresh();

            assertThat(context.getBeanProvider(ProductionPaymentProvider.class).getIfAvailable()).isNotNull();
            assertThat(context.getBeanProvider(RazorpayPaymentProvider.class).getIfAvailable()).isNull();
            assertThat(context.getBeanProvider(MockPaymentProvider.class).getIfAvailable()).isNull();
                assertThatThrownBy(() -> context.getBean(ProductionPaymentProvider.class)
                    .createPaymentRequest(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Real payment provider not configured");
        }
    }

    @Test
    void razorpayProviderIsSelectedWhenEnabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        environment.getPropertySources().addFirst(new MapPropertySource("provider-test", Map.of(
                "razorpay.enabled", "true",
                "razorpay.key-id", "rzp_test_123",
                "razorpay.key-secret", "secret_123")));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.register(TestConfig.class, ProductionPaymentProvider.class, RazorpayPaymentProvider.class, MockPaymentProvider.class);
            context.refresh();

            assertThat(context.getBeanProvider(RazorpayPaymentProvider.class).getIfAvailable()).isNotNull();
            assertThat(context.getBeanProvider(ProductionPaymentProvider.class).getIfAvailable()).isNull();
            assertThat(context.getBeanProvider(MockPaymentProvider.class).getIfAvailable()).isNull();
        }
    }

    @Test
    void razorpayProviderFailsFastWhenCredentialsAreMissing() {
        assertThatThrownBy(() -> new RazorpayPaymentProvider(new org.springframework.web.client.RestTemplate(),
                "https://api.razorpay.com", "", "", new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_ID")
                .hasMessageContaining("RAZORPAY_KEY_SECRET");
    }

    @Configuration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
