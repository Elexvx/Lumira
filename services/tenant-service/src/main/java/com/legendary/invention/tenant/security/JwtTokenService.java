package com.legendary.invention.tenant.security;

import com.legendary.invention.tenant.config.SecurityProperties;
import com.legendary.invention.tenant.model.AuthSessionClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class JwtTokenService {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_TENANT_ID = "tid";
    private static final String CLAIM_SESSION_VERSION = "sv";

    private final SecretKey secretKey;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.secretKey = Keys.hmacShaKeyFor(decodeSecret(securityProperties.getJwtSecret()));
    }

    public AuthSessionClaims parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            AuthSessionClaims result = new AuthSessionClaims();
            result.setSessionId(claims.get(CLAIM_SESSION_ID, String.class));
            result.setUserId(claims.get(CLAIM_USER_ID, Long.class));
            result.setUsername(claims.get(CLAIM_USERNAME, String.class));
            result.setCurrentTenantId(claims.get(CLAIM_TENANT_ID, Long.class));
            result.setSessionVersion(claims.get(CLAIM_SESSION_VERSION, Integer.class));
            return result;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
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
