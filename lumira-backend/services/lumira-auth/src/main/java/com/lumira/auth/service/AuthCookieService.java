package com.lumira.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthCookieService {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final SecuritySettingsService securitySettingsService;

    public AuthCookieService(SecuritySettingsService securitySettingsService) {
        this.securitySettingsService = securitySettingsService;
    }

    public void writeRefreshToken(HttpServletResponse response, String refreshToken) {
        if (response == null || !StringUtils.hasText(refreshToken)) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(securitySettingsService.getRefreshTokenExpireSeconds())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public String readRefreshToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
