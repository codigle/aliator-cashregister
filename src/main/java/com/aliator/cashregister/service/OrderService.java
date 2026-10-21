package com.aliator.cashregister.service;

import com.aliator.cashregister.api.ApiDtos.*;
import com.aliator.cashregister.domain.*;
import com.aliator.cashregister.repo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final MenuRepository menu;

    public OrderService(OrderRepository orders, MenuRepository menu) {
        this.orders = orders;
        this.menu = menu;
    }

    @Transactional
    public synchronized OrderView place(PlaceOrderRequest request) {
        // In addition to the DB unique constraint, serialize demo requests in this single JVM.
        // A multi-instance production deployment requires DB/upsert idempotency handling.
        var existing = orders.findByRequestId(request.requestId());
        if (existing.isPresent()) return view(existing.get());

        Set<Long> ids = request.items().stream().map(OrderItemRequest::menuItemId).collect(Collectors.toSet());
        Map<Long, MenuItem> products = menu.findAllById(ids).stream()
            .collect(Collectors.toMap(MenuItem::getId, item -> item));
        if (products.size() != ids.size()) throw badRequest("One or more items are unavailable.");

        List<OrderLine> lines = new ArrayList<>();
        long total = 0;
        for (OrderItemRequest line : request.items()) {
            MenuItem item = products.get(line.menuItemId());
            if (item == null || !item.isActive()) throw badRequest("This menu item is not available.");
            total += (long) item.getPriceYen() * line.quantity();
            lines.add(new OrderLine(item, line.quantity(), line.note()));
        }
        if (total > 100_000) throw badRequest("Order amount exceeds the demo limit.");
        RestaurantOrder saved = orders.saveAndFlush(new RestaurantOrder(request.requestId(), request.tableCode(), lines));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderView> recent(String status) {
        List<RestaurantOrder> found = orders.findTop150ByOrderByCreatedAtDesc();
        return found.stream().filter(order -> {
            if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) return true;
            if (status.equalsIgnoreCase("ACTIVE")) return switch (order.getStatus()) {
                case NEW, PREPARING, READY -> true;
                default -> false;
            };
            return order.getStatus().name().equalsIgnoreCase(status);
        }).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public OrderView track(String requestId) {
        if (!requestId.matches("[a-fA-F0-9-]{36}")) throw notFound("Order not found.");
        return view(orders.findByRequestId(requestId).orElseThrow(() -> notFound("Order not found.")));
    }

    @Transactional(readOnly = true)
    public OrderView get(Long id) {
        return view(orders.findById(id).orElseThrow(() -> notFound("Order not found.")));
    }

    @Transactional
    public OrderView changeStatus(Long id, OrderStatus target) {
        RestaurantOrder order = orders.findById(id).orElseThrow(() -> notFound("Order not found."));
        OrderStatus current = order.getStatus();
        if (current == target) return view(order);
        boolean allowed = switch (current) {
            case NEW -> target == OrderStatus.PREPARING || target == OrderStatus.CANCELLED;
            case PREPARING -> target == OrderStatus.READY || target == OrderStatus.CANCELLED;
            case READY -> target == OrderStatus.SERVED;
            case SERVED, CANCELLED -> false;
        };
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Invalid transition from " + current + " to " + target);
        order.updateStatus(target);
        return view(order);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        List<RestaurantOrder> all = orders.findAll(); // Demo data set only.
        long completed = all.stream().filter(o -> o.getStatus() == OrderStatus.SERVED).count();
        long active = all.stream().filter(o -> switch (o.getStatus()) {
            case NEW, PREPARING, READY -> true;
            default -> false;
        }).count();
        long sales = all.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED)
            .mapToLong(o -> total(o.getLines())).sum();
        long billableCount = all.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).count();
        return new DashboardView(all.size(), sales, active, completed,
                billableCount == 0 ? 0 : sales / billableCount);
    }

    private OrderView view(RestaurantOrder order) {
        List<OrderLineView> lines = order.getLines().stream()
            .map(l -> new OrderLineView(l.getMenuItemId(), l.getItemName(), l.getQuantity(), l.getUnitPriceYen(), l.getNote()))
            .toList();
        return new OrderView(order.getId(), "CR-" + String.format("%04d", order.getId()), order.getTableCode(),
            order.getStatus(), order.getCreatedAt(), order.getUpdatedAt(), lines, total(order.getLines()));
    }

    private static long total(List<OrderLine> lines) {
        return lines.stream().mapToLong(l -> (long) l.getUnitPriceYen() * l.getQuantity()).sum();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
