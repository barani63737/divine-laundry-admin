package com.divinelaundry.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {
    @Test
    void weightAndPhysicalPiecesRemainIndependent() {
        LaundryServiceItem service = new LaundryServiceItem(
                "WASH_IRON_KG", "Wash & Iron", "Laundry by KG", PricingUnit.KG, new BigDecimal("115.00"));

        OrderItem item = new OrderItem(service, new BigDecimal("1.39"), 7, false);

        assertThat(item.getBillableQuantity()).isEqualByComparingTo("1.39");
        assertThat(item.getPieceCount()).isEqualTo(7);
        assertThat(item.getLineTotal()).isEqualByComparingTo("159.85");
    }

    @Test
    void keepsTheOriginalCatalogPriceWhenTheServicePriceChanges() {
        LaundryServiceItem service = new LaundryServiceItem(
                "CURTAIN", "Curtain cleaning", "Sofa Cleaning", PricingUnit.PIECE, new BigDecimal("50.00"));

        OrderItem item = new OrderItem(service, BigDecimal.valueOf(2), 2, false);
        service.updateDetails(service.getName(), service.getCategory(), service.getPricingUnit(),
                new BigDecimal("60.00"), true);

        assertThat(item.getUnitRate()).isEqualByComparingTo("50.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("100.00");
    }
}
