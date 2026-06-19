package com.lumira.auth.service;

import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.security.JwtSecretKeyFactory;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenParser;
import com.lumira.common.security.JwtTokenType;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service("authJwtTokenService")
public class JwtTokenService {

    private final AuthSecurityProperties securityProperties;
    private final SecuritySettingsService securitySettingsService;
    private final SecretKey secretKey;
    private final JwtTokenParser jwtTokenParser;

    public JwtTokenService(AuthSecurityProperties securityProperties, SecuritySettingsService securitySettingsService) {
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

    public JwtTokenClaims parseToken(String token) {
        return jwtTokenParser.parseToken(token);
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

}
