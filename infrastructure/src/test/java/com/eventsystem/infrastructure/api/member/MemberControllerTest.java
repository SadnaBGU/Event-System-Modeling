package com.eventsystem.infrastructure.api.member;

import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.application.member.MemberDto;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.member.UpdateMemberDetailsRequest;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    @Mock
    private MemberService memberService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MemberController(memberService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("Get details - Returns 200 OK and member JSON")
    void getDetails_ReturnsOk() throws Exception {
        MemberId memberId = new MemberId("member-123");
        MemberDto dto = new MemberDto(
                memberId,
                "david",
                "David",
                "Cohen",
                "david@test.com",
                LocalDate.of(1990, 1, 1),
                MemberStatus.ACTIVE);
        when(memberService.getDetails(nullable(MemberId.class), any(MemberId.class))).thenReturn(dto);

        mockMvc.perform(get("/api/members/{targetId}", memberId.value())
                        .requestAttr("authenticatedMemberId", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId.value").value("member-123"))
                .andExpect(jsonPath("$.username").value("david"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Get details - Other member - Returns 403 Forbidden")
    void getDetails_OtherMember_ReturnsForbidden() throws Exception {
        MemberId target = new MemberId("member-456");
        when(memberService.getDetails(nullable(MemberId.class), any(MemberId.class)))
                .thenThrow(new SecurityException("Actor member-123 is not authorized to act on member member-456"));

        mockMvc.perform(get("/api/members/{targetId}", target.value())
                        .requestAttr("authenticatedMemberId", new MemberId("member-123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Update details - Returns 200 OK with updated member JSON")
    void updateDetails_ReturnsOk() throws Exception {
        MemberId memberId = new MemberId("member-123");
        UpdateMemberDetailsRequest request = new UpdateMemberDetailsRequest(
                "Dave", "Cohen", "dave@test.com", LocalDate.of(1991, 2, 2));
        MemberDto dto = new MemberDto(
                memberId,
                "david",
                "Dave",
                "Cohen",
                "dave@test.com",
                LocalDate.of(1991, 2, 2),
                MemberStatus.ACTIVE);
        when(memberService.updateDetails(nullable(MemberId.class), any(MemberId.class), any(UpdateMemberDetailsRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(put("/api/members/{targetId}/details", memberId.value())
                        .requestAttr("authenticatedMemberId", memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Dave"))
                .andExpect(jsonPath("$.email").value("dave@test.com"));
    }

    @Test
    @DisplayName("Delete self - Returns 204 No Content")
    void cancelOwnAccount_ReturnsNoContent() throws Exception {
        MemberId memberId = new MemberId("member-123");

        mockMvc.perform(delete("/api/members/{targetId}", memberId.value())
                        .requestAttr("authenticatedMemberId", memberId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Get details - Missing member - Returns 404 Not Found")
    void getDetails_MissingMember_ReturnsNotFound() throws Exception {
        MemberId memberId = new MemberId("member-404");
        when(memberService.getDetails(nullable(MemberId.class), any(MemberId.class)))
                .thenThrow(new MemberNotFoundException(memberId));

        mockMvc.perform(get("/api/members/{targetId}", memberId.value())
                        .requestAttr("authenticatedMemberId", memberId))
                .andExpect(status().isNotFound());
    }
}
