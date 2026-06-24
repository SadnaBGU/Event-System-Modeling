package com.eventsystem.application.acceptance;

import com.eventsystem.application.appexceptions.AuthenticationException;
import com.eventsystem.application.appexceptions.UsernameAlreadyTakenException;
import com.eventsystem.application.auth.AuthService;
import com.eventsystem.application.auth.LoginRequest;
import com.eventsystem.application.auth.LoginResponse;
import com.eventsystem.application.auth.RegisterMemberRequest;
import com.eventsystem.application.member.MemberDto;
import com.eventsystem.application.member.UpdateMemberDetailsRequest;
import com.eventsystem.domain.member.MemberId;
import com.eventsystem.domain.member.MemberStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acceptance tests for member self-service:
 *   UC 4  - Guest Registration
 *   UC 5  - Member Login
 *   UC 13 - Update Identifying Details
 *
 * Uses the real {@link com.eventsystem.application.member.MemberService} with
 * fake (deterministic) password hashing and token services from the fixture.
 */
class UC04_05_13_MemberAuthAcceptanceTest {

    private static RegisterMemberRequest validRegister(String username) {
        return new RegisterMemberRequest(
                username,
                "password123",
                "Jon",
                "Snow",
                "jon@example.com",
                LocalDate.of(1990, 1, 1));
    }

    // REQ: USR-01
    // UC: UC 4 - Guest Registration
    // UAT: UAT-10 - Successful Registration
    @Test
    void registerWithValidUniqueDetails_createsMemberThatCanThenLogIn() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        MemberId newId = app.memberService.register(validRegister("jon"));

        assertThat(newId).isNotNull();
        assertThat(app.members.findById(newId)).isPresent();
        assertThat(app.members.findByUsername("jon")).isPresent();

        // The newly registered member can authenticate with the same credentials.
        LoginResponse login = app.memberService.login(new LoginRequest("jon", "password123"));
        assertThat(login.memberId()).isEqualTo(newId);
    }

    // REQ: USR-01
    // UC: UC 4 - Guest Registration
    // UAT: UAT-11 - Registration Duplicate Details
    @Test
    void registerWithExistingUsername_isRejectedAndDoesNotCreateSecondMember() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();

        app.memberService.register(validRegister("jon"));

        assertThatThrownBy(() -> app.memberService.register(validRegister("jon")))
                .isInstanceOf(UsernameAlreadyTakenException.class);

        // Still exactly one member with that username.
        assertThat(app.members.findAll().stream()
                .filter(m -> "jon".equals(m.getUsername()))
                .count())
                .isEqualTo(1);
    }

    // REQ: USR-02
    // UC: UC 5 - Member Login
    // UAT: UAT-12 - Successful Login
    @Test
    void loginWithValidCredentials_returnsTokenThatResolvesToTheMember() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId memberId = app.memberService.register(validRegister("jon"));

        LoginResponse response = app.memberService.login(new LoginRequest("jon", "password123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.expiresAt()).isNotNull();

        // The issued token resolves back to the same member via AuthService.
        AuthService authService = new AuthService(app.tokenService);
        assertThat(authService.authenticate(response.token())).isEqualTo(memberId);
    }

    // REQ: USR-02
    // UC: UC 5 - Member Login
    // UAT: UAT-13 - Invalid Login Credentials
    @Test
    void loginWithWrongPasswordOrUnknownUsername_isRejected() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        app.memberService.register(validRegister("jon"));

        assertThatThrownBy(() -> app.memberService.login(new LoginRequest("jon", "wrong-password")))
                .isInstanceOf(AuthenticationException.class);

        assertThatThrownBy(() -> app.memberService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(AuthenticationException.class);
    }

    // REQ: USR-03
    // UC: UC 13 - Update Identifying Details
    // UAT: UAT-37 - Update Profile Success
    @Test
    void memberUpdatesOwnDetails_changesArePersistedAndReturned() {
        ApplicationAcceptanceFixture app = new ApplicationAcceptanceFixture();
        MemberId memberId = app.memberService.register(validRegister("jon"));

        UpdateMemberDetailsRequest update = new UpdateMemberDetailsRequest(
                "Jonathan",
                "Stark",
                "jonathan@example.com",
                LocalDate.of(1985, 5, 20));

        MemberDto updated = app.memberService.updateDetails(memberId, memberId, update);

        assertThat(updated.firstName()).isEqualTo("Jonathan");
        assertThat(updated.lastName()).isEqualTo("Stark");
        assertThat(updated.email()).isEqualTo("jonathan@example.com");
        assertThat(updated.dateOfBirth()).isEqualTo(LocalDate.of(1985, 5, 20));
        assertThat(updated.status()).isEqualTo(MemberStatus.ACTIVE);

        // Re-reading the member returns the updated details.
        MemberDto reread = app.memberService.getDetails(memberId, memberId);
        assertThat(reread.firstName()).isEqualTo("Jonathan");
        assertThat(reread.email()).isEqualTo("jonathan@example.com");
    }
}
