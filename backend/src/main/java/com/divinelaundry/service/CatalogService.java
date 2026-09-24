package com.divinelaundry.service;

import com.divinelaundry.domain.LaundryServiceItem;
import com.divinelaundry.domain.PricingUnit;
import com.divinelaundry.repository.LaundryServiceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CatalogService {
    public static final Set<String> CATEGORIES = Set.of(
            "Dry Clean", "Laundry by KG", "Ironing", "Shoe Cleaning", "Sofa Cleaning");

    private final LaundryServiceRepository services;

    public CatalogService(LaundryServiceRepository services) {
        this.services = services;
    }

    @Transactional(readOnly = true)
    public List<LaundryServiceItem> activeServices() {
        return services.findByActiveTrueOrderByCategoryAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<LaundryServiceItem> allServices() {
        return services.findAllByOrderByCategoryAscNameAsc();
    }

    @Transactional
    public LaundryServiceItem create(String name, String code, String category,
            PricingUnit pricingUnit, BigDecimal unitRate, boolean active) {
        String normalizedCode = normalizeCode(code);
        validate(name, normalizedCode, category, pricingUnit, unitRate);
        if (services.existsByCodeIgnoreCase(normalizedCode)) {
            throw new IllegalArgumentException("Service code already exists");
        }
        try {
            LaundryServiceItem item = new LaundryServiceItem(normalizedCode, name.trim(), category,
                    pricingUnit, unitRate);
            item.updateDetails(name.trim(), category, pricingUnit, unitRate, active);
            return services.save(item);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Service code already exists", ex);
        }
    }

    @Transactional
    public LaundryServiceItem update(Long id, String name, String category,
            PricingUnit pricingUnit, BigDecimal unitRate, boolean active) {
        LaundryServiceItem item = services.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));
        validate(name, item.getCode(), category, pricingUnit, unitRate);
        item.updateDetails(name.trim(), category, pricingUnit, unitRate, active);
        return item;
    }

    private void validate(String name, String code, String category,
            PricingUnit pricingUnit, BigDecimal unitRate) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Service code is required");
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{1,39}")) {
            throw new IllegalArgumentException("Service code must use 2-40 uppercase letters, numbers, _ or -");
        }
        if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("Select a valid service category");
        if (pricingUnit == null) throw new IllegalArgumentException("Pricing unit is required");
        if (unitRate == null || unitRate.signum() < 0) throw new IllegalArgumentException("Price must be zero or greater");
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}