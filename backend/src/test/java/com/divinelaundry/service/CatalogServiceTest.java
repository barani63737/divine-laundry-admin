package com.divinelaundry.service;

import com.divinelaundry.domain.LaundryServiceItem;
import com.divinelaundry.domain.PricingUnit;
import com.divinelaundry.repository.LaundryServiceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final LaundryServiceRepository repository = mock(LaundryServiceRepository.class);
    private final CatalogService catalog = new CatalogService(repository);

    @Test
    void createsServiceWithCatalogDetails() {
        when(repository.existsByCodeIgnoreCase("CURTAIN_PREMIUM")).thenReturn(false);
        when(repository.save(any(LaundryServiceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LaundryServiceItem result = catalog.create("Premium Curtain Cleaning", " curtain_premium ",
                "Sofa Cleaning", PricingUnit.PIECE, new BigDecimal("299"), true);

        assertThat(result.getCode()).isEqualTo("CURTAIN_PREMIUM");
        assertThat(result.getName()).isEqualTo("Premium Curtain Cleaning");
        assertThat(result.getUnitRate()).isEqualByComparingTo("299");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void rejectsDuplicateServiceCode() {
        when(repository.existsByCodeIgnoreCase("SHIRT_IRON")).thenReturn(true);

        assertThatThrownBy(() -> catalog.create("Shirt ironing", "shirt_iron", "Ironing",
                PricingUnit.PIECE, BigDecimal.TEN, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service code already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> catalog.create("Invalid", "INVALID_PRICE", "Ironing",
                PricingUnit.PIECE, new BigDecimal("-0.01"), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Price must be zero or greater");
        verify(repository, never()).save(any());
    }

    @Test
    void editsPriceAndStatusWithoutChangingServiceCode() {
        LaundryServiceItem service = new LaundryServiceItem("SOFA_PREMIUM", "Premium sofa", "Sofa Cleaning",
                PricingUnit.PIECE, new BigDecimal("299"));
        when(repository.findById(7L)).thenReturn(Optional.of(service));

        LaundryServiceItem result = catalog.update(7L, "Premium sofa", "Sofa Cleaning", PricingUnit.PIECE,
                new BigDecimal("349"), false);

        assertThat(result.getCode()).isEqualTo("SOFA_PREMIUM");
        assertThat(result.getUnitRate()).isEqualByComparingTo("349");
        assertThat(result.isActive()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void activatesService() {
        LaundryServiceItem service = new LaundryServiceItem("IRON_SPECIAL", "Special ironing", "Ironing",
                PricingUnit.PIECE, BigDecimal.TEN);
        service.updateDetails(service.getName(), service.getCategory(), service.getPricingUnit(), service.getUnitRate(), false);
        when(repository.findById(8L)).thenReturn(Optional.of(service));

        catalog.update(8L, service.getName(), service.getCategory(), service.getPricingUnit(), service.getUnitRate(), true);

        assertThat(service.isActive()).isTrue();
    }

    @Test
    void deactivatesService() {
        LaundryServiceItem service = new LaundryServiceItem("IRON_SPECIAL", "Special ironing", "Ironing",
                PricingUnit.PIECE, BigDecimal.TEN);
        when(repository.findById(9L)).thenReturn(Optional.of(service));

        catalog.update(9L, service.getName(), service.getCategory(), service.getPricingUnit(), service.getUnitRate(), false);

        assertThat(service.isActive()).isFalse();
    }

    @Test
    void activeServicesExcludeInactiveCatalogEntries() {
        LaundryServiceItem active = new LaundryServiceItem("ACTIVE", "Active service", "Ironing",
                PricingUnit.PIECE, BigDecimal.TEN);
        when(repository.findByActiveTrueOrderByCategoryAscNameAsc()).thenReturn(List.of(active));

        assertThat(catalog.activeServices()).containsExactly(active);
        verify(repository).findByActiveTrueOrderByCategoryAscNameAsc();
    }
}