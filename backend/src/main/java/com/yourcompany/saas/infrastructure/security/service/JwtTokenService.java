package com.yourcompany.saas.infrastructure.security.service;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.SecurityProperties;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
import com.yourcompany.saas.infrastructure.security.model.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenService {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_SESSION_VERSION = "sv";
    private static final String CLAIM_TOKEN_TYPE = "tt";

    private final SecurityProperties securityProperties;
    private final SecretKey secretKey;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.secretKey = Keys.hmacShaKeyFor(securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(AuthSession session) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(securityProperties.getAccessTokenExpireSeconds());
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
        Instant expireAt = now.plusSeconds(securityProperties.getRefreshTokenExpireSeconds());
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
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toTokenClaims(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token无效或已过期");
        }
    }

    public long getAccessTokenExpireSeconds() {
        return securityProperties.getAccessTokenExpireSeconds();
    }

    public long getRefreshTokenExpireSeconds() {
        return securityProperties.getRefreshTokenExpireSeconds();
    }

    private TokenClaims toTokenClaims(Claims claims) {
        TokenClaims tokenClaims = new TokenClaims();
        tokenClaims.setSessionId(claims.get(CLAIM_SESSION_ID, String.class));
        tokenClaims.setUserId(claims.get(CLAIM_USER_ID, Long.class));
        tokenClaims.setUsername(claims.get(CLAIM_USERNAME, String.class));
        tokenClaims.setCurrentTenantId(claims.get(CLAIM_TENANT_ID, Long.class));
        tokenClaims.setSessionVersion(claims.get(CLAIM_SESSION_VERSION, Integer.class));
        tokenClaims.setTokenId(claims.getId());
        tokenClaims.setTokenType(TokenType.valueOf(claims.get(CLAIM_TOKEN_TYPE, String.class)));
        return tokenClaims;
    }
}
