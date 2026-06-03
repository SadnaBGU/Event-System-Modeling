package com.eventsystem.infrastructure.security;

import com.eventsystem.application.member.IMemberRepository;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationInterceptorTest {

    @Mock
    private ITokenService tokenService;

    @Mock
    private IMemberRepository memberRepository;

    private AuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthenticationInterceptor(tokenService, memberRepository);
    }

    @Test
    void preHandle_optionsBypassesAuthAndReturnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/any");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandle_postWithActiveMemberAllowsRequestAndSetsAttribute() {
        MemberId memberId = new MemberId("member-1");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/any");
        request.addHeader("Authorization", "Bearer token-1");

        when(tokenService.verifyToken("token-1"))
                .thenReturn(new ITokenService.TokenClaims(memberId, Instant.EPOCH, Instant.EPOCH.plusSeconds(60)));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(new Member(memberId)));

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authenticatedMemberId")).isEqualTo(memberId);
    }

    @Test
    void preHandle_postWithSuspendedMemberThrowsSecurityException() {
        MemberId memberId = new MemberId("member-2");
        Member member = new Member(memberId);
        member.suspend(Instant.now().minus(1, ChronoUnit.HOURS), null, "test suspension");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/any");
        request.addHeader("Authorization", "Bearer token-2");

        when(tokenService.verifyToken("token-2"))
                .thenReturn(new ITokenService.TokenClaims(memberId, Instant.EPOCH, Instant.EPOCH.plusSeconds(60)));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Account is suspended. Access denied.");
    }

    // test that suspended members actually stay suspended when they should not 
    @Test
    void preHandle_postWithExpiredTemporarySuspension_shouldRefreshStatusSaveMemberAndAllowRequest() {
        MemberId memberId = new MemberId("member-expired-suspension");
        Member member = new Member(memberId);

        member.suspend(
                Instant.now().minus(2, ChronoUnit.DAYS),
                java.time.Duration.ofDays(1),
                "expired temporary suspension"
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/any");
        request.addHeader("Authorization", "Bearer token-expired");

        when(tokenService.verifyToken("token-expired"))
                .thenReturn(new ITokenService.TokenClaims(
                        memberId,
                        Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(60)
                ));

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        boolean allowed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()
        );

        assertThat(allowed).isTrue();
        assertThat(request.getAttribute("authenticatedMemberId")).isEqualTo(memberId);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspension()).isEmpty();

        verify(memberRepository).save(member);
    }
}