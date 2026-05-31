package com.eventsystem.infrastructure.api.history;

import com.eventsystem.application.appexceptions.OrderNotFoundException;
import com.eventsystem.application.order.PurchaseHistoryService;
import com.eventsystem.application.purchaserecorddto.PurchaseRecordDTO;
import com.eventsystem.application.purchaserecorddto.PurchasedItemDTO;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.shared.Money;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/history/receipts")
public class PurchaseHistoryController {

    private final PurchaseHistoryService historyService;

    public PurchaseHistoryController(PurchaseHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("")
    public ResponseEntity<Map<String,Object>> listReceipts(
            @RequestAttribute(name = "authenticatedMemberId") MemberId member,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        String buyerId = member.value();
        List<PurchaseRecordDTO> all = historyService.getHistoryForBuyer(buyerId);

        int totalElements = all.size();
        int from = Math.max(0, page * size);
        int to = Math.min(totalElements, from + size);
        List<Map<String,Object>> items = all.subList(from, to).stream().map(this::toSummaryMap).collect(Collectors.toList());

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean hasNext = size > 0 && (page + 1) < totalPages;

        Map<String,Object> payload = Map.of(
            "currentPage", page,
            "hasNext", hasNext,
                "totalElements", totalElements,
                "totalPages", totalPages,
                "items", items
        );

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<Map<String,Object>> getReceipt(@RequestAttribute(name = "authenticatedMemberId") MemberId member,
                                                          @PathVariable String recordId) {
        PurchaseRecordDTO dto = historyService.getReceiptDetails(recordId)
                .orElseThrow(() -> new OrderNotFoundException("receipt not found: " + recordId));

        // ensure the requesting member matches the receipt owner
        if (!dto.buyerId().equals(member.value())) {
            throw new SecurityException("access denied");
        }

        Map<String,Object> payload = toDetailMap(dto);
        return ResponseEntity.ok(payload);
    }

    private Map<String,Object> toSummaryMap(PurchaseRecordDTO dto) {
        return Map.of(
                "recordId", dto.recordId(),
                "purchaseDate", dto.purchaseTimestamp().toString(),
                "eventName", dto.eventSnapshot().eventName(),
                "totalAmount", dto.totalPaid().amount(),
                "currency", dto.totalPaid().currency()
        );
    }

    private Map<String,Object> toDetailMap(PurchaseRecordDTO dto) {
        List<Map<String,Object>> tickets = dto.items().stream().map(i -> {
            PurchasedItemDTO pit = i;
            Money price = pit.priceAtPurchase();
            return Map.<String,Object>of(
                    "zoneId", pit.zoneName(),
                    "seatId", pit.seatId(),
                    "price", price.amount()
            );
        }).collect(Collectors.toList());

        String purchaseDate = dto.purchaseTimestamp().toString();

        String paymentStatus = dto.paymentConfirmationId() != null ? "COMPLETED" : "PENDING";

        return Map.of(
                "recordId", dto.recordId(),
                "purchaseDate", purchaseDate,
                "eventName", dto.eventSnapshot().eventName(),
                "totalAmount", dto.totalPaid().amount(),
                "currency", dto.totalPaid().currency(),
                "paymentStatus", paymentStatus,
                "tickets", tickets
        );
    }
}
