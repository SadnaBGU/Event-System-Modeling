package com.eventsystem.infrastructure.api.auth;

import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.auth.RegisterMemberRequest;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberService memberService;

    @MockBean
    private ITokenService tokenService;

    @MockBean
    private AuthenticationInterceptor authenticationInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("null")
    @Test
    @DisplayName("Register - Valid details - Returns 201 Created")
    void register_ValidDetails_ReturnsCreated() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
                "newuser", "pass123", "John", "Doe", "john@test.com", LocalDate.of(2000, 1, 1)
        );
        when(memberService.register(any(RegisterMemberRequest.class))).thenReturn(new MemberId("member-123"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Register - Username already taken - Returns 400 Bad Request")
    void register_UsernameTaken_ReturnsBadRequest() throws Exception {
        RegisterMemberRequest request = new RegisterMemberRequest(
                "david", "pass123", "David", "E", "d@test.com", LocalDate.of(1990, 1, 1)
        );
        doThrow(new UsernameAlreadyTakenException("david"))
                .when(memberService).register(any(RegisterMemberRequest.class));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Login - Valid credentials - Returns 200 OK and Token")
    void login_ValidCredentials_ReturnsOkAndToken() throws Exception {
        LoginRequest request = new LoginRequest("david", "password123");
        LoginResponse fakeResponse = new LoginResponse("mock-jwt-token", new MemberId("member-123"), Instant.now().plusSeconds(3600));
        
        when(memberService.login(any(LoginRequest.class))).thenReturn(fakeResponse);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.memberId.value").value("member-123"));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Login - Invalid password - Returns 401 Unauthorized")
    void login_InvalidPassword_ReturnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("david", "wrong_password");
        when(memberService.login(any(LoginRequest.class)))
                .thenThrow(new AuthenticationException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Login - Missing body parameters - Returns 400 Bad Request")
    void login_MissingBody_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}