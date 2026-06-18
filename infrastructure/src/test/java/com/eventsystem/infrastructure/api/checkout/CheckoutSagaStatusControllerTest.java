package com.eventsystem.infrastructure.api.checkout;

import com.eventsystem.application.order.ActiveOrderDTO;
import com.eventsystem.application.order.BuyerRefernceDTO;
import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.order.OrderStatus;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CheckoutSagaController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CheckoutSagaStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CheckoutSaga checkoutSaga;

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
    @DisplayName("GET /api/checkout/{orderId}/status returns status")
    void status_ReturnsOrderStatus() throws Exception {
        ActiveOrderDTO dto = new ActiveOrderDTO(
                "ORDER-1",
                new BuyerRefernceDTO(null, "session-1", "member-1"),
                "EVENT-1",
                List.of(),
                Instant.now(),
                OrderStatus.CHECKED_OUT,
                1L
        );

        when(orderService.getOrderById("ORDER-1")).thenReturn(dto);

        mockMvc.perform(get("/api/checkout/ORDER-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-1"))
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"));
    }

}
