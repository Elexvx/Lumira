package com.lumira.payment.security;

import com.lumira.common.security.JwtSecretKeyFactory;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenParser;
import com.lumira.common.security.JwtTokenType;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentJwtTokenContractTest {

    private static final String SHARED_JWT_SECRET = "0123456789abcdef0123456789abcdef!auth-payment-contract";

    @Test
    void parsesEveryClaimInTheSharedAuthAccessTokenContract() {
        Instant now = Instant.now();
        String accessToken = Jwts.builder()
                .issuer("saas-auth")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1_800)))
                .id(UUID.randomUUID().toString())
                .claim(JwtTokenParser.CLAIM_SESSION_ID, "session-payment-contract")
                .claim(JwtTokenParser.CLAIM_USER_ID, 42L)
                .claim(JwtTokenParser.CLAIM_USER_UUID, "user-uuid-42")
                .claim(JwtTokenParser.CLAIM_USERNAME, "alice")
                .claim(JwtTokenParser.CLAIM_SIMULATED_ROLE_ID, 7L)
                .claim(JwtTokenParser.CLAIM_SESSION_VERSION, 3)
                .claim(JwtTokenParser.CLAIM_PERMISSIONS_VERSION, "permissions-v5")
                .claim(JwtTokenParser.CLAIM_TOKEN_TYPE, JwtTokenType.ACCESS.name())
                .signWith(JwtSecretKeyFactory.createHmacKey(SHARED_JWT_SECRET))
                .compact();

        SecurityProperties paymentProperties = new SecurityProperties();
        paymentProperties.setJwtSecret(SHARED_JWT_SECRET);
        JwtTokenClaims claims = new JwtTokenService(paymentProperties).parseToken(accessToken);

        assertThat(claims.getTokenType()).isEqualTo(JwtTokenType.ACCESS);
        assertThat(claims.getSessionId()).isEqualTo("session-payment-contract");
        assertThat(claims.getUserId()).isEqualTo(42L);
        assertThat(claims.getUserUuid()).isEqualTo("user-uuid-42");
        assertThat(claims.getUsername()).isEqualTo("alice");
        assertThat(claims.getSimulatedRoleId()).isEqualTo(7L);
        assertThat(claims.getSessionVersion()).isEqualTo(3);
        assertThat(claims.getPermissionsVersion()).isEqualTo("permissions-v5");
        assertThat(claims.getTokenId()).isNotBlank();
    }
}
