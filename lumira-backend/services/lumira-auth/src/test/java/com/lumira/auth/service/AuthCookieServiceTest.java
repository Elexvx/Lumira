package com.lumira.auth.service;

import com.lumira.common.exception.BizException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthCookieServiceTest {

    private SecuritySettingsService securitySettingsService;
    private AuthCookieService authCookieService;

    @BeforeEach
    void setUp() {
        securitySettingsService = mock(SecuritySettingsService.class);
        when(securitySettingsService.getRefreshTokenExpireSeconds()).thenReturn(604800L);
        authCookieService = new AuthCookieService(securitySettingsService);
    }

    @Test
    void writeRefreshTokenShouldExposeCsrfCookieToFrontendRoutesAndClearLegacyApiCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authCookieService.writeRefreshToken(response, "refresh-token");

        List<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).anySatisfy(header -> {
            assertThat(header).contains("refresh_token=refresh-token");
            assertThat(header).contains("Path=/api");
            assertThat(header).contains("HttpOnly");
            assertThat(header).contains("Secure");
            assertThat(header).contains("SameSite=Lax");
        });
        assertThat(setCookieHeaders).anySatisfy(header -> {
            assertThat(header).startsWith("csrf_token=");
            assertThat(header).contains("Path=/;");
            assertThat(header).doesNotContain("Max-Age=0");
            assertThat(header).doesNotContain("HttpOnly");
            assertThat(header).contains("Secure");
            assertThat(header).contains("SameSite=Lax");
        });
        assertThat(setCookieHeaders).anySatisfy(header -> {
            assertThat(header).startsWith("csrf_token=");
            assertThat(header).contains("Path=/api");
            assertThat(header).contains("Max-Age=0");
        });
    }

    @Test
    void validateCsrfShouldAcceptHeaderMatchingAnyDuplicateCsrfCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "refresh-token"),
                new Cookie(AuthCookieService.CSRF_TOKEN_COOKIE, "legacy-api-token"),
                new Cookie(AuthCookieService.CSRF_TOKEN_COOKIE, "root-token")
        );
        request.addHeader(AuthCookieService.CSRF_TOKEN_HEADER, "root-token");

        assertThatCode(() -> authCookieService.validateCsrfIfCookieAuth(request)).doesNotThrowAnyException();
    }

    @Test
    void validateCsrfShouldRejectCookieAuthenticatedRefreshWithoutMatchingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "refresh-token"),
                new Cookie(AuthCookieService.CSRF_TOKEN_COOKIE, "root-token")
        );
        request.addHeader(AuthCookieService.CSRF_TOKEN_HEADER, "other-token");

        assertThatThrownBy(() -> authCookieService.validateCsrfIfCookieAuth(request))
                .isInstanceOf(BizException.class);
    }
}
