package com.lumira.common.security;

import com.lumira.common.exception.BizException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenParserTest {

    private static final String PLAIN_SECRET = "0123456789abcdef0123456789abcdef!plain";

    @Test
    void parsesTokenSignedWithPlainSecret() {
        JwtTokenParser parser = new JwtTokenParser(PLAIN_SECRET);

        JwtTokenClaims claims = parser.parseToken(buildToken(PLAIN_SECRET, Instant.now().plusSeconds(60)));

        assertThat(claims.getSessionId()).isEqualTo("session-1");
        assertThat(claims.getUserId()).isEqualTo(100L);
        assertThat(claims.getUsername()).isEqualTo("alice");
        assertThat(claims.getCurrentTenantId()).isEqualTo(1001L);
        assertThat(claims.getSessionVersion()).isEqualTo(3);
        assertThat(claims.getTokenId()).isEqualTo("token-1");
        assertThat(claims.getTokenType()).isEqualTo(JwtTokenType.ACCESS);
    }

    @Test
    void parsesTokenSignedWithBase64Secret() {
        String encodedSecret = Base64.getEncoder().encodeToString(PLAIN_SECRET.getBytes());
        JwtTokenParser parser = new JwtTokenParser(encodedSecret);

        JwtTokenClaims claims = parser.parseToken(buildToken(encodedSecret, Instant.now().plusSeconds(60)));

        assertThat(claims.getSessionId()).isEqualTo("session-1");
    }

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtTokenParser(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT密钥未配置");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtTokenParser("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT密钥长度不足");
    }

    @Test
    void rejectsExpiredOrInvalidToken() {
        JwtTokenParser parser = new JwtTokenParser(PLAIN_SECRET);

        assertThatThrownBy(() -> parser.parseToken(buildToken(PLAIN_SECRET, Instant.now().minusSeconds(1))))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> parser.parseToken("not-a-token"))
                .isInstanceOf(BizException.class);
    }

    private String buildToken(String secret, Instant expireAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("lumira")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .id("token-1")
                .claim(JwtTokenParser.CLAIM_SESSION_ID, "session-1")
                .claim(JwtTokenParser.CLAIM_USER_ID, 100L)
                .claim(JwtTokenParser.CLAIM_USERNAME, "alice")
                .claim(JwtTokenParser.CLAIM_TENANT_ID, 1001L)
                .claim(JwtTokenParser.CLAIM_SESSION_VERSION, 3)
                .claim(JwtTokenParser.CLAIM_TOKEN_TYPE, JwtTokenType.ACCESS.name())
                .signWith(JwtSecretKeyFactory.createHmacKey(secret))
                .compact();
    }
}
