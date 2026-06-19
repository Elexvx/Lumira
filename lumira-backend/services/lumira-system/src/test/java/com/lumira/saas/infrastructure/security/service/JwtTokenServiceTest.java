package com.lumira.saas.infrastructure.security.service;

import com.lumira.saas.infrastructure.security.SecurityProperties;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.model.TokenClaims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void shouldSupportUtf8Secret() {
        JwtTokenService jwtTokenService = buildJwtTokenService("saas_foundation_jwt_secret_for_dev_env_please_change_me_2026");

        TokenClaims tokenClaims = jwtTokenService.parseToken(jwtTokenService.generateAccessToken(buildSession()));

        assertEquals("session-1", tokenClaims.getSessionId());
        assertEquals(1L, tokenClaims.getUserId());
        assertEquals("tester", tokenClaims.getUsername());
        assertEquals(2, tokenClaims.getSessionVersion());
    }

    @Test
    void shouldSupportBase64Secret() {
        String rawSecret = "saas_foundation_jwt_secret_for_prod_env_change_me_2026";
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        JwtTokenService jwtTokenService = buildJwtTokenService(base64Secret);

        TokenClaims tokenClaims = jwtTokenService.parseToken(jwtTokenService.generateRefreshToken(buildSession(), "refresh-1"));

        assertEquals("session-1", tokenClaims.getSessionId());
        assertEquals(1L, tokenClaims.getUserId());
        assertEquals("refresh-1", tokenClaims.getTokenId());
    }

    @Test
    void shouldCalculateSessionTtlWithInstant() {
        JwtTokenService jwtTokenService = buildJwtTokenService("saas_foundation_jwt_secret_for_dev_env_please_change_me_2026");

        Duration sessionTtl = jwtTokenService.calculateSessionTtl(Instant.now().plusSeconds(5));

        assertTrue(sessionTtl.compareTo(Duration.ZERO) > 0);
        assertTrue(jwtTokenService.calculateSessionTtl(Instant.now().minusSeconds(1)).isZero());
        assertFalse(jwtTokenService.isExpired(Instant.now().plusSeconds(5)));
        assertTrue(jwtTokenService.isExpired(Instant.now().minusSeconds(1)));
    }

    @Test
    void shouldRejectTooShortSecret() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> buildJwtTokenService("too-short-secret")
        );

        assertEquals("JWT密钥长度不足", exception.getMessage());
    }

    @Test
    void shouldRejectBase64SecretWhenDecodedBytesTooShort() {
        String shortRawSecret = "short-secret-for-test";
        String base64Secret = Base64.getEncoder().encodeToString(shortRawSecret.getBytes(StandardCharsets.UTF_8));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> buildJwtTokenService(base64Secret)
        );

        assertEquals("JWT密钥长度不足", exception.getMessage());
    }

    private SecurityProperties buildSecurityProperties(String jwtSecret) {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setJwtSecret(jwtSecret);
        securityProperties.setIssuer("saas-foundation");
        securityProperties.setAccessTokenExpireSeconds(1800);
        securityProperties.setRefreshTokenExpireSeconds(604800);
        return securityProperties;
    }

    private JwtTokenService buildJwtTokenService(String jwtSecret) {
        SecurityProperties securityProperties = buildSecurityProperties(jwtSecret);
        SecuritySettingsService securitySettingsService = new SecuritySettingsService(null, securityProperties) {
            @Override
            public long getAccessTokenExpireSeconds() {
                return 1800L;
            }

            @Override
            public long getRefreshTokenExpireSeconds() {
                return 604800L;
            }

            @Override
            public long getIdleTimeoutSeconds() {
                return 1800L;
            }
        };
        return new JwtTokenService(securityProperties, securitySettingsService);
    }

    private AuthSession buildSession() {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(1L);
        session.setUsername("tester");
        session.setCurrentTenantId(10L);
        session.setSessionVersion(2);
        session.setLoginTime(Instant.now());
        session.setExpireTime(Instant.now().plusSeconds(604800));
        return session;
    }
}
