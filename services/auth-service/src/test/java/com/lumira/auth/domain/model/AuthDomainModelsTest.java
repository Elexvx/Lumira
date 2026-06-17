package com.lumira.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.auth.domain.model.AuthDomainModels.AuthSessionAggregate;
import com.lumira.auth.domain.model.AuthDomainModels.LoginChallenge;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthDomainModelsTest {

    @Test
    void sessionTouchEmitsRefreshEvent() {
        AuthSessionAggregate session = new AuthSessionAggregate("sid-1", 1L, 10L, Instant.parse("2026-01-01T00:00:00Z"));

        session.touch(Instant.parse("2026-01-01T00:01:00Z"));

        assertThat(session.domainEvents()).hasSize(1);
        assertThat(session.domainEvents().getFirst().eventType()).isEqualTo("AUTH_SESSION_REFRESHED");
        assertThat(session.domainEvents().getFirst().aggregateId()).isEqualTo("sid-1");
    }

    @Test
    void revokedSessionCannotBeTouched() {
        AuthSessionAggregate session = new AuthSessionAggregate("sid-1", 1L, 10L, Instant.now());

        session.revoke("logout");

        assertThatThrownBy(() -> session.touch(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void loginChallengeDetectsExpiration() {
        LoginChallenge challenge = new LoginChallenge("c-1", 1L, "LOGIN", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(challenge.isExpired(Instant.parse("2026-01-01T00:00:01Z"))).isTrue();
    }
}
