package com.eventsystem.infrastructure.security;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.MemberNotFoundException;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final ITokenService tokenService;
    private final IMemberRepository memberRepository;

    public AuthenticationInterceptor(ITokenService tokenService, IMemberRepository memberRepository) {
        this.tokenService = tokenService;
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 1. Extract the Authorization header
        boolean isGuestAllowed = false;

        if (path.startsWith("/api/events") && !path.contains("/policies") && !path.contains("/lottery")){
            isGuestAllowed = true;
        }

        if (path.startsWith("/api/orders") || path.startsWith("/api/checkout")) {
            isGuestAllowed = true;
        }

        if ("GET".equalsIgnoreCase(method) && path.startsWith("/api/zones/") && path.endsWith("/seats")) {
            isGuestAllowed = true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (isGuestAllowed) {
                return true;
            }
            throw new AuthenticationException("Missing or malformed Authorization header. Expected 'Bearer <token>'");
        }

        // 2. Extract the token string (remove the "Bearer " prefix)
        String token = authHeader.substring(7);

        try {
            // 3. Verify the token and extract claims (like MemberId)
            ITokenService.TokenClaims claims = tokenService.verifyToken(token);
            MemberId memberId = claims.subject();

            // 4. Enforce suspended account check
            if(!"GET".equalsIgnoreCase(method)) {
                Member member = memberRepository.findById(memberId)
                        .orElseThrow(() -> new MemberNotFoundException(memberId));
                //added verifySuspensionAndSave(member)- check if suspension status should change, if true, save the change
                if (verifySuspensionAndSave(member)) {
                    throw new SecurityException("Account is suspended. Access denied.");
                }
            }

            // 5. Inject the MemberId into the request!
            // Now every controller can use @RequestAttribute("authenticatedMemberId")
            request.setAttribute("authenticatedMemberId", memberId);

            return true;
            
        } catch (ITokenService.InvalidTokenException e) {
            // If token verification fails, throw an AuthenticationException that will be handled globally
            throw new AuthenticationException("Invalid or expired token: " + e.getMessage());
        }
    }

    private boolean verifySuspensionAndSave(Member member) {
        Instant now = Instant.now();
        boolean suspensionStatusChanged = member.refreshSuspensionStatusAt(now);
        if (suspensionStatusChanged) {
            memberRepository.save(member);
        }
        return member.isSuspendedAt(now);
    }
}