package com.eventsystem.infrastructure.api.checkout;

import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.CheckoutResult;
import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Checkout", description = "Checkout saga orchestration endpoints")
public class CheckoutSagaController {

    private final CheckoutSaga checkoutSaga;
    private final OrderService orderService;

    @Autowired
    public CheckoutSagaController(CheckoutSaga checkoutSaga, OrderService orderService) {
        this.checkoutSaga = checkoutSaga;
        this.orderService = orderService;
    }

    @PostMapping("")
    @Operation(
            summary = "Complete checkout",
            description = "Synchronously completes checkout for an active order: validates policies, charges payment, issues ticket codes, finalizes the receipt, and checks out the order."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Checkout completed successfully",
                    content = @Content(schema = @Schema(implementation = CheckoutResult.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request / payment failed / price calculation failed", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Order expired, violates policy, or ticket issuance failed after compensation", content = @Content)
    })
    public ResponseEntity<CheckoutResult> checkout(@RequestBody CheckoutRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }

        requireNonBlank(req.orderId, "orderId is required");
        requireNonBlank(req.paymentToken, "paymentToken is required");

        CheckoutResult result = checkoutSaga.executeCheckout(
                req.orderId,
                req.paymentToken,
                req.discountCode
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{orderId}/status")
    @Operation(summary = "Get checkout status", description = "Returns current status for the active order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order status returned"),
            @ApiResponse(responseCode = "400", description = "Invalid order id", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
    })
    public ResponseEntity<StatusResponse> status(@PathVariable String orderId) {
        requireNonBlank(orderId, "orderId is required");

        ActiveOrderDTO dto = orderService.getOrderById(orderId);

        return ResponseEntity.ok(new StatusResponse(dto.orderId(), dto.status().name()));
    }

    @PostMapping("/{orderId}/callbacks")
    @Operation(
            summary = "Accept checkout callback",
            description = "Accepts asynchronous callback events for an order. Currently acknowledged only; checkout is handled synchronously by the Saga endpoint."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "202",
                    description = "Callback accepted",
                    content = @Content(schema = @Schema(implementation = CallbackAckResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid callback request", content = @Content)
    })
    public ResponseEntity<CallbackAckResponse> callback(
            @PathVariable String orderId,
            @RequestBody CallbackRequest req
    ) {
        requireNonBlank(orderId, "orderId is required");

        if (req == null) {
            throw new IllegalArgumentException("request body is required");
        }

        requireNonBlank(req.type, "callback type is required");
        requireNonBlank(req.payload, "callback payload is required");

        // Checkout is currently synchronous. We still keep this endpoint for future
        // provider callbacks / asynchronous orchestration support.
        return ResponseEntity.accepted().body(new CallbackAckResponse(orderId, "ACCEPTED"));
    }

    public static class CheckoutRequest {
        @Schema(description = "Active order id", example = "ORDER-123", requiredMode = Schema.RequiredMode.REQUIRED)
        public String orderId;

        @Schema(description = "Payment token provided by payment provider", example = "tok_abc123", requiredMode = Schema.RequiredMode.REQUIRED)
        public String paymentToken;

        @Schema(description = "Optional discount code", example = "DISC10")
        public String discountCode;
    }

    public static class StatusResponse {
        @Schema(description = "Active order id", example = "ORDER-123")
        public String orderId;

        @Schema(description = "Order lifecycle status", example = "CHECKED_OUT")
        public String status;

        public StatusResponse(String orderId, String status) {
            this.orderId = orderId;
            this.status = status;
        }
    }

    public static class CallbackRequest {
        @Schema(description = "Callback type", example = "PAYMENT_SETTLED", requiredMode = Schema.RequiredMode.REQUIRED)
        public String type;

        @Schema(description = "Raw callback payload", example = "{\"provider\":\"X\",\"status\":\"OK\"}", requiredMode = Schema.RequiredMode.REQUIRED)
        public String payload;
    }

    public static class CallbackAckResponse {
        @Schema(description = "Active order id", example = "ORDER-123")
        public String orderId;

        @Schema(description = "Callback handling status", example = "ACCEPTED")
        public String status;

        public CallbackAckResponse(String orderId, String status) {
            this.orderId = orderId;
            this.status = status;
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}