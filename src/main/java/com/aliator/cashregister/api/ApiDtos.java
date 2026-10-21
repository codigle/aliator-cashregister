package com.aliator.cashregister.api;

import com.aliator.cashregister.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}
    public record OrderItemRequest(
        @NotNull Long menuItemId,
        @NotNull @Min(1) @Max(15) Integer quantity,
        @Size(max = 180) String note
    ) {}
    public record PlaceOrderRequest(
        @NotBlank @Pattern(regexp = "T0[1-8]") String tableCode,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9-]{36}") String requestId,
        @NotEmpty @Size(max = 20) List<@Valid OrderItemRequest> items
    ) {}
    public record StatusUpdateRequest(@NotNull OrderStatus status) {}
    public record OrderLineView(Long menuItemId, String itemName, int quantity, int unitPriceYen, String note) {}
    public record OrderView(Long id, String code, String tableCode, OrderStatus status,
                            Instant createdAt, Instant updatedAt, List<OrderLineView> lines, long totalYen) {}
    public record DashboardView(long totalOrders, long totalSalesYen, long activeOrders, long completedOrders,
                                long averageOrderYen) {}
}
