package com.lumira.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AuthCookieService {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_TOKEN_COOKIE = "csrf_token";
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        response.addHeader("Set-Cookie", buildCsrfCookie(generateCsrfToken(), securitySettingsService.getRefreshTokenExpireSeconds()).toString());
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
        response.addHeader("Set-Cookie", buildCsrfCookie("", 0).toString());
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

    public boolean hasRefreshTokenCookie(HttpServletRequest request) {
        return StringUtils.hasText(readRefreshToken(request));
    }

    public void validateCsrfIfCookieAuth(HttpServletRequest request) {
        if (!hasRefreshTokenCookie(request)) {
            return;
        }
        String cookieToken = readCookie(request, CSRF_TOKEN_COOKIE);
        String headerToken = request == null ? null : request.getHeader(CSRF_TOKEN_HEADER);
        if (!StringUtils.hasText(cookieToken) || !StringUtils.hasText(headerToken) || !cookieToken.equals(headerToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "CSRF validation failed", "登录状态已失效，请重新登录");
        }
    }

    private ResponseCookie buildCsrfCookie(String csrfToken, long maxAgeSeconds) {
        return ResponseCookie.from(CSRF_TOKEN_COOKIE, csrfToken)
                .httpOnly(false)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String generateCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
