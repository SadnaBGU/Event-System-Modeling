package com.eventsystem.infrastructure.api.history;

import com.eventsystem.application.order.PurchaseHistoryService;
import com.eventsystem.application.purchaserecorddto.EventSnapshotDTO;
import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.application.purchaserecorddto.PurchasedItemDTO;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.Money;
import com.eventsystem.infrastructure.api.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PurchaseHistoryControllerTest {

    @Mock
    private PurchaseHistoryService historyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PurchaseHistoryController(historyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/history/receipts returns pagination envelope")
    void listReceipts_returnsPaginationEnvelope() throws Exception {
        PurchaseRecordDTO record = new PurchaseRecordDTO(
                "REC-1",
                "member-1",
                "Member One",
                new EventSnapshotDTO("EVT-1", "Desert Rock Festival", "Company", null, "Beer Sheva"),
                List.of(),
                Money.of(new BigDecimal("250.00"), "ILS"),
                List.of(),
                Instant.parse("2026-05-25T14:30:00Z"),
                "PAY-1",
                "TKT-1"
        );

        when(historyService.getHistoryForBuyer("member-1")).thenReturn(List.of(record));

        mockMvc.perform(get("/api/history/receipts")
                        .requestAttr("authenticatedMemberId", new MemberId("member-1"))
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].recordId").value("REC-1"))
                .andExpect(jsonPath("$.items[0].eventName").value("Desert Rock Festival"));
    }

    @Test
    @DisplayName("GET /api/history/receipts/{recordId} returns receipt detail")
    void getReceipt_returnsDetail() throws Exception {
        PurchaseRecordDTO record = new PurchaseRecordDTO(
                "REC-1",
                "member-1",
                "Member One",
                new EventSnapshotDTO("EVT-1", "Desert Rock Festival", "Company", null, "Beer Sheva"),
                List.of(new PurchasedItemDTO("VIP", "A-12", 1, Money.of(new BigDecimal("250.00"), "ILS"))),
                Money.of(new BigDecimal("250.00"), "ILS"),
                List.of(),
                Instant.parse("2026-05-25T14:30:00Z"),
                "PAY-1",
                "TKT-1"
        );

        when(historyService.getReceiptDetails("REC-1")).thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/history/receipts/REC-1")
                        .requestAttr("authenticatedMemberId", new MemberId("member-1"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("REC-1"))
                .andExpect(jsonPath("$.eventName").value("Desert Rock Festival"))
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.tickets[0].zoneId").value("VIP"));
    }

    @Test
    @DisplayName("GET /api/history/receipts/{recordId} rejects missing receipt with 404")
    void getReceipt_missing_ReturnsNotFound() throws Exception {
        when(historyService.getReceiptDetails("REC-404")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/history/receipts/REC-404")
                        .requestAttr("authenticatedMemberId", new MemberId("member-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/history/receipts/{recordId} rejects cross-user access with 403")
    void getReceipt_wrongMember_ReturnsForbidden() throws Exception {
        PurchaseRecordDTO record = new PurchaseRecordDTO(
                "REC-1",
                "member-1",
                "Member One",
                new EventSnapshotDTO("EVT-1", "Desert Rock Festival", "Company", null, "Beer Sheva"),
                List.of(),
                Money.of(new BigDecimal("250.00"), "ILS"),
                List.of(),
                Instant.parse("2026-05-25T14:30:00Z"),
                "PAY-1",
                "TKT-1"
        );

        when(historyService.getReceiptDetails("REC-1")).thenReturn(Optional.of(record));

        mockMvc.perform(get("/api/history/receipts/REC-1")
                        .requestAttr("authenticatedMemberId", new MemberId("member-2")))
                .andExpect(status().isForbidden());
    }
}
