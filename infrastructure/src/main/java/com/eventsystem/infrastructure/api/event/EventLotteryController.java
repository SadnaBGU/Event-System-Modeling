package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lottery registration (any authenticated member) and organiser actions
 * (opening a lottery for an event). Opening is gated by the same event-management
 * permission used elsewhere: only an owner/manager of the event's company may do it.
 */
@RestController
@RequestMapping("/api/events/{eventId}/lottery")
public class EventLotteryController {

    private final LotteryService lotteryService;
    private final ILotteryRepository lotteryRepository;
    private final IEventRepository eventRepository;
    private final ICompanyPermissionServicePort permissionService;

    public EventLotteryController(LotteryService lotteryService,
                                  ILotteryRepository lotteryRepository,
                                  IEventRepository eventRepository,
                                  ICompanyPermissionServicePort companyPermissionServicePort) {
        this.lotteryService = lotteryService;
        this.lotteryRepository = lotteryRepository;
        this.eventRepository = eventRepository;
        this.permissionService = companyPermissionServicePort;
    }

    /** Participant action: enter the lottery for this event. */
    @PostMapping("/registrations")
    public ResponseEntity<Void> registerForLottery(
            @PathVariable String eventId,
            @RequestAttribute("authenticatedMemberId") MemberId authenticatedMemberId) {
        lotteryService.register(authenticatedMemberId, new EventId(eventId));
        return ResponseEntity.status(201).build();
    }

    /** Organiser action: open a lottery for this event (requires event-management permission). */
    @PostMapping("")
    public ResponseEntity<Map<String, Object>> openLottery(
            @PathVariable String eventId,
            @RequestAttribute("authenticatedMemberId") MemberId actor) {
        EventId eId = new EventId(eventId);
        requireManagePermission(actor, eId);

        if (lotteryRepository.findByEventId(eId).isPresent()) {
            throw new IllegalStateException("A lottery already exists for this event");
        }

        LotteryId lotteryId = lotteryService.openLottery(eId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lotteryId", lotteryId.value());
        payload.put("status", "REGISTRATION_OPEN");
        return ResponseEntity.status(201).body(payload);
    }

    /** Public read: whether a lottery exists for this event and its current status. */
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getLotteryStatus(@PathVariable String eventId) {
        Optional<Lottery> lottery = lotteryRepository.findByEventId(new EventId(eventId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exists", lottery.isPresent());
        payload.put("status", lottery.map(l -> l.getStatus().name()).orElse(null));
        return ResponseEntity.ok(payload);
    }

    private void requireManagePermission(MemberId actor, EventId eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId.value()));
        if (!permissionService.canManageEvents(actor, event.companyId())) {
            throw new SecurityException("You do not have permission to manage this event");
        }
    }
}
