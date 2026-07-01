package com.eventsystem.infrastructure.api.admin;

import com.eventsystem.application.admin.AdminService;
import com.eventsystem.application.admin.PlatformDto;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.PlatformStatus;
import com.eventsystem.domain.shared.ProviderId;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import com.eventsystem.infrastructure.security.AuthenticationInterceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventsystem.infrastructure.testsupport.InfrastructureSpringBootTest;

@InfrastructureSpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminPlatformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

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
    @DisplayName("GET /api/admin/platform returns the platform projection")
    void getPlatform_ReturnsDto() throws Exception {
        MemberId admin = MemberId.generate();
        ProviderId paymentProvider = new ProviderId("visa");
        ProviderId issuanceProvider = new ProviderId("ticketmaster");
        PlatformDto dto = new PlatformDto(
                PlatformStatus.ACTIVE,
                Set.of(admin),
                Set.of(paymentProvider),
                Set.of(issuanceProvider),
                Duration.ofMinutes(15),
                100);

        when(adminService.getPlatform(admin)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/platform")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.systemAdmins[0].value").value(admin.value()))
                .andExpect(jsonPath("$.paymentProviders[0].value").value(paymentProvider.value()))
                .andExpect(jsonPath("$.issuanceProviders[0].value").value(issuanceProvider.value()))
                .andExpect(jsonPath("$.defaultReservationTimeout").value("PT15M"))
                .andExpect(jsonPath("$.queueLoadThreshold").value(100));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Platform lifecycle endpoints delegate to AdminService")
    void lifecycleEndpoints_ReturnNoContent() throws Exception {
        MemberId admin = MemberId.generate();

        doNothing().when(adminService).activate(admin);
        doNothing().when(adminService).shutdown(admin);

        mockMvc.perform(post("/api/admin/platform/activate")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/platform/shutdown")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        verify(adminService).activate(admin);
        verify(adminService).shutdown(admin);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Admin membership endpoints delegate to AdminService")
    void adminMembershipEndpoints_DelegateToService() throws Exception {
        MemberId admin = MemberId.generate();
        MemberId target = MemberId.generate();

        doNothing().when(adminService).addAdmin(admin, target);
        doNothing().when(adminService).removeAdmin(admin, target);

        mockMvc.perform(post("/api/admin/admins/{memberId}", target.value())
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/admins/{memberId}", target.value())
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        verify(adminService).addAdmin(admin, target);
        verify(adminService).removeAdmin(admin, target);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Provider and tunable endpoints delegate to AdminService")
    void providerAndTunablesEndpoints_DelegateToService() throws Exception {
        MemberId admin = MemberId.generate();

        doNothing().when(adminService).addPaymentProvider(admin, new ProviderId("visa"));
        doNothing().when(adminService).removePaymentProvider(admin, new ProviderId("visa"));
        doNothing().when(adminService).addIssuanceProvider(admin, new ProviderId("tm"));
        doNothing().when(adminService).removeIssuanceProvider(admin, new ProviderId("tm"));
        doNothing().when(adminService).setDefaultReservationTimeout(admin, Duration.ofMinutes(30));
        doNothing().when(adminService).setQueueLoadThreshold(admin, 250);

        mockMvc.perform(post("/api/admin/payment-providers/{providerId}", "visa")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/payment-providers/{providerId}", "visa")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/issuance-providers/{providerId}", "tm")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/issuance-providers/{providerId}", "tm")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/admin/platform/reservation-timeout")
                        .param("timeout", "PT30M")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/admin/platform/queue-load-threshold")
                        .param("threshold", "250")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        verify(adminService).addPaymentProvider(admin, new ProviderId("visa"));
        verify(adminService).removePaymentProvider(admin, new ProviderId("visa"));
        verify(adminService).addIssuanceProvider(admin, new ProviderId("tm"));
        verify(adminService).removeIssuanceProvider(admin, new ProviderId("tm"));
        verify(adminService).setDefaultReservationTimeout(admin, Duration.ofMinutes(30));
        verify(adminService).setQueueLoadThreshold(admin, 250);
    }
}