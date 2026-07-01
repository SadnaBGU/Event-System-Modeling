package com.eventsystem.infrastructure.api.order;

import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.OrderPricingPreviewDTO;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.order.QueueService;
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
    private final QueueService queueService;

    @Autowired
    public OrdersRestController(OrderService orderService, QueueService queueService) {
        this.orderService = orderService;
        this.queueService = queueService;
    }

    @PostMapping("/active")
    public ResponseEntity<ActiveOrderDTO> createOrGetActiveOrder(@RequestBody CreateOrderRequest req) {
        // basic validation
        if (req == null || req.eventId == null || req.eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        BuyerType type = "GUEST".equalsIgnoreCase(req.buyerType) ? BuyerType.GUEST : BuyerType.MEMBER;
        BuyerReference buyer = new BuyerReference(type, req.sessionId, req.memberId);
        queueService.requireAdmissionOrEnqueueOnHighLoad(req.eventId, buyer);
        Optional<String> lottery = Optional.ofNullable(req.lotteryCode);
        ActiveOrderDTO dto = orderService.createOrGetActiveOrder(buyer, req.eventId, lottery);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("")
    public ResponseEntity<ActiveOrderDTO> createNewOrderStrict(@RequestBody CreateOrderRequest req) {
        if (req == null || req.eventId == null || req.eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
        BuyerType type = "GUEST".equalsIgnoreCase(req.buyerType) ? BuyerType.GUEST : BuyerType.MEMBER;
        BuyerReference buyer = new BuyerReference(type, req.sessionId, req.memberId);
        queueService.requireAdmissionOrEnqueueOnHighLoad(req.eventId, buyer);
        ActiveOrderDTO dto = orderService.createNewOrderStrict(buyer, req.eventId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<Void> addItemToOrder(@PathVariable String orderId, @RequestBody AddItemRequest req) {
        if (req == null || req.zoneId == null || req.zoneId.isBlank())
            throw new IllegalArgumentException("zoneId is required");
        int quantity = (req.quantity != null && req.quantity > 0) ? req.quantity : 1;
        orderService.addItemToOrder(orderId, req.zoneId, req.seatId, quantity);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{orderId}/items")
    public ResponseEntity<Void> removeItemFromOrder(@PathVariable String orderId, @RequestBody RemoveItemRequest req) {
        if (req == null || req.zoneId == null || req.zoneId.isBlank())
            throw new IllegalArgumentException("zoneId is required");
        int quantity = (req.quantity != null && req.quantity > 0) ? req.quantity : 1;
        orderService.removeItemFromOrder(orderId, req.zoneId, req.seatId, quantity);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ActiveOrderDTO> getOrder(@PathVariable String orderId) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        ActiveOrderDTO dto = orderService.getOrderById(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{orderId}/discount")
    public ResponseEntity<OrderPricingPreviewDTO> previewDiscount(@PathVariable String orderId,
                                                                  @RequestBody ApplyDiscountRequest req) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        if (req == null || req.discountCode == null || req.discountCode.isBlank()) {
            throw new IllegalArgumentException("discountCode is required");
        }

        OrderPricingPreviewDTO dto = orderService.previewDiscount(orderId, req.discountCode);
        return ResponseEntity.ok(dto);
    }

}
