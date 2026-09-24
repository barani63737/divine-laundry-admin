package com.divinelaundry.api;

import com.divinelaundry.domain.LaundryServiceItem;
import com.divinelaundry.domain.PricingUnit;
import com.divinelaundry.repository.LaundryServiceRepository;
import com.divinelaundry.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final LaundryServiceRepository services;
    private final CatalogService catalog;

    public CatalogController(LaundryServiceRepository services, CatalogService catalog) {
        this.services = services;
        this.catalog = catalog;
    }

    @GetMapping("/services")
    List<ServiceResponse> listServices() {
        return catalog.activeServices().stream().map(ServiceResponse::from).toList();
    }

    @GetMapping("/services/all")
    List<ServiceResponse> listAllServices() {
        return catalog.allServices().stream().map(ServiceResponse::from).toList();
    }

    @PostMapping("/services")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
        return ServiceResponse.from(catalog.create(request.name(), request.code(), request.category(),
                request.unit(), request.rate(), request.active()));
    }

    @PutMapping("/services/{id}")
    ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceUpdateRequest request) {
        return ServiceResponse.from(catalog.update(id, request.name(), request.category(), request.unit(),
                request.rate(), request.active()));
    }

    public record ServiceRequest(@NotBlank String name, @NotBlank String code, @NotBlank String category,
            @NotNull PricingUnit unit, @NotNull @DecimalMin("0.00") BigDecimal rate, boolean active) {}

    public record ServiceUpdateRequest(@NotBlank String name, @NotBlank String category,
            @NotNull PricingUnit unit, @NotNull @DecimalMin("0.00") BigDecimal rate, boolean active) {}

    public record ServiceResponse(Long id, String code, String name, String category, String group,
            PricingUnit unit, BigDecimal rate, boolean active) {
        static ServiceResponse from(LaundryServiceItem item) {
            return new ServiceResponse(item.getId(), item.getCode(), item.getName(), item.getCategory(),
                    item.getCatalogGroup(), item.getPricingUnit(), item.getUnitRate(), item.isActive());
        }
    }
}
