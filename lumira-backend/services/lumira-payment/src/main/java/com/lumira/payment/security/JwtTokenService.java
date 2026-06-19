package com.lumira.payment.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component("paymentJwtTokenService")
public class JwtTokenService {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_SESSION_VERSION = "sv";
    private static final String CLAIM_TOKEN_TYPE = "tt";

    private final SecretKey secretKey;

    public JwtTokenService(SecurityProperties securityProperties) {
        byte[] secretBytes = securityProperties.getJwtSecret() == null || securityProperties.getJwtSecret().isBlank()
                ? "payment-service-dev-secret-change-me-please-payment-service-dev-secret".getBytes(StandardCharsets.UTF_8)
                : normalizeSecret(securityProperties.getJwtSecret());
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public JwtTokenClaims parseToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            JwtTokenClaims tokenClaims = new JwtTokenClaims();
            tokenClaims.setSessionId(claims.get(CLAIM_SESSION_ID, String.class));
            tokenClaims.setUserId(claims.get(CLAIM_USER_ID, Long.class));
            tokenClaims.setUsername(claims.get(CLAIM_USERNAME, String.class));
            tokenClaims.setCurrentTenantId(claims.get(CLAIM_TENANT_ID, Long.class));
            tokenClaims.setSessionVersion(claims.get(CLAIM_SESSION_VERSION, Integer.class));
            tokenClaims.setTokenId(claims.getId());
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (tokenType != null) {
                tokenClaims.setTokenType(JwtTokenType.valueOf(tokenType));
            }
            return tokenClaims;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token无效或已过期");
        }
    }

    public String createToken(JwtTokenClaims claims) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofHours(1));
        return Jwts.builder()
                .id(claims.getTokenId())
                .claim(CLAIM_TOKEN_TYPE, claims.getTokenType() == null ? null : claims.getTokenType().name())
                .claim(CLAIM_SESSION_ID, claims.getSessionId())
                .claim(CLAIM_USER_ID, claims.getUserId())
                .claim(CLAIM_USERNAME, claims.getUsername())
                .claim(CLAIM_TENANT_ID, claims.getCurrentTenantId())
                .claim(CLAIM_SESSION_VERSION, claims.getSessionVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    private byte[] normalizeSecret(String secret) {
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (Exception ignored) {
            // Fall through to UTF-8 bytes.
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        return padded;
    }
}
