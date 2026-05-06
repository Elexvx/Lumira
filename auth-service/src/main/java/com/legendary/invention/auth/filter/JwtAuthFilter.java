package com.legendary.invention.auth.filter;

import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.auth.model.TokenClaims;
import com.legendary.invention.auth.model.TokenType;
import com.legendary.invention.auth.service.AuthSessionStore;
import com.legendary.invention.auth.service.JwtTokenService;
import com.legendary.invention.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthSessionStore authSessionStore;

    public JwtAuthFilter(JwtTokenService jwtTokenService, AuthSessionStore authSessionStore) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionStore = authSessionStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                TokenClaims claims = jwtTokenService.parseToken(authorization.substring(7));
                if (claims.getTokenType() == TokenType.ACCESS) {
                    AuthSession session = authSessionStore.findBySessionId(claims.getSessionId()).orElse(null);
                    if (session != null) {
                        CurrentUser currentUser = new CurrentUser(
                                claims.getUserId(),
                                claims.getUsername(),
                                claims.getCurrentTenantId(),
                                claims.getSessionId(),
                                claims.getSessionVersion(),
                                true,
                                Set.of()
                        );
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(currentUser, authorization, java.util.List.of());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
