package com.eventsystem.infrastructure.api.admin;

import com.eventsystem.application.company.ProductionCompanyService;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.order.PurchaseHistoryService;
import com.eventsystem.application.purchaserecorddto.EventSnapshotDTO;
import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.application.purchaserecorddto.PurchasedItemDTO;
import com.eventsystem.application.purchaserecorddto.DiscountSnapshotDTO;
import com.eventsystem.domain.company.CompanyId;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.platform.IPlatformRepository;
import com.eventsystem.domain.platform.Platform;
import com.eventsystem.domain.shared.Money;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AdminStreamController.class, properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @SuppressWarnings("unused")
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IPlatformRepository platformRepository;

    @MockBean
    private ProductionCompanyService productionCompanyService;

    @MockBean
    private PurchaseHistoryService purchaseHistoryService;

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
    @DisplayName("DELETE /api/admin/companies/{companyId} closes company and returns 204")
    void closeCompany_ReturnsNoContent() throws Exception {
        MemberId admin = MemberId.generate();
        when(platformRepository.findInstance()).thenReturn(Optional.of(new Platform(admin, Duration.ofMinutes(15), 100)));

        mockMvc.perform(delete("/api/admin/companies/{companyId}", "company-1")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isNoContent());

        verify(productionCompanyService).adminCloseCompany(new CompanyId("company-1"));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("DELETE /api/admin/companies/{companyId} rejects non-admin")
    void closeCompany_RejectsNonAdmin() throws Exception {
        MemberId admin = MemberId.generate();
        MemberId actor = MemberId.generate();
        when(platformRepository.findInstance()).thenReturn(Optional.of(new Platform(admin, Duration.ofMinutes(15), 100)));

        mockMvc.perform(delete("/api/admin/companies/{companyId}", "company-1")
                        .requestAttr("authenticatedMemberId", actor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("GET /api/admin/history returns paged purchase history")
    void getGlobalHistory_ReturnsPagedHistory() throws Exception {
        MemberId admin = MemberId.generate();
        when(platformRepository.findInstance()).thenReturn(Optional.of(new Platform(admin, Duration.ofMinutes(15), 100)));

        PurchaseRecordDTO first = new PurchaseRecordDTO(
                "REC-2",
                "buyer-2",
                "Buyer Two",
                new EventSnapshotDTO("EV-2", "Event Two", "Company Two", LocalDate.of(2026, 5, 28), "Hall 2"),
                List.of(new PurchasedItemDTO("Zone B", "Seat B1", 1, Money.of(BigDecimal.valueOf(75), "USD"))),
                Money.of(BigDecimal.valueOf(75), "USD"),
                List.of(new DiscountSnapshotDTO("NONE", Money.of(BigDecimal.ZERO, "USD"))),
                Instant.parse("2026-05-28T10:15:31Z"),
                "PAY-2",
                "TICK-2");
        PurchaseRecordDTO second = new PurchaseRecordDTO(
                "REC-1",
                "buyer-1",
                "Buyer One",
                new EventSnapshotDTO("EV-1", "Event One", "Company One", LocalDate.of(2026, 5, 27), "Hall 1"),
                List.of(new PurchasedItemDTO("Zone A", "Seat A1", 1, Money.of(BigDecimal.valueOf(50), "USD"))),
                Money.of(BigDecimal.valueOf(50), "USD"),
                List.of(new DiscountSnapshotDTO("NONE", Money.of(BigDecimal.ZERO, "USD"))),
                Instant.parse("2026-05-27T10:15:30Z"),
                "PAY-1",
                "TICK-1");

        when(purchaseHistoryService.getGlobalHistory()).thenReturn(List.of(second, first));

        mockMvc.perform(get("/api/admin/history")
                        .param("page", "0")
                        .param("size", "1")
                        .requestAttr("authenticatedMemberId", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].recordId").value("REC-2"));
    }
}
