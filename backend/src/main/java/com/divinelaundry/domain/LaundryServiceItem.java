package com.divinelaundry.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "laundry_services")
public class LaundryServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "catalog_group", length = 80)
    private String catalogGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_unit", nullable = false, length = 20)
    private PricingUnit pricingUnit;

    @Column(name = "unit_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitRate;

    @Column(nullable = false)
    private boolean active = true;

    protected LaundryServiceItem() {}

    public LaundryServiceItem(String code, String name, String category, PricingUnit pricingUnit, BigDecimal unitRate) {
        this(code, name, category, null, pricingUnit, unitRate);
    }

    public LaundryServiceItem(String code, String name, String category, String catalogGroup, PricingUnit pricingUnit, BigDecimal unitRate) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.catalogGroup = catalogGroup;
        this.pricingUnit = pricingUnit;
        this.unitRate = unitRate;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getCatalogGroup() { return catalogGroup; }
    public PricingUnit getPricingUnit() { return pricingUnit; }
    public BigDecimal getUnitRate() { return unitRate; }
    public boolean isActive() { return active; }

    public void updateDetails(String name, String category, PricingUnit pricingUnit, BigDecimal unitRate, boolean active) {
        this.name = name;
        this.category = category;
        this.pricingUnit = pricingUnit;
        this.unitRate = unitRate;
        this.active = active;
    }
}
