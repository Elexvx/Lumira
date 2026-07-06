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
    public static final String CLAIM_USER_UUID = "uuid";
    public static final String CLAIM_USERNAME = "uname";
    public static final String CLAIM_SIMULATED_ROLE_ID = "sri";
    public static final String CLAIM_SESSION_VERSION = "sv";
    public static final String CLAIM_PERMISSIONS_VERSION = "pv";
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
            tokenClaims.setUserUuid(claims.get(CLAIM_USER_UUID, String.class));
            tokenClaims.setUsername(claims.get(CLAIM_USERNAME, String.class));
            tokenClaims.setSimulatedRoleId(claims.get(CLAIM_SIMULATED_ROLE_ID, Long.class));
            tokenClaims.setSessionVersion(claims.get(CLAIM_SESSION_VERSION, Integer.class));
            tokenClaims.setPermissionsVersion(claims.get(CLAIM_PERMISSIONS_VERSION, String.class));
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
