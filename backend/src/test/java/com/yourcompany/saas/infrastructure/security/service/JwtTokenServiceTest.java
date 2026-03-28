package com.yourcompany.saas.infrastructure.security.service;

import com.yourcompany.saas.infrastructure.security.SecurityProperties;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
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
        JwtTokenService jwtTokenService = new JwtTokenService(buildSecurityProperties("saas_foundation_jwt_secret_for_dev_env_please_change_me_2026"));

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
        JwtTokenService jwtTokenService = new JwtTokenService(buildSecurityProperties(base64Secret));

        TokenClaims tokenClaims = jwtTokenService.parseToken(jwtTokenService.generateRefreshToken(buildSession(), "refresh-1"));

        assertEquals("session-1", tokenClaims.getSessionId());
        assertEquals(1L, tokenClaims.getUserId());
        assertEquals("refresh-1", tokenClaims.getTokenId());
    }

    @Test
    void shouldCalculateSessionTtlWithInstant() {
        JwtTokenService jwtTokenService = new JwtTokenService(buildSecurityProperties("saas_foundation_jwt_secret_for_dev_env_please_change_me_2026"));

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
                () -> new JwtTokenService(buildSecurityProperties("too-short-secret"))
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
