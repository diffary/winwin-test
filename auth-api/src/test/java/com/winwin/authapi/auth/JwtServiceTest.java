package com.winwin.authapi.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwt = new JwtService("0123456789abcdef0123456789abcdef", 3600);

    @Test
    void issuedTokenParsesBackToSameUser() {
        UUID userId = UUID.randomUUID();

        String token = jwt.issue(userId);

        assertThat(jwt.parseUserId(token)).contains(userId);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.issue(UUID.randomUUID());

        assertThat(jwt.parseUserId(token + "x")).isEmpty();
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        JwtService other = new JwtService("ffffffffffffffffffffffffffffffff", 3600);
        String foreignToken = other.issue(UUID.randomUUID());

        assertThat(jwt.parseUserId(foreignToken)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService("0123456789abcdef0123456789abcdef", -10);
        String expired = shortLived.issue(UUID.randomUUID());

        assertThat(jwt.parseUserId(expired)).isEmpty();
    }
}
