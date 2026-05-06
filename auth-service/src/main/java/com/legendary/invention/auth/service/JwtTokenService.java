package com.legendary.invention.auth.service;

import com.legendary.invention.auth.config.SecurityProperties;
import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.auth.model.TokenClaims;
import com.legendary.invention.auth.model.TokenType;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_SESSION_VERSION = "sv";
    private static final String CLAIM_TOKEN_TYPE = "tt";

    private final SecurityProperties securityProperties;
    private final SecuritySettingsService securitySettingsService;
    private final SecretKey secretKey;

    public JwtTokenService(SecurityProperties securityProperties, SecuritySettingsService securitySettingsService) {
        this.securityProperties = securityProperties;
        this.securitySettingsService = securitySettingsService;
        this.secretKey = buildSecretKey(securityProperties.getJwtSecret());
    }

    public String generateAccessToken(AuthSession session) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(getAccessTokenExpireSeconds());
        return Jwts.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .id(java.util.UUID.randomUUID().toString())
                .claim(CLAIM_SESSION_ID, session.getSessionId())
                .claim(CLAIM_USER_ID, session.getUserId())
                .claim(CLAIM_USERNAME, session.getUsername())
                .claim(CLAIM_TENANT_ID, session.getCurrentTenantId())
                .claim(CLAIM_SESSION_VERSION, session.getSessionVersion())
                .claim(CLAIM_TOKEN_TYPE, TokenType.ACCESS.name())
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(AuthSession session, String refreshTokenId) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(getRefreshTokenExpireSeconds());
        return Jwts.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .id(refreshTokenId)
                .claim(CLAIM_SESSION_ID, session.getSessionId())
                .claim(CLAIM_USER_ID, session.getUserId())
                .claim(CLAIM_USERNAME, session.getUsername())
                .claim(CLAIM_SESSION_VERSION, session.getSessionVersion())
                .claim(CLAIM_TOKEN_TYPE, TokenType.REFRESH.name())
                .signWith(secretKey)
                .compact();
    }

    public TokenClaims parseToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            TokenClaims tokenClaims = new TokenClaims();
            tokenClaims.setSessionId(claims.get(CLAIM_SESSION_ID, String.class));
            tokenClaims.setUserId(claims.get(CLAIM_USER_ID, Long.class));
            tokenClaims.setUsername(claims.get(CLAIM_USERNAME, String.class));
            tokenClaims.setCurrentTenantId(claims.get(CLAIM_TENANT_ID, Long.class));
            tokenClaims.setSessionVersion(claims.get(CLAIM_SESSION_VERSION, Integer.class));
            tokenClaims.setTokenId(claims.getId());
            tokenClaims.setTokenType(TokenType.valueOf(claims.get(CLAIM_TOKEN_TYPE, String.class)));
            return tokenClaims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token无效或已过期");
        }
    }

    public long getAccessTokenExpireSeconds() {
        return securitySettingsService.getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return securitySettingsService.getRefreshTokenExpireSeconds();
    }

    public long getIdleTimeoutSeconds() {
        return securitySettingsService.getIdleTimeoutSeconds();
    }

    public Duration getRefreshTokenTtl() {
        return Duration.ofSeconds(getRefreshTokenExpireSeconds());
    }

    private SecretKey buildSecretKey(String jwtSecret) {
        byte[] secretBytes = decodeSecret(jwtSecret);
        if (secretBytes.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalStateException("JWT密钥长度不足");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    private byte[] decodeSecret(String jwtSecret) {
        String normalizedJwtSecret = jwtSecret == null ? "" : jwtSecret.trim();
        if (normalizedJwtSecret.isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置");
        }
        if (normalizedJwtSecret.length() % 4 == 0 && normalizedJwtSecret.matches("^[A-Za-z0-9+/]+={0,2}$")) {
            try {
                return Base64.getDecoder().decode(normalizedJwtSecret);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return normalizedJwtSecret.getBytes(StandardCharsets.UTF_8);
    }
}
