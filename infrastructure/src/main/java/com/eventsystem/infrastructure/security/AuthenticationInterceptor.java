package com.eventsystem.infrastructure.security;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.security.TokenService;
import com.eventsystem.domain.member.MemberId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    public AuthenticationInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 1. Extract the Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthenticationException("Missing or malformed Authorization header. Expected 'Bearer <token>'");
        }

        // 2. Extract the token string (remove the "Bearer " prefix)
        String token = authHeader.substring(7);

        try {
            // 3. Verify the token and extract claims (like MemberId)
            TokenService.TokenClaims claims = tokenService.verifyToken(token);
            
            // 4. Inject the MemberId into the request!
            // Now every controller can use @RequestAttribute("authenticatedMemberId")
            request.setAttribute("authenticatedMemberId", claims.subject());
            
            return true;
            
        } catch (TokenService.InvalidTokenException e) {
            // If token verification fails, throw an AuthenticationException that will be handled globally
            throw new AuthenticationException("Invalid or expired token: " + e.getMessage());
        }
    }
}