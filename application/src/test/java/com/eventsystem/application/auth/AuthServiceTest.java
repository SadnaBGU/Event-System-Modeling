package com.eventsystem.application.auth;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.member.MemberService;
import com.eventsystem.application.security.IPasswordHasher;
import com.eventsystem.application.security.ITokenService;
import com.eventsystem.application.security.ITokenService.TokenClaims;
import com.eventsystem.domain.member.HashedCredentials;
import com.eventsystem.domain.member.IMemberRepository;
import com.eventsystem.domain.member.Member;
import com.eventsystem.domain.member.MemberId;
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

    @Mock private ITokenService tokens;

    private AuthService service;

    private static final HashedCredentials CREDS = new HashedCredentials("h", "s", "BCrypt");
    private static final PersonalDetails DETAILS = new PersonalDetails(LocalDate.of(1990, 1, 1), "jon@x", 
            "Jon", "Snow");

    @BeforeEach
    void setUp() {
        service = new AuthService(tokens);
    }

    // -------------------- authenticate --------------------

    @Test
    void authenticateDelegatesToTokenService() {
        MemberId id = MemberId.generate();
        when(tokens.verifyToken("t")).thenReturn(new TokenClaims(id, Instant.now(), Instant.now().plusSeconds(60)));
        assertThat(service.authenticate("t")).isEqualTo(id);
    }
}
