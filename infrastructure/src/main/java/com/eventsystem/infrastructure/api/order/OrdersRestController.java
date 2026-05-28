package com.eventsystem.infrastructure.api.order;

import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrdersRestController {

    private final OrderService orderService;

    @Autowired
    public OrdersRestController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/active")
    public ResponseEntity<ActiveOrderDTO> createOrGetActiveOrder(@RequestBody CreateOrderRequest req) {
        // basic validation
        if (req == null || req.eventId == null || req.eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        BuyerType type = "GUEST".equalsIgnoreCase(req.buyerType) ? BuyerType.GUEST : BuyerType.MEMBER;
        BuyerReference buyer = new BuyerReference(type, req.sessionId, req.memberId);
        Optional<String> lottery = Optional.ofNullable(req.lotteryCode);
        ActiveOrderDTO dto = orderService.createOrGetActiveOrder(buyer, req.eventId, lottery);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("")
    public ResponseEntity<ActiveOrderDTO> createNewOrderStrict(@RequestBody CreateOrderRequest req) {
        if (req == null || req.eventId == null || req.eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        BuyerType type = "GUEST".equalsIgnoreCase(req.buyerType) ? BuyerType.GUEST : BuyerType.MEMBER;
        BuyerReference buyer = new BuyerReference(type, req.sessionId, req.memberId);
        ActiveOrderDTO dto = orderService.createNewOrderStrict(buyer, req.eventId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<Void> reserveSeat(@PathVariable String orderId, @RequestBody ReserveSeatRequest req) {
        if (req == null || req.zoneId == null || req.zoneId.isBlank() || req.seatId == null || req.seatId.isBlank())
            throw new IllegalArgumentException("zoneId and seatId are required");
        orderService.reserveSeat(orderId, req.zoneId, req.seatId);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{orderId}/items")
    public ResponseEntity<Void> releaseSeat(@PathVariable String orderId, @RequestBody ReleaseSeatRequest req) {
        if (req == null || req.zoneId == null || req.zoneId.isBlank() || req.seatId == null || req.seatId.isBlank())
            throw new IllegalArgumentException("zoneId and seatId are required");
        orderService.releaseSeat(orderId, req.zoneId, req.seatId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ActiveOrderDTO> getOrder(@PathVariable String orderId) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        ActiveOrderDTO dto = orderService.getOrderById(orderId);
        return ResponseEntity.ok(dto);
    }

}
