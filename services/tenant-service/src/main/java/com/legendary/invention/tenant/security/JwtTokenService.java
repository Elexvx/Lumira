package com.legendary.invention.tenant.security;

import com.legendary.invention.common.security.JwtTokenClaims;
import com.legendary.invention.common.security.JwtTokenParser;
import com.legendary.invention.tenant.config.SecurityProperties;
import com.legendary.invention.tenant.model.AuthSessionClaims;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtTokenParser jwtTokenParser;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.jwtTokenParser = new JwtTokenParser(securityProperties.getJwtSecret());
    }

    public AuthSessionClaims parseAccessToken(String token) {
        try {
            JwtTokenClaims claims = jwtTokenParser.parseToken(token);
            AuthSessionClaims result = new AuthSessionClaims();
            result.setSessionId(claims.getSessionId());
            result.setUserId(claims.getUserId());
            result.setUsername(claims.getUsername());
            result.setCurrentTenantId(claims.getCurrentTenantId());
            result.setSessionVersion(claims.getSessionVersion());
            return result;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
