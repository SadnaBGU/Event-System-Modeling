package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.order.QueueService;
import com.eventsystem.application.order.QueueService.AdmissionStatus;
import com.eventsystem.domain.order.BuyerReference;
import com.eventsystem.domain.order.BuyerType;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events/{eventId}/queue")
public class EventQueueController {

    private final QueueService queueService;

    public EventQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/entries")
    public ResponseEntity<Void> enqueueVisitor(
            @PathVariable String eventId,
            @RequestAttribute(name = "authenticatedMemberId", required = false) MemberId authenticatedMember,
            @RequestParam(name = "sessionId", required = false) String sessionId
    ) {
        BuyerReference buyer;
        if (authenticatedMember != null) {
            buyer = new BuyerReference(BuyerType.MEMBER, sessionId, authenticatedMember.value());
        } else if (sessionId != null && !sessionId.isBlank()) {
            buyer = new BuyerReference(BuyerType.GUEST, sessionId, null);
        } else {
            throw new IllegalArgumentException("Either authenticated member or sessionId is required");
        }

        queueService.enqueueVisitor(eventId, buyer);
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String,Object>> admissionStatus(
            @PathVariable String eventId,
            @RequestAttribute(name = "authenticatedMemberId", required = false) MemberId authenticatedMember,
            @RequestParam(name = "sessionId", required = false) String sessionId
    ) {
        BuyerReference buyer;
        if (authenticatedMember != null) {
            buyer = new BuyerReference(BuyerType.MEMBER, sessionId, authenticatedMember.value());
        } else if (sessionId != null && !sessionId.isBlank()) {
            buyer = new BuyerReference(BuyerType.GUEST, sessionId, null);
        } else {
            throw new IllegalArgumentException("Either authenticated member or sessionId is required");
        }

        AdmissionStatus s = queueService.getAdmissionStatus(eventId, buyer);
        return ResponseEntity.ok(Map.of("isAdmitted", s.isAdmitted, "position", s.position));
    }

    @DeleteMapping("/admissions")
    public ResponseEntity<Void> revokeAdmission(
            @PathVariable String eventId,
            @RequestAttribute(name = "authenticatedMemberId", required = false) MemberId authenticatedMember,
            @RequestParam(name = "sessionId", required = false) String sessionId
    ) {
        BuyerReference buyer;
        if (authenticatedMember != null) {
            buyer = new BuyerReference(BuyerType.MEMBER, sessionId, authenticatedMember.value());
        } else if (sessionId != null && !sessionId.isBlank()) {
            buyer = new BuyerReference(BuyerType.GUEST, sessionId, null);
        } else {
            throw new IllegalArgumentException("Either authenticated member or sessionId is required");
        }

        queueService.revokeAdmission(eventId, buyer);
        return ResponseEntity.noContent().build();
    }
}
