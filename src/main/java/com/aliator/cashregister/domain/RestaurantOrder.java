package com.aliator.cashregister.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "restaurant_orders", indexes = {
    @Index(name = "idx_orders_created", columnList = "created_at"),
    @Index(name = "idx_orders_status", columnList = "status")
})
public class RestaurantOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String requestId;
    @Column(nullable = false, length = 8)
    private String tableCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderLine> lines = new ArrayList<>();

    protected RestaurantOrder() {}

    public RestaurantOrder(String requestId, String tableCode, List<OrderLine> lines) {
        this.requestId = requestId;
        this.tableCode = tableCode;
        for (OrderLine line : lines) {
            line.setOrder(this);
            this.lines.add(line);
        }
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getTableCode() { return tableCode; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderLine> getLines() { return lines; }
}
