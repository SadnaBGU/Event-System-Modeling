package com.eventsystem.infrastructure.api.checkout;

import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.infrastructure.api.checkout.CheckoutSagaController.CallbackRequest;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CheckoutSagaController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CheckoutSagaCallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ITokenService tokenService;

    @MockBean
    private AuthenticationInterceptor authenticationInterceptor;

    @MockBean
    private CheckoutSaga checkoutSaga;

    @MockBean
    private OrderService orderService;

    @SuppressWarnings("null")
    @BeforeEach
    void allowMvcRequests() throws Exception {
        when(authenticationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/checkout/{orderId}/callbacks accepts callback")
    void callback_AcceptsCallback() throws Exception {
        CallbackRequest req = new CallbackRequest();
        req.type = "payment";
        req.payload = "{\"status\":\"success\"}";

        mockMvc.perform(post("/api/checkout/ORDER-1/callbacks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
    }
}
