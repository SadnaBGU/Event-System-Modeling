package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/lottery")
public class EventLotteryController {

    private final LotteryService lotteryService;

    public EventLotteryController(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    @PostMapping("/registrations")
    public ResponseEntity<Void> registerForLottery(
            @PathVariable String eventId,
            @RequestAttribute("authenticatedMemberId") MemberId authenticatedMemberId) {
        lotteryService.register(authenticatedMemberId, new EventId(eventId));
        return ResponseEntity.status(201).build();
    }
}
