package com.eventsystem.infrastructure.api.orders;

import com.eventsystem.application.appexceptions.AlreadyExistsOrderException;
import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.BuyerRefernceDTO;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = OrdersRestController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class OrdersRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

        @MockBean
        private ITokenService tokenService;

        @MockBean
        private AuthenticationInterceptor authenticationInterceptor;

        @SuppressWarnings("null")
        @BeforeEach
        void allowMvcRequests() throws Exception {
            when(authenticationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        }

    @Test
    @DisplayName("GET /api/orders/{orderId} returns order details")
    void getOrder_ReturnsOrderDetails() throws Exception {
        ActiveOrderDTO dto = new ActiveOrderDTO(
                "ORDER-123",
                new BuyerRefernceDTO(BuyerType.MEMBER, "session-1", "member-1"),
                "EVENT-9",
                List.of(),
                Instant.parse("2026-05-28T10:15:30Z"),
                OrderStatus.ACTIVE,
                2L
        );
        when(orderService.getOrderById("ORDER-123")).thenReturn(dto);

        mockMvc.perform(get("/api/orders/ORDER-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-123"))
                .andExpect(jsonPath("$.eventId").value("EVENT-9"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.buyerRef.type").value("MEMBER"))
                .andExpect(jsonPath("$.buyerRef.memberId").value("member-1"));
    }

    @Test
    @DisplayName("GET /api/orders/{orderId} maps missing order to 404")
    void getOrder_MissingOrder_ReturnsNotFound() throws Exception {
        when(orderService.getOrderById("ORDER-404")).thenThrow(new OrderNotFoundException("Active order ORDER-404 not found"));

        mockMvc.perform(get("/api/orders/ORDER-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Active order ORDER-404 not found"));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/orders returns 409 when an active order already exists")
    void createNewOrderStrict_ExistingOrder_ReturnsConflict() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.buyerType = "MEMBER";
        request.sessionId = "session-1";
        request.memberId = "member-1";
        request.eventId = "EVENT-1";

        doThrow(new AlreadyExistsOrderException("An active order for this buyer and event already exists."))
                .when(orderService).createNewOrderStrict(any(), eq("EVENT-1"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/orders/active rejects missing eventId")
    void createOrGetActiveOrder_MissingEventId_ReturnsBadRequest() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.buyerType = "MEMBER";
        request.sessionId = "session-1";
        request.memberId = "member-1";

        mockMvc.perform(post("/api/orders/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("eventId is required"));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/orders/{orderId}/items validates body and returns 400")
    void reserveSeat_MissingBodyFields_ReturnsBadRequest() throws Exception {
        ReserveSeatRequest request = new ReserveSeatRequest();
        request.zoneId = "ZONE-1";

        mockMvc.perform(post("/api/orders/ORDER-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("zoneId and seatId are required"));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("DELETE /api/orders/{orderId}/items delegates release")
    void releaseSeat_ValidRequest_ReturnsAccepted() throws Exception {
        ReleaseSeatRequest request = new ReleaseSeatRequest();
        request.zoneId = "ZONE-1";
        request.seatId = "SEAT-1";

        mockMvc.perform(delete("/api/orders/ORDER-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
