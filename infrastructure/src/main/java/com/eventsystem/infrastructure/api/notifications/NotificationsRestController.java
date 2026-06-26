package com.eventsystem.infrastructure.api.notifications;

import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.member.NotificationDto;
import com.eventsystem.domain.member.MemberId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsRestController {

    private final MemberService memberService;

    public NotificationsRestController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/pending")
    @Transactional
    public ResponseEntity<Map<String,Object>> pending(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @RequestParam(name = "markAsRead", defaultValue = "true") boolean markAsRead) {
        List<NotificationDto> pending = memberService.getAndMarkPendingNotifications(actor, markAsRead);

        return ResponseEntity.ok(Map.of("notifications", pending));
    }
}
