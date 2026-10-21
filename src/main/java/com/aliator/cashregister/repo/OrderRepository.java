package com.aliator.cashregister.repo;

import com.aliator.cashregister.domain.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<RestaurantOrder, Long> {
    Optional<RestaurantOrder> findByRequestId(String requestId);
    List<RestaurantOrder> findTop150ByOrderByCreatedAtDesc();
}
