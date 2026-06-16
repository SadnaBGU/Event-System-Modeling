package com.eventsystem.infrastructure.api.notifications;

import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.infrastructure.persistence.springrepos.PostgresMemberRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
public class NotificationsRestController {

    private final IMemberRepository memberRepository;

    public NotificationsRestController(IMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/pending")
    @Transactional
    public ResponseEntity<Map<String,Object>> pending(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @RequestParam(name = "markAsRead", defaultValue = "true") boolean markAsRead) {
        Member m = findMember(actor, markAsRead)
                .orElseThrow(() -> new MemberNotFoundException(actor));

        List<NotificationDto> pending;
        synchronized (m) {
            pending = m.getUndeliveredNotifications().stream()
                    .map(n -> new NotificationDto(n.getNotificationId(), n.getType().name(), n.getContent(), n.getCreatedAt().toString(), n.isDelivered()))
                    .collect(Collectors.toList());

            if (markAsRead && !pending.isEmpty()) {
                m.markNotificationsDelivered();
                memberRepository.save(m);
            }
        }

        return ResponseEntity.ok(Map.of("notifications", pending));
    }

    private Optional<Member> findMember(MemberId actor, boolean markAsRead) {
        if (markAsRead && memberRepository instanceof PostgresMemberRepository postgresMemberRepository) {
            return postgresMemberRepository.findByIdForUpdate(actor);
        }
        return memberRepository.findById(actor);
    }
}
