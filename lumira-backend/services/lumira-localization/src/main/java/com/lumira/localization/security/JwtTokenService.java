package com.lumira.localization.security;

import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenParser;
import org.springframework.stereotype.Service;

@Service("localizationJwtTokenService")
public class JwtTokenService {

    private final JwtTokenParser jwtTokenParser;

    public JwtTokenService(SecurityProperties securityProperties) {
        this.jwtTokenParser = new JwtTokenParser(securityProperties.getJwtSecret());
    }

    public JwtTokenClaims parseToken(String token) {
        return jwtTokenParser.parseToken(token);
    }
}
