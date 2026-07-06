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
import java.util.regex.Pattern;

@Component
public class JwtTokenService {

    private static final int MAX_TOKEN_ID_LENGTH = 128;
    private static final Pattern SAFE_TOKEN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

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
        AuthSessionTrustValidator.requireTrustedSession(session);
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(getAccessTokenExpireSeconds());
        return Jwts.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .id(java.util.UUID.randomUUID().toString())
                .claim(JwtTokenParser.CLAIM_SESSION_ID, session.getSessionId())
                .claim(JwtTokenParser.CLAIM_USER_ID, session.getUserId())
                .claim(JwtTokenParser.CLAIM_USER_UUID, session.getUserUuid())
                .claim(JwtTokenParser.CLAIM_USERNAME, session.getUsername())
                .claim(JwtTokenParser.CLAIM_SIMULATED_ROLE_ID, session.getSimulatedRoleId())
                .claim(JwtTokenParser.CLAIM_SESSION_VERSION, session.getSessionVersion())
                .claim(JwtTokenParser.CLAIM_PERMISSIONS_VERSION, session.getPermissionsVersion())
                .claim(JwtTokenParser.CLAIM_TOKEN_TYPE, JwtTokenType.ACCESS.name())
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(AuthSession session, String refreshTokenId) {
        AuthSessionTrustValidator.requireTrustedSession(session);
        String trustedRefreshTokenId = requireTrustedTokenId(refreshTokenId);
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(getRefreshTokenExpireSeconds());
        return Jwts.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .id(trustedRefreshTokenId)
                .claim(JwtTokenParser.CLAIM_SESSION_ID, session.getSessionId())
                .claim(JwtTokenParser.CLAIM_USER_ID, session.getUserId())
                .claim(JwtTokenParser.CLAIM_USER_UUID, session.getUserUuid())
                .claim(JwtTokenParser.CLAIM_USERNAME, session.getUsername())
                .claim(JwtTokenParser.CLAIM_SIMULATED_ROLE_ID, session.getSimulatedRoleId())
                .claim(JwtTokenParser.CLAIM_SESSION_VERSION, session.getSessionVersion())
                .claim(JwtTokenParser.CLAIM_PERMISSIONS_VERSION, session.getPermissionsVersion())
                .claim(JwtTokenParser.CLAIM_TOKEN_TYPE, JwtTokenType.REFRESH.name())
                .signWith(secretKey)
                .compact();
    }

    public TokenClaims parseToken(String token) {
        try {
            return toTokenClaims(jwtTokenParser.parseToken(token));
        } catch (com.lumira.common.exception.BizException | IllegalArgumentException ex) {
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
        tokenClaims.setSessionId(AuthSessionTrustValidator.requireTrustedSessionId(claims.getSessionId()));
        if (claims.getUserId() == null || claims.getUserId() <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token鏃犳晥鎴栧凡杩囨湡");
        }
        tokenClaims.setUserId(claims.getUserId());
        tokenClaims.setUserUuid(requireTrustedUserUuid(claims.getUserUuid()));
        tokenClaims.setUsername(requireTrustedUsername(claims.getUsername()));
        tokenClaims.setSimulatedRoleId(normalizeSimulatedRoleId(claims.getSimulatedRoleId()));
        if (claims.getSessionVersion() == null || claims.getSessionVersion() < 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token鏃犳晥鎴栧凡杩囨湡");
        }
        tokenClaims.setSessionVersion(claims.getSessionVersion());
        tokenClaims.setPermissionsVersion(requireTrustedPermissionsVersion(claims.getPermissionsVersion()));
        tokenClaims.setTokenId(claims.getTokenId() == null ? null : requireTrustedTokenId(claims.getTokenId()));
        tokenClaims.setTokenType(claims.getTokenType() == null ? null : TokenType.valueOf(claims.getTokenType().name()));
        return tokenClaims;
    }

    private String requireTrustedUsername(String username) {
        if (username == null || username.trim().isEmpty() || username.trim().length() > 64) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token鏃犳晥鎴栧凡杩囨湡");
        }
        return username.trim();
    }

    private String requireTrustedUserUuid(String userUuid) {
        if (userUuid == null || userUuid.trim().isEmpty() || userUuid.trim().length() > 64) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token identity snapshot is incomplete");
        }
        return userUuid.trim();
    }

    private String requireTrustedPermissionsVersion(String permissionsVersion) {
        if (permissionsVersion == null || permissionsVersion.trim().isEmpty() || permissionsVersion.trim().length() > 128) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token permission snapshot is incomplete");
        }
        return permissionsVersion.trim();
    }

    private String requireTrustedTokenId(String tokenId) {
        if (tokenId == null) {
            throw new IllegalArgumentException("tokenId is required");
        }
        String normalized = tokenId.trim();
        if (normalized.length() > MAX_TOKEN_ID_LENGTH
                || !SAFE_TOKEN_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new IllegalArgumentException("tokenId is invalid");
        }
        return normalized;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        if (simulatedRoleId == null) {
            return null;
        }
        if (simulatedRoleId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token identity snapshot is incomplete");
        }
        return simulatedRoleId;
    }
}
