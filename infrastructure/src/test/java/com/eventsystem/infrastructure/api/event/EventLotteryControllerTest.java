package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;
import com.eventsystem.application.security.ITokenService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = EventLotteryController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EventLotteryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LotteryService lotteryService;

    @MockBean
    private ILotteryRepository lotteryRepository;

    @MockBean
    private IEventRepository eventRepository;

    @MockBean
    private ICompanyPermissionServicePort permissionService;

    @MockBean
    private ITokenService tokenService;

    @MockBean
    private AuthenticationInterceptor authenticationInterceptor;

    @SuppressWarnings("null")
    @BeforeEach
    void allowMvcRequests() throws Exception {
        org.mockito.Mockito.when(authenticationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/events/{eventId}/lottery/registrations returns 201")
    void registerForLottery_ReturnsCreated() throws Exception {
        mockMvc.perform(post("/api/events/E-1/lottery/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("authenticatedMemberId", new MemberId("M-1")))
                .andExpect(status().isCreated());

        verify(lotteryService).register(eq(new MemberId("M-1")), any(EventId.class));
    }
}
