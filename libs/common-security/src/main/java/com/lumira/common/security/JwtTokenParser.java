package com.lumira.common.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;

public class JwtTokenParser {

    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_USERNAME = "uname";
    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_SESSION_VERSION = "sv";
    public static final String CLAIM_TOKEN_TYPE = "tt";

    private final SecretKey secretKey;

    public JwtTokenParser(String jwtSecret) {
        this(JwtSecretKeyFactory.createHmacKey(jwtSecret));
    }

    public JwtTokenParser(SecretKey secretKey) {
        this.secretKey = secretKey;
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
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token无效或已过期");
        }
    }
}
