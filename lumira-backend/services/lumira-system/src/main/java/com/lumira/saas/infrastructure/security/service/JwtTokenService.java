package com.lumira.saas.infrastructure.security.service;

import com.lumira.common.security.JwtSecretKeyFactory;
import com.lumira.common.security.JwtTokenParser;
import com.lumira.common.security.JwtTokenType;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.security.model.AuthSession;
import com.lumira.saas.infrastructure.security.model.TokenClaims;
import com.lumira.saas.infrastructure.security.SecurityProperties;
import com.lumira.saas.infrastructure.security.model.TokenType;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenService {

    private final SecurityProperties securityProperties;
    private final SecuritySettingsService securitySettingsService;
    private final SecretKey secretKey;
    private final JwtTokenParser jwtTokenParser;

    public JwtTokenService(SecurityProperties securityProperties, SecuritySettingsService securitySettingsService) {
        this.securityProperties = securityProperties;
        this.securitySettingsService = securitySettingsService;
        this.secretKey = JwtSecretKeyFactory.createHmacKey(securityProperties.getJwtSecret());
        this.jwtTokenParser = new JwtTokenParser(secretKey);
    }

    public String generateAccessToken(AuthSession session) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(getAccessTokenExpireSeconds());
        return Jwts.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .id(java.util.UUID.randomUUID().toString())
                .claim(JwtTokenParser.CLAIM_SESSION_ID, session.getSessionId())
                .claim(JwtTokenParser.CLAIM_USER_ID, session.getUserId())
                .claim(JwtTokenParser.CLAIM_USERNAME, session.getUsername())
                .claim(JwtTokenParser.CLAIM_TENANT_ID, session.getCurrentTenantId())
                .claim(JwtTokenParser.CLAIM_SESSION_VERSION, session.getSessionVersion())
                .claim(JwtTokenParser.CLAIM_TOKEN_TYPE, JwtTokenType.ACCESS.name())
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
                .claim(JwtTokenParser.CLAIM_SESSION_ID, session.getSessionId())
                .claim(JwtTokenParser.CLAIM_USER_ID, session.getUserId())
                .claim(JwtTokenParser.CLAIM_USERNAME, session.getUsername())
                .claim(JwtTokenParser.CLAIM_SESSION_VERSION, session.getSessionVersion())
                .claim(JwtTokenParser.CLAIM_TOKEN_TYPE, JwtTokenType.REFRESH.name())
                .signWith(secretKey)
                .compact();
    }

    public TokenClaims parseToken(String token) {
        try {
            return toTokenClaims(jwtTokenParser.parseToken(token));
        } catch (com.lumira.common.exception.BizException ex) {
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

    public Instant createRefreshTokenExpireAt() {
        return Instant.now().plusSeconds(getRefreshTokenExpireSeconds());
    }

    public Duration getRefreshTokenTtl() {
        return Duration.ofSeconds(getRefreshTokenExpireSeconds());
    }

    public boolean isExpired(Instant expireAt) {
        return expireAt == null || !expireAt.isAfter(Instant.now());
    }

    public Duration calculateSessionTtl(Instant expireAt) {
        if (expireAt == null) {
            return Duration.ZERO;
        }
        Duration ttl = Duration.between(Instant.now(), expireAt);
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }

    private TokenClaims toTokenClaims(com.lumira.common.security.JwtTokenClaims claims) {
        TokenClaims tokenClaims = new TokenClaims();
        tokenClaims.setSessionId(claims.getSessionId());
        tokenClaims.setUserId(claims.getUserId());
        tokenClaims.setUsername(claims.getUsername());
        tokenClaims.setCurrentTenantId(claims.getCurrentTenantId());
        tokenClaims.setSessionVersion(claims.getSessionVersion());
        tokenClaims.setTokenId(claims.getTokenId());
        tokenClaims.setTokenType(claims.getTokenType() == null ? null : TokenType.valueOf(claims.getTokenType().name()));
        return tokenClaims;
    }
}
