package com.eventsystem.application.auth;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.security.PasswordHasher;
import com.eventsystem.application.security.TokenService;
import com.eventsystem.application.security.TokenService.TokenClaims;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberRepository;
import com.eventsystem.domain.member.PersonalDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MemberRepository members;
    @Mock private PasswordHasher hasher;
    @Mock private TokenService tokens;

    private AuthService service;

    private static final HashedCredentials CREDS = new HashedCredentials("h", "s", "BCrypt");
    private static final PersonalDetails DETAILS = new PersonalDetails(
            "Jon", "Snow", "jon@x", LocalDate.of(1990, 1, 1));

    @BeforeEach
    void setUp() {
        service = new AuthService(members, hasher, tokens, Duration.ofHours(1));
    }

    private RegisterMemberRequest validRegister() {
        return new RegisterMemberRequest(
                "jon", "password123", "Jon", "Snow", "jon@x", LocalDate.of(1990, 1, 1));
    }

    // -------------------- register --------------------

    @Test
    void registerHashesPasswordAndSavesMember() {
        when(members.findByUsername("jon")).thenReturn(Optional.empty());
        when(hasher.hash("password123")).thenReturn(CREDS);

        MemberId id = service.register(validRegister());

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(members).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("jon");
        assertThat(saved.getHashedCredentials()).isEqualTo(CREDS);
        assertThat(saved.getMemberId()).isEqualTo(id);
    }

    @Test
    void registerRejectsDuplicateUsername() {
        Member existing = new Member(MemberId.generate(), "jon", CREDS, DETAILS);
        when(members.findByUsername("jon")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(validRegister()))
                .isInstanceOf(UsernameAlreadyTakenException.class);
        verify(members, never()).save(any());
    }

    @Test
    void registerRejectsShortPassword() {
        RegisterMemberRequest req = new RegisterMemberRequest(
                "jon", "short", "Jon", "Snow", "jon@x", LocalDate.of(1990, 1, 1));
        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @Test
    void registerRejectsBlankFields() {
        RegisterMemberRequest req = new RegisterMemberRequest(
                " ", "password123", "Jon", "Snow", "jon@x", LocalDate.of(1990, 1, 1));
        assertThatThrownBy(() -> service.register(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerRejectsNullRequest() {
        assertThatThrownBy(() -> service.register(null)).isInstanceOf(NullPointerException.class);
    }

    // -------------------- login --------------------

    @Test
    void loginReturnsTokenOnSuccess() {
        Member m = new Member(MemberId.generate(), "jon", CREDS, DETAILS);
        Instant exp = Instant.now().plusSeconds(3600);
        when(members.findByUsername("jon")).thenReturn(Optional.of(m));
        when(hasher.matches("pw", CREDS)).thenReturn(true);
        when(tokens.issueToken(eq(m.getMemberId()), any())).thenReturn("token-xyz");
        when(tokens.verifyToken("token-xyz")).thenReturn(
                new TokenClaims(m.getMemberId(), Instant.now(), exp));

        LoginResponse resp = service.login(new LoginRequest("jon", "pw"));

        assertThat(resp.token()).isEqualTo("token-xyz");
        assertThat(resp.memberId()).isEqualTo(m.getMemberId());
        assertThat(resp.expiresAt()).isEqualTo(exp);
    }

    @Test
    void loginRejectsWrongPassword() {
        Member m = new Member(MemberId.generate(), "jon", CREDS, DETAILS);
        when(members.findByUsername("jon")).thenReturn(Optional.of(m));
        when(hasher.matches("bad", CREDS)).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("jon", "bad")))
                .isInstanceOf(AuthenticationException.class);
        verify(tokens, never()).issueToken(any(), any());
    }

    @Test
    void loginRejectsUnknownUsernameWithGenericMessage() {
        when(members.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "pw")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void loginRejectsCancelledMember() {
        Member m = new Member(MemberId.generate(), "jon", CREDS, DETAILS);
        m.cancel();
        when(members.findByUsername("jon")).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.login(new LoginRequest("jon", "pw")))
                .isInstanceOf(AuthenticationException.class);
        verify(hasher, never()).matches(any(), any());
    }

    @Test
    void loginRejectsBlankCredentials() {
        assertThatThrownBy(() -> service.login(new LoginRequest("", "pw")))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("jon", "")))
                .isInstanceOf(AuthenticationException.class);
    }

    // -------------------- authenticate --------------------

    @Test
    void authenticateDelegatesToTokenService() {
        MemberId id = MemberId.generate();
        when(tokens.verifyToken("t")).thenReturn(new TokenClaims(id, Instant.now(), Instant.now().plusSeconds(60)));
        assertThat(service.authenticate("t")).isEqualTo(id);
    }
}
