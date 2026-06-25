package com.lumira.auth.controller;

import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasskeyAuthenticationCompleteRequest;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.WechatLoginRequest;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthCookieService;
import com.lumira.auth.service.PasskeyAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthAppService authAppService;
    private PasskeyAuthService passkeyAuthService;
    private AuthCookieService authCookieService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authAppService = mock(AuthAppService.class);
        passkeyAuthService = mock(PasskeyAuthService.class);
        authCookieService = mock(AuthCookieService.class);
        controller = new AuthController(authAppService, passkeyAuthService, authCookieService);
    }

    @Test
    void loginCodeCompleteShouldWriteRefreshCookieAndStripResponseBodyToken() {
        LoginCodeCompleteRequest request = new LoginCodeCompleteRequest("challenge-1", "123456");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        LoginResponseDTO loginResponse = loginResponse();
        when(authAppService.completeLoginCodeLogin(request, httpRequest)).thenReturn(loginResponse);

        var response = controller.loginCodeComplete(request, httpRequest, httpResponse);

        assertThat(response.getData().getRefreshToken()).isNull();
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh-token");
        verify(authAppService).completeLoginCodeLogin(request, httpRequest);
    }

    @Test
    void wechatLoginShouldWriteRefreshCookieAndStripResponseBodyToken() {
        WechatLoginRequest request = new WechatLoginRequest("wechat-code", "wechat-state");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        LoginResponseDTO loginResponse = loginResponse();
        when(authAppService.wechatLogin(request, httpRequest)).thenReturn(loginResponse);

        var response = controller.wechatLogin(request, httpRequest, httpResponse);

        assertThat(response.getData().getRefreshToken()).isNull();
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh-token");
        verify(authAppService).wechatLogin(request, httpRequest);
    }

    @Test
    void passkeyAuthenticationCompleteShouldWriteRefreshCookieAndStripResponseBodyToken() {
        PasskeyAuthenticationCompleteRequest request = new PasskeyAuthenticationCompleteRequest(
                "challenge-1",
                "credential-id",
                "raw-id",
                "public-key",
                new PasskeyAuthenticationCompleteRequest.Response("client-data", "auth-data", "signature", null),
                null
        );
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        LoginResponseDTO loginResponse = loginResponse();
        when(passkeyAuthService.completeAuthentication(request, httpRequest)).thenReturn(loginResponse);

        var response = controller.passkeyAuthenticationComplete(request, httpRequest, httpResponse);

        assertThat(response.getData().getRefreshToken()).isNull();
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh-token");
        verify(passkeyAuthService).completeAuthentication(request, httpRequest);
    }

    @Test
    void completeSecondFactorShouldWriteRefreshCookieAndStripResponseBodyToken() {
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("totp", "challenge-1", "123456");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        LoginResponseDTO loginResponse = loginResponse();
        when(authAppService.completeSecondFactorLogin(request, httpRequest)).thenReturn(loginResponse);

        var response = controller.completeSecondFactor(request, httpRequest, httpResponse);

        assertThat(response.getData().getRefreshToken()).isNull();
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh-token");
        verify(authAppService).completeSecondFactorLogin(request, httpRequest);
    }

    @Test
    void authenticatedReadEndpointsShouldReissueCsrfCookieForLegacySessions() {
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        controller.keepalive(httpResponse);

        verify(authCookieService).writeCsrfToken(httpResponse);
    }

    private LoginResponseDTO loginResponse() {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setAccessToken("access-token");
        response.setRefreshToken("refresh-token");
        response.setTokenType("Bearer");
        response.setExpiresIn(7200L);
        return response;
    }
}
