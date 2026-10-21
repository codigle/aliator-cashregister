package com.aliator.cashregister.api;

import com.aliator.cashregister.api.ApiDtos.*;
import com.aliator.cashregister.domain.MenuItem;
import com.aliator.cashregister.repo.MenuRepository;
import com.aliator.cashregister.service.OrderService;
import com.aliator.cashregister.service.OrderThrottle;
import com.aliator.cashregister.service.StaffAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RestaurantController {
    private final MenuRepository menu;
    private final OrderService orders;
    private final OrderThrottle throttle;
    private final StaffAccess staff;

    public RestaurantController(MenuRepository menu, OrderService orders, OrderThrottle throttle, StaffAccess staff) {
        this.menu = menu;
        this.orders = orders;
        this.throttle = throttle;
        this.staff = staff;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "aliator-cashregister", "mode", "DEMO");
    }

    @GetMapping("/menu")
    public List<MenuItem> menu() { return menu.findByActiveTrueOrderBySortOrderAscIdAsc(); }

    @GetMapping("/tables")
    public List<String> tables() { return List.of("T01", "T02", "T03", "T04", "T05", "T06", "T07", "T08"); }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView place(@Valid @RequestBody PlaceOrderRequest request, HttpServletRequest httpRequest) {
        // Only honour X-Real-IP from the trusted reverse proxy (app is localhost-only in compose).
        String realIp = httpRequest.getHeader("X-Real-IP");
        String key = (realIp != null && realIp.matches("[0-9a-fA-F:.]{3,50}")) ? realIp : httpRequest.getRemoteAddr();
        throttle.check(key);
        return orders.place(request);
    }

    @GetMapping("/orders")
    public List<OrderView> recent(@RequestParam(defaultValue = "ACTIVE") String status, @RequestHeader(value = "X-Staff-Token", required = false) String token) {
        staff.require(token);
        return orders.recent(status);
    }

    @GetMapping("/orders/{id}")
    public OrderView get(@PathVariable Long id, @RequestHeader(value = "X-Staff-Token", required = false) String token) {
        staff.require(token);
        return orders.get(id);
    }

    @GetMapping("/track/{requestId}")
    public OrderView track(@PathVariable String requestId) { return orders.track(requestId); }

    @PatchMapping("/orders/{id}/status")
    public OrderView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req,
                                  @RequestHeader(value = "X-Staff-Token", required = false) String token) {
        staff.require(token);
        return orders.changeStatus(id, req.status());
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        staff.require(token);
        return orders.dashboard();
    }
}
