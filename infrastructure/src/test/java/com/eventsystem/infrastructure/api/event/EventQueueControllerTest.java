package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.order.QueueService;
import com.eventsystem.application.order.QueueService.AdmissionStatus;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.order.BuyerReference;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = EventQueueController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EventQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueService queueService;

    @MockBean
    private ITokenService tokenService;

    @MockBean
    private AuthenticationInterceptor authenticationInterceptor;

    private final String EVENT_ID = "EVT-123";
    private final MemberId MEMBER_ID = new MemberId("MEM-456");
    private final String SESSION_ID = "sess-789";

    @SuppressWarnings("null")
    @BeforeEach
    void allowMvcRequests() throws Exception {
        when(authenticationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("POST /entries - Manual member entry is disabled (automatic under high load)")
    void enqueueVisitor_AsMember_isDisabled() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/queue/entries", EVENT_ID)
                        .requestAttr("authenticatedMemberId", MEMBER_ID)
                        .param("sessionId", SESSION_ID))
                .andExpect(status().isBadRequest());

        verify(queueService, never()).enqueueVisitor(any(), any());
    }

    @Test
    @DisplayName("POST /entries - Manual guest entry is disabled (automatic under high load)")
    void enqueueVisitor_AsGuest_isDisabled() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/queue/entries", EVENT_ID)
                        .param("sessionId", SESSION_ID))
                .andExpect(status().isBadRequest());

        verify(queueService, never()).enqueueVisitor(any(), any());
    }

    @Test
    @DisplayName("POST /entries - Missing Both Branches Throws Exception")
    void enqueueVisitor_MissingBoth_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/queue/entries", EVENT_ID))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("GET /status - Authenticated Member Branch")
    void admissionStatus_AsMember_ReturnsOk() throws Exception {
        AdmissionStatus mockStatus = new AdmissionStatus(true, 0);
        when(queueService.getAdmissionStatus(eq(EVENT_ID), any(BuyerReference.class))).thenReturn(mockStatus);

        mockMvc.perform(get("/api/events/{eventId}/queue/status", EVENT_ID)
                        .requestAttr("authenticatedMemberId", MEMBER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmitted").value(true))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    @DisplayName("GET /status - Guest with SessionId Branch")
    void admissionStatus_AsGuest_ReturnsOk() throws Exception {
        AdmissionStatus mockStatus = new AdmissionStatus(false, 42);
        when(queueService.getAdmissionStatus(eq(EVENT_ID), any(BuyerReference.class))).thenReturn(mockStatus);

        mockMvc.perform(get("/api/events/{eventId}/queue/status", EVENT_ID)
                        .param("sessionId", SESSION_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmitted").value(false))
                .andExpect(jsonPath("$.position").value(42));
    }

    @Test
    @DisplayName("GET /status - Missing Both Branches Throws Exception")
    void admissionStatus_MissingBoth_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}/queue/status", EVENT_ID))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("DELETE /admissions - Authenticated Member Branch")
    void revokeAdmission_AsMember_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/events/{eventId}/queue/admissions", EVENT_ID)
                        .requestAttr("authenticatedMemberId", MEMBER_ID))
                .andExpect(status().isNoContent());

        verify(queueService).revokeAdmission(eq(EVENT_ID), any(BuyerReference.class));
    }

    @Test
    @DisplayName("DELETE /admissions - Guest with SessionId Branch")
    void revokeAdmission_AsGuest_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/events/{eventId}/queue/admissions", EVENT_ID)
                        .param("sessionId", SESSION_ID))
                .andExpect(status().isNoContent());

        verify(queueService).revokeAdmission(eq(EVENT_ID), any(BuyerReference.class));
    }

    @Test
    @DisplayName("DELETE /admissions - Missing Both Branches Throws Exception")
    void revokeAdmission_MissingBoth_ReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/events/{eventId}/queue/admissions", EVENT_ID))
                .andExpect(status().isBadRequest());
    }
}