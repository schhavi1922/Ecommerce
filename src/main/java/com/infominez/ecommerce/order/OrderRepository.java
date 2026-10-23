package com.infominez.ecommerce.order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByIdempotencyKey(String idempotencyKey);
    long countByStatus(PurchaseOrder.Status status);
}
