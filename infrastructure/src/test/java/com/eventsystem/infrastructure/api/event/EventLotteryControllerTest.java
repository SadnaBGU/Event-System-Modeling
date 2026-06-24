package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.lottery.LotteryStatus;
import com.eventsystem.domain.lottery.LotteryWinner;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private IMemberRepository memberRepository;

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

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/events/{eventId}/lottery/draw draws winners and returns 200")
    void drawLottery_ReturnsOk() throws Exception {
        Event event = mock(Event.class);
        when(event.companyId()).thenReturn(new CompanyId("C-1"));
        when(eventRepository.findById(any())).thenReturn(java.util.Optional.of(event));
        when(permissionService.canManageEvents(any(), any())).thenReturn(true);

        Lottery lottery = mock(Lottery.class);
        LotteryId lotteryId = LotteryId.generate();
        when(lottery.getLotteryId()).thenReturn(lotteryId);
        when(lottery.getStatus()).thenReturn(LotteryStatus.DRAWN);
        when(lottery.getWinners()).thenReturn(java.util.Set.of());
        when(lotteryRepository.findByEventId(any())).thenReturn(java.util.Optional.of(lottery));

        mockMvc.perform(post("/api/events/E-1/lottery/draw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerCount\":2}")
                        .requestAttr("authenticatedMemberId", new MemberId("M-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAWN"));

        verify(lotteryService).draw(eq(lotteryId), eq(2));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /api/events/{eventId}/lottery/draw without permission returns 403")
    void drawLottery_NoPermission_ReturnsForbidden() throws Exception {
        Event event = mock(Event.class);
        when(event.companyId()).thenReturn(new CompanyId("C-1"));
        when(eventRepository.findById(any())).thenReturn(java.util.Optional.of(event));
        when(permissionService.canManageEvents(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/events/E-1/lottery/draw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerCount\":2}")
                        .requestAttr("authenticatedMemberId", new MemberId("M-1")))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(lotteryService);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("GET /api/events/{eventId}/lottery/winners returns winners with usernames")
    void getWinners_ReturnsOk() throws Exception {
        Event event = mock(Event.class);
        when(event.companyId()).thenReturn(new CompanyId("C-1"));
        when(eventRepository.findById(any())).thenReturn(java.util.Optional.of(event));
        when(permissionService.canManageEvents(any(), any())).thenReturn(true);

        MemberId winnerId = new MemberId("M-9");
        LotteryWinner winner = new LotteryWinner(winnerId, "CODE123", java.time.Instant.parse("2026-12-31T00:00:00Z"));
        Lottery lottery = mock(Lottery.class);
        when(lottery.getWinners()).thenReturn(java.util.Set.of(winner));
        when(lotteryRepository.findByEventId(any())).thenReturn(java.util.Optional.of(lottery));

        Member member = mock(Member.class);
        when(member.getUsername()).thenReturn("alice");
        when(memberRepository.findById(winnerId)).thenReturn(java.util.Optional.of(member));

        mockMvc.perform(get("/api/events/E-1/lottery/winners")
                        .requestAttr("authenticatedMemberId", new MemberId("M-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].memberId").value("M-9"));
    }
}
