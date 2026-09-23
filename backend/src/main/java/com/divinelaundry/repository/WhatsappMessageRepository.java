package com.divinelaundry.repository;

import com.divinelaundry.domain.WhatsappMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface WhatsappMessageRepository extends JpaRepository<WhatsappMessage, Long> {
    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<WhatsappMessage> findByDeduplicationKey(String deduplicationKey);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<WhatsappMessage> findById(Long id);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    List<WhatsappMessage> findByOrder_OrderNumberOrderByCreatedAtDesc(String orderNumber);

    @EntityGraph(attributePaths = {"order", "order.customer"})
    Optional<WhatsappMessage> findByIdAndOrder_OrderNumber(Long id, String orderNumber);

    long countByDeliveryStatus(com.divinelaundry.domain.WhatsappDeliveryStatus deliveryStatus);

        @Modifying
        @Query(value = """
            UPDATE whatsapp_messages
               SET delivery_status = 'PENDING',
               claimed_at = :claimedAt,
               attempt_count = attempt_count + 1,
               last_error = NULL
             WHERE deduplication_key = :deduplicationKey
               AND delivery_status NOT IN ('SENT', 'DELIVERED')
               AND (delivery_status <> 'PENDING'
                OR claimed_at IS NULL
                OR claimed_at <= :staleBefore)
            """, nativeQuery = true)
        int claimForDelivery(
            @Param("deduplicationKey") String deduplicationKey,
            @Param("claimedAt") Instant claimedAt,
            @Param("staleBefore") Instant staleBefore);
}
