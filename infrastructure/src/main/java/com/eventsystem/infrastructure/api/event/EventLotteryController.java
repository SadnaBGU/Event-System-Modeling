package com.eventsystem.infrastructure.api.event;

import com.eventsystem.application.company.ICompanyPermissionServicePort;
import com.eventsystem.application.appexceptions.LotteryNotFoundException;
import com.eventsystem.application.lottery.LotteryService;
import com.eventsystem.domain.event.Event;
import com.eventsystem.domain.event.EventId;
import com.eventsystem.domain.event.IEventRepository;
import com.eventsystem.domain.lottery.ILotteryRepository;
import com.eventsystem.domain.lottery.Lottery;
import com.eventsystem.domain.lottery.LotteryId;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final IMemberRepository memberRepository;

    public EventLotteryController(LotteryService lotteryService,
                                  ILotteryRepository lotteryRepository,
                                  IEventRepository eventRepository,
                                  ICompanyPermissionServicePort companyPermissionServicePort,
                                  IMemberRepository memberRepository) {
        this.lotteryService = lotteryService;
        this.lotteryRepository = lotteryRepository;
        this.eventRepository = eventRepository;
        this.permissionService = companyPermissionServicePort;
        this.memberRepository = memberRepository;
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

    /**
     * Organiser action: close registration and draw the winners for this event's lottery
     * (requires event-management permission). The draw is terminal: the lottery moves to
     * DRAWN and winners receive time-limited permission codes.
     */
    @PostMapping("/draw")
    public ResponseEntity<Map<String, Object>> drawLottery(
            @PathVariable String eventId,
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @RequestBody(required = false) DrawLotteryRequest body) {
        EventId eId = new EventId(eventId);
        requireManagePermission(actor, eId);

        Lottery lottery = lotteryRepository.findByEventId(eId)
                .orElseThrow(() -> new LotteryNotFoundException(eId));

        int winnerCount = (body != null && body.winnerCount() != null) ? body.winnerCount() : 0;
        if (winnerCount < 0) {
            throw new IllegalArgumentException("winnerCount must be non-negative");
        }

        lotteryService.draw(lottery.getLotteryId(), winnerCount);

        Lottery drawn = lotteryRepository.findByEventId(eId).orElseThrow(() -> new LotteryNotFoundException(eId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lotteryId", drawn.getLotteryId().value());
        payload.put("status", drawn.getStatus().name());
        payload.put("winners", drawn.getWinners().size());
        return ResponseEntity.ok(payload);
    }

    /** Request body for {@link #drawLottery}: how many winners to pick (capped at the pool size). */
    public record DrawLotteryRequest(Integer winnerCount) {}

    /**
     * Organiser read: list the winners of this event's lottery (requires event-management
     * permission). Returns each winner's id, username and code expiry. The private purchase
     * code itself is not exposed here — it is delivered to the winner via their notification.
     */
    @GetMapping("/winners")
    public ResponseEntity<List<Map<String, Object>>> getWinners(
            @PathVariable String eventId,
            @RequestAttribute("authenticatedMemberId") MemberId actor) {
        EventId eId = new EventId(eventId);
        requireManagePermission(actor, eId);

        Lottery lottery = lotteryRepository.findByEventId(eId)
                .orElseThrow(() -> new LotteryNotFoundException(eId));

        List<Map<String, Object>> winners = lottery.getWinners().stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("memberId", w.memberId().value());
            m.put("username", memberRepository.findById(w.memberId()).map(Member::getUsername).orElse(w.memberId().value()));
            m.put("codeExpiry", w.codeExpiry().toString());
            return m;
        }).toList();

        return ResponseEntity.ok(winners);
    }

    private void requireManagePermission(MemberId actor, EventId eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId.value()));
        if (!permissionService.canManageEvents(actor, event.companyId())) {
            throw new SecurityException("You do not have permission to manage this event");
        }
    }
}
