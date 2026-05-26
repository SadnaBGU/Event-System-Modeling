package com.eventsystem.infrastructure.api.member;

import com.eventsystem.application.member.MemberDto;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.member.UpdateMemberDetailsRequest;
import com.eventsystem.domain.member.MemberId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/{targetId}")
    public ResponseEntity<MemberDto> getDetails(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String targetId) {
        MemberDto dto = memberService.getDetails(actor, new MemberId(targetId));
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{targetId}/details")
    public ResponseEntity<MemberDto> updateDetails(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String targetId,
            @RequestBody UpdateMemberDetailsRequest request) {
        MemberDto dto = memberService.updateDetails(actor, new MemberId(targetId), request);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{targetId}")
    public void cancelOwnAccount(
            @RequestAttribute("authenticatedMemberId") MemberId actor,
            @PathVariable String targetId,
            HttpServletResponse response) {
        memberService.cancelOwnAccount(actor, new MemberId(targetId));
        response.setStatus(HttpStatus.NO_CONTENT.value());
    }
}
