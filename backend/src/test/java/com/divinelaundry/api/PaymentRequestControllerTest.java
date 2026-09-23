package com.divinelaundry.api;

import com.divinelaundry.domain.PaymentRequest;
import com.divinelaundry.service.PaymentRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-request-controller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.username=sa",
        "spring.datasource.hikari.password=",
        "app.admin.username=admin",
        "app.admin.password=TestPassword123!",
        "app.whatsapp.enabled=false",
        "app.payment.upi-id="
})
@ActiveProfiles("test")
class PaymentRequestControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private PaymentRequestService paymentRequestService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createPaymentRequestEndpointReturnsSafePayload() throws Exception {
        PaymentRequest request = new PaymentRequest(
                MockPaymentProviderTestOrderFactory.order("SO-2026-000101"),
                new BigDecimal("1000.00"),
                "INR",
                "mock-local",
                "admin",
                "req-1000");
        request.markPending("MOCK-REF-100", Instant.now().plusSeconds(600));
        request.setPaymentUrl("mock-local://payment/MOCK-REF-100");
        request.setQrPayload("upi://pay?pa=mock%40divinelaundry&am=1000.00&cu=INR");

        when(paymentRequestService.createPaymentRequest(eq("SO-2026-000101"), eq(new BigDecimal("1000.00")), eq("admin"), eq("req-1000")))
                .thenReturn(request);

        mockMvc.perform(post("/api/orders/SO-2026-000101/payment-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00,\"idempotencyKey\":\"req-1000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("SO-2026-000101"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("mock-local"))
                .andExpect(jsonPath("$.paymentUrl").value("mock-local://payment/MOCK-REF-100"))
                .andExpect(jsonPath("$.qrPayload").value(org.hamcrest.Matchers.containsString("upi://pay?")));
    }

    @Test
    void createPaymentRequestRequiresAdminAuthAndCsrf() throws Exception {
        mockMvc.perform(post("/api/orders/SO-2026-000101/payment-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00,\"idempotencyKey\":\"req-no-auth\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/SO-2026-000101/payment-requests")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("not-admin").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00,\"idempotencyKey\":\"req-no-role\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/SO-2026-000101/payment-requests")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00,\"idempotencyKey\":\"req-no-csrf\"}"))
                .andExpect(status().isForbidden());
    }

    static final class MockPaymentProviderTestOrderFactory {
        static com.divinelaundry.domain.LaundryOrder order(String orderNumber) {
            com.divinelaundry.domain.Customer customer = new com.divinelaundry.domain.Customer("Test Customer", "9876543210", null, "Trichy");
            com.divinelaundry.domain.LaundryServiceItem item = new com.divinelaundry.domain.LaundryServiceItem(
                    "WASH_IRON_KG", "Wash & Iron", "Laundry by KG", com.divinelaundry.domain.PricingUnit.KG, new BigDecimal("120.00"));
            com.divinelaundry.domain.LaundryOrder order = new com.divinelaundry.domain.LaundryOrder("req-order", customer, null, null, "admin");
            order.addItem(new com.divinelaundry.domain.OrderItem(item, BigDecimal.ONE, 1, false));
            order.calculateTotals(BigDecimal.ZERO, BigDecimal.ZERO);
            order.assignOrderNumber(orderNumber);
            return order;
        }
    }
}
