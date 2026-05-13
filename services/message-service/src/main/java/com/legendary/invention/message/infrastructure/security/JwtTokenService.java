package com.legendary.invention.message.infrastructure.security;

import com.legendary.invention.common.security.JwtTokenClaims;
import com.legendary.invention.common.security.JwtTokenParser;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtTokenParser jwtTokenParser;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.jwtTokenParser = new JwtTokenParser(securityProperties.getJwtSecret());
    }

    public JwtTokenClaims parseToken(String token) {
        return jwtTokenParser.parseToken(token);
    }
}
