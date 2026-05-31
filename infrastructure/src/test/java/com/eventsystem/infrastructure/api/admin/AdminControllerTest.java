package com.eventsystem.infrastructure.api.admin;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.SuspensionDto;
import com.eventsystem.application.appexceptions.NotAuthorizedException;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    private MockMvc mockMvc;

    private final MemberId adminId = new MemberId("admin-1");
    private final MemberId targetId = new MemberId("member-1");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminController(adminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /api/admin/members/{id}/suspend (II.6.7) ────────────────────────

    @SuppressWarnings("null")
    @Test
    void suspendMember_temporaryDuration_returns200() throws Exception {
        mockMvc.perform(post("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationDays\": 7, \"reason\": \"Violation of rules\"}"))
                .andExpect(status().isOk());

        verify(adminService).suspendMember(adminId, targetId, Duration.ofDays(7), "Violation of rules");
    }

    @SuppressWarnings("null")
    @Test
    void suspendMember_noBody_permanent_returns200() throws Exception {
        mockMvc.perform(post("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isOk());

        verify(adminService).suspendMember(eq(adminId), eq(targetId), isNull(), isNull());
    }

    @SuppressWarnings("null")
    @Test
    void suspendMember_zeroDays_treatAsPermanent() throws Exception {
        mockMvc.perform(post("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationDays\": 0}"))
                .andExpect(status().isOk());

        verify(adminService).suspendMember(eq(adminId), eq(targetId), isNull(), isNull());
    }

    @SuppressWarnings("null")
    @Test
    void suspendMember_notAdmin_returns403() throws Exception {
        doThrow(new NotAuthorizedException("not-admin"))
                .when(adminService).suspendMember(any(), any(), any(), any());

        mockMvc.perform(post("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationDays\": 1}"))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("null")
    @Test
    void suspendMember_memberNotFound_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Member not found"))
                .when(adminService).suspendMember(any(), any(), any(), any());

        mockMvc.perform(post("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationDays\": 1}"))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/admin/members/{id}/suspensions (II.6.8) ─────────────────────

    @SuppressWarnings("null")
    @Test
    void unsuspendMember_returns200() throws Exception {
        mockMvc.perform(delete("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isOk());

        verify(adminService).unsuspendMember(adminId, targetId);
    }

    @SuppressWarnings("null")
    @Test
    void unsuspendMember_notSuspended_returns400() throws Exception {
        doThrow(new IllegalStateException("Member is not suspended"))
                .when(adminService).unsuspendMember(any(), any());

        mockMvc.perform(delete("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("null")
    @Test
    void unsuspendMember_notAdmin_returns403() throws Exception {
        doThrow(new NotAuthorizedException("not-admin"))
                .when(adminService).unsuspendMember(any(), any());

        mockMvc.perform(delete("/api/admin/members/{id}/suspensions", targetId.value())
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/suspensions (II.6.9) ──────────────────────────────────

    @SuppressWarnings("null")
    @Test
    void listSuspensions_returnsSuspensionList() throws Exception {
        Instant now = Instant.parse("2026-05-31T10:00:00Z");
        SuspensionDto dto = new SuspensionDto(
                targetId.value(), "alice", now, "PT168H", now.plus(Duration.ofDays(7)));

        when(adminService.listSuspensions(adminId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/suspensions")
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(targetId.value()))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].duration").value("PT168H"));
    }

    @SuppressWarnings("null")
    @Test
    void listSuspensions_empty_returnsEmptyArray() throws Exception {
        when(adminService.listSuspensions(adminId)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/suspensions")
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @SuppressWarnings("null")
    @Test
    void listSuspensions_notAdmin_returns403() throws Exception {
        doThrow(new NotAuthorizedException("not-admin"))
                .when(adminService).listSuspensions(any());

        mockMvc.perform(get("/api/admin/suspensions")
                        .requestAttr("authenticatedMemberId", adminId))
                .andExpect(status().isForbidden());
    }
}
