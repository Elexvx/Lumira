package com.lumira.auth.controller;

import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasskeyAuthenticationCompleteRequest;
import com.lumira.api.auth.PasskeyCredentialRenameRequest;
import com.lumira.api.auth.PasskeyOperationVerificationRequest;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.SimulatedRoleSwitchRequest;
import com.lumira.api.auth.SimulatedRoleSwitchResponseDTO;
import com.lumira.api.auth.VerificationBindRequest;
import com.lumira.api.auth.WechatLoginRequest;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthCookieService;
import com.lumira.auth.service.PasskeyAuthService;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void passkeyRegistrationOptionsShouldDelegateVerificationRequest() {
        PasskeyOperationVerificationRequest request = new PasskeyOperationVerificationRequest("Password!23", null, null, null);
        when(passkeyAuthService.registrationOptions(request)).thenReturn(new com.lumira.api.auth.PasskeyOptionsDTO("challenge-1", java.util.Map.of()));

        var response = controller.passkeyRegistrationOptions(request);

        assertThat(response.getData().challengeId()).isEqualTo("challenge-1");
        verify(passkeyAuthService).registrationOptions(request);
    }

    @Test
    void renamePasskeyCredentialShouldDelegateVerificationRequest() {
        PasskeyCredentialRenameRequest request = new PasskeyCredentialRenameRequest("Laptop", null, "totp", "challenge-1", "123456");
        when(passkeyAuthService.renameCredential(9L, request)).thenReturn(new com.lumira.api.system.PasskeyCredentialDTO(9L, "Laptop", null, null));

        var response = controller.renamePasskeyCredential(9L, request);

        assertThat(response.getData().label()).isEqualTo("Laptop");
        verify(passkeyAuthService).renameCredential(9L, request);
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
    void switchSimulatedRoleShouldWriteRefreshCookieAndStripResponseBodyToken() {
        SimulatedRoleSwitchRequest request = new SimulatedRoleSwitchRequest(9L);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        SimulatedRoleSwitchResponseDTO response = new SimulatedRoleSwitchResponseDTO(
                null,
                "access-role",
                "refresh-role",
                "Bearer",
                1800L
        );
        when(authAppService.switchSimulatedRole(request)).thenReturn(response);

        var apiResponse = controller.switchSimulatedRole(request, httpResponse);

        assertThat(apiResponse.getData().refreshToken()).isNull();
        assertThat(apiResponse.getData().accessToken()).isEqualTo("access-role");
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh-role");
        verify(authAppService).switchSimulatedRole(request);
    }

    @Test
    void verificationUnbindShouldDelegateVerifiedChallengeRequest() {
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("totp", "challenge-1", "123456");
        when(authAppService.verificationUnbind("totp", request)).thenReturn(true);

        var response = controller.verificationUnbind("totp", request);

        assertThat(response.getData()).isTrue();
        verify(authAppService).verificationUnbind("totp", request);
    }

    @Test
    void verificationBindShouldDelegateVerificationRequest() {
        VerificationBindRequest request = new VerificationBindRequest(null, "email", "challenge-1", "123456");
        com.lumira.api.system.VerificationBindingChallengeDTO challenge = new com.lumira.api.system.VerificationBindingChallengeDTO();
        challenge.setFactorCode("totp");
        challenge.setFactorName("2FA");
        challenge.setChallengeId("challenge-2");
        challenge.setMaskedContact("***");
        when(authAppService.verificationBind("totp", request)).thenReturn(challenge);

        var response = controller.verificationBind("totp", request);

        assertThat(response.getData().getChallengeId()).isEqualTo("challenge-2");
        verify(authAppService).verificationBind("totp", request);
    }

    @Test
    void deletePasskeyCredentialShouldDelegateVerificationRequest() {
        PasskeyOperationVerificationRequest request = new PasskeyOperationVerificationRequest(null, "totp", "challenge-1", "123456");
        when(passkeyAuthService.deleteCredential(9L, request)).thenReturn(true);

        var response = controller.deletePasskeyCredential(9L, request);

        assertThat(response.getData()).isTrue();
        verify(passkeyAuthService).deleteCredential(9L, request);
    }

    @Test
    void authenticatedReadEndpointsShouldReissueCsrfCookieForLegacySessions() {
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        when(authAppService.keepalive()).thenReturn(true);

        var response = controller.keepalive(httpResponse);

        assertThat(response.getData()).isTrue();
        verify(authAppService).keepalive();
        verify(authCookieService).writeCsrfToken(httpResponse);
    }

    @Test
    void keepaliveShouldRejectUntrustedUserBeforeWritingCsrfCookie() {
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        when(authAppService.keepalive()).thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "User context is required"));

        assertThatThrownBy(() -> controller.keepalive(httpResponse))
                .isInstanceOf(BizException.class);

        verify(authCookieService, never()).writeCsrfToken(httpResponse);
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
