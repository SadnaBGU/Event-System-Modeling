package com.eventsystem.infrastructure.api.checkout;

import com.eventsystem.application.order.CheckoutResult;
import com.eventsystem.application.order.CheckoutSaga;
import com.eventsystem.application.order.OrderService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.api.checkout.CheckoutSagaController.CheckoutRequest;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CheckoutSagaController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CheckoutSagaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CheckoutSaga checkoutSaga;

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

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/checkout completes checkout and returns result")
    void checkout_ValidRequest_ReturnsOkWithCheckoutResult() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.orderId = "ORDER-1";
        req.paymentToken = "tok-1";
        req.discountCode = "DISC-1";

        when(checkoutSaga.executeCheckout("ORDER-1", "tok-1", "DISC-1"))
                .thenReturn(new CheckoutResult(
                        "ORDER-1",
                        "REC-1",
                        "CHECKED_OUT",
                        Money.of(BigDecimal.valueOf(100), "USD"),
                        "TXN-1",
                        List.of("TIX-1")
                ));

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORDER-1"))
                .andExpect(jsonPath("$.purchaseRecordId").value("REC-1"))
                .andExpect(jsonPath("$.orderStatus").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.paymentTransactionId").value("TXN-1"))
                .andExpect(jsonPath("$.issuedTicketCodes[0]").value("TIX-1"));

        verify(checkoutSaga).executeCheckout("ORDER-1", "tok-1", "DISC-1");
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/checkout rejects missing orderId")
    void checkout_MissingOrderId_ReturnsBadRequest() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.paymentToken = "tok-1";

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/checkout rejects missing paymentToken")
    void checkout_MissingPaymentToken_ReturnsBadRequest() throws Exception {
        CheckoutRequest req = new CheckoutRequest();
        req.orderId = "ORDER-1";

        mockMvc.perform(post("/api/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}