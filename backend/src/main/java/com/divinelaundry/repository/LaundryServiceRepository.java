package com.divinelaundry.repository;

import com.divinelaundry.domain.LaundryServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LaundryServiceRepository extends JpaRepository<LaundryServiceItem, Long> {
    List<LaundryServiceItem> findByActiveTrueOrderByCategoryAscNameAsc();
    List<LaundryServiceItem> findAllByOrderByCategoryAscNameAsc();
    boolean existsByCodeIgnoreCase(String code);
}

