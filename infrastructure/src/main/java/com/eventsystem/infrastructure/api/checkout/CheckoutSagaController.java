package com.eventsystem.infrastructure.api.checkout;

import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutSagaController {

    private final CheckoutSaga checkoutSaga;
    private final OrderService orderService;

    @Autowired
    public CheckoutSagaController(CheckoutSaga checkoutSaga, OrderService orderService) {
        this.checkoutSaga = checkoutSaga;
        this.orderService = orderService;
    }

    @PostMapping("")
    public ResponseEntity<Void> checkout(@RequestBody CheckoutRequest req) {
        if (req == null || req.orderId == null || req.orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        checkoutSaga.executeCheckout(req.orderId, req.paymentToken, req.discountCode);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<StatusResponse> status(@PathVariable String orderId) {
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
        try {
            ActiveOrderDTO dto = orderService.getOrderById(orderId);
            return ResponseEntity.ok(new StatusResponse(dto.orderId(), dto.status().name()));
        } catch (OrderNotFoundException ex) {
            throw ex;
        }
    }

    public static class CheckoutRequest {
        public String orderId;
        public String paymentToken;
        public String discountCode;
    }

    public static class StatusResponse {
        public String orderId;
        public String status;

        public StatusResponse(String orderId, String status) {
            this.orderId = orderId;
            this.status = status;
        }
    }
}
