package com.lumira.auth.service;

import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.security.JwtTokenType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    private final SecuritySettingsService securitySettingsService = mock(SecuritySettingsService.class);
    private final JwtTokenService jwtTokenService = new JwtTokenService(securityProperties(), securitySettingsService);

    @Test
    void generateAccessTokenIncludesFullTrustedSessionSnapshot() {
        when(securitySettingsService.getAccessTokenExpireSeconds()).thenReturn(1800L);

        var claims = jwtTokenService.parseToken(jwtTokenService.generateAccessToken(trustedSession()));

        assertThat(claims.getTokenType()).isEqualTo(JwtTokenType.ACCESS);
        assertThat(claims.getSessionId()).isEqualTo("session-1");
        assertThat(claims.getUserId()).isEqualTo(42L);
        assertThat(claims.getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(claims.getUsername()).isEqualTo("alice");
        assertThat(claims.getSimulatedRoleId()).isEqualTo(9L);
        assertThat(claims.getSessionVersion()).isEqualTo(3);
        assertThat(claims.getPermissionsVersion()).isEqualTo("permissions-3");
    }

    @Test
    void generateAccessTokenRejectsSessionWithoutUserUuid() {
        AuthSession session = trustedSession();
        session.setUserUuid(" ");

        assertThatThrownBy(() -> jwtTokenService.generateAccessToken(session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userUuid");
    }

    @Test
    void generateRefreshTokenRejectsSessionWithoutPermissionsVersion() {
        AuthSession session = trustedSession();
        session.setPermissionsVersion(null);

        assertThatThrownBy(() -> jwtTokenService.generateRefreshToken(session, "refresh-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissionsVersion");
    }

    private static AuthSecurityProperties securityProperties() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setJwtSecret("0123456789abcdef0123456789abcdef!auth");
        properties.setIssuer("lumira-auth-test");
        return properties;
    }

    private static AuthSession trustedSession() {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(42L);
        session.setUserUuid("user-uuid-42");
        session.setUsername("alice");
        session.setSimulatedRoleId(9L);
        session.setSessionVersion(3);
        session.setPermissionsVersion("permissions-3");
        return session;
    }
}
