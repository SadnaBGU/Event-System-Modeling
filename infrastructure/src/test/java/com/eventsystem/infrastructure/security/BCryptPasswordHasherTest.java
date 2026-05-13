package com.eventsystem.infrastructure.security;

import com.eventsystem.domain.member.HashedCredentials;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(4); // low cost = fast tests

    @Test
    void hashProducesBCryptCredentials() {
        HashedCredentials creds = hasher.hash("s3cret!");
        assertThat(creds.algorithm()).isEqualTo("BCrypt");
        assertThat(creds.hash()).startsWith("$2");
        assertThat(creds.salt()).isNotBlank();
    }

    @Test
    void hashIsSaltedSoSamePlaintextProducesDifferentHashes() {
        HashedCredentials a = hasher.hash("same");
        HashedCredentials b = hasher.hash("same");
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void matchesAcceptsCorrectPassword() {
        HashedCredentials creds = hasher.hash("correct horse battery staple");
        assertThat(hasher.matches("correct horse battery staple", creds)).isTrue();
    }

    @Test
    void matchesRejectsWrongPassword() {
        HashedCredentials creds = hasher.hash("right");
        assertThat(hasher.matches("wrong", creds)).isFalse();
    }

    @Test
    void matchesRejectsNullOrEmptyPlaintext() {
        HashedCredentials creds = hasher.hash("right");
        assertThat(hasher.matches(null, creds)).isFalse();
        assertThat(hasher.matches("", creds)).isFalse();
    }

    @Test
    void matchesRejectsUnsupportedAlgorithm() {
        HashedCredentials foreign = new HashedCredentials("h", "s", "MD5");
        assertThatThrownBy(() -> hasher.matches("x", foreign))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashRejectsNullAndEmpty() {
        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> hasher.hash("")).isInstanceOf(IllegalArgumentException.class);
    }
}
