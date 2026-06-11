package com.lumira.auth.service;

import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.WechatLoginRequest;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAppServiceWechatSecondFactorTest {

    @Test
    void wechatLoginShouldReturnPendingSecondFactorBeforeIssuingTokens() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        WechatLoginService wechatLoginService = mock(WechatLoginService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        SecuritySettingsService securitySettingsService = new SecuritySettingsService(securityProperties, systemInternalApi);
        AuthAppService service = new AuthAppService(
                systemInternalApi,
                mock(LoginEncryptionService.class),
                mock(LoginProtectionService.class),
                authSessionStore,
                jwtTokenService,
                mock(PasswordEncoder.class),
                mock(SecurityContextFacade.class),
                clientIpResolver,
                wechatLoginService,
                securityProperties,
                securitySettingsService
        );
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "wechat-user",
                "hash",
                "ENABLED",
                null,
                null,
                "Wechat User",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "zh-CN"
        );
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("dashboard:view"), List.of(1L), null, List.of(), List.of(), List.of(), "/dashboard/home");
        VerificationProviderDTO provider = new VerificationProviderDTO();
        provider.setFactorCode("totp");
        provider.setFactorName("2FA");
        provider.setEnabled(true);
        provider.setBound(true);
        provider.setMaskedContact("Authenticator app");
        provider.setPromptMessage("Enter your 2FA code");
        VerificationChallengeDTO challenge = new VerificationChallengeDTO();
        challenge.setFactorCode("totp");
        challenge.setFactorName("2FA");
        challenge.setChallengeId("challenge-1");
        challenge.setMaskedContact("Authenticator app");
        challenge.setPromptMessage("Enter your 2FA code");

        when(wechatLoginService.exchangeCode("code", "state"))
                .thenReturn(new WechatLoginService.WechatOAuthUser("openid", "unionid", "snsapi_login"));
        when(systemInternalApi.resolveWechatLoginUser(any())).thenReturn(user);
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, user.userId())).thenReturn(snapshot);
        when(systemInternalApi.listVerificationProviders(PlatformConstants.PLATFORM_TENANT_ID, user.userId())).thenReturn(List.of(provider));
        when(systemInternalApi.verificationChallenge(PlatformConstants.PLATFORM_TENANT_ID, user.userId(), "totp")).thenReturn(challenge);
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("JUnit");

        LoginResponseDTO response = service.wechatLogin(new WechatLoginRequest("code", "state"), request);

        assertTrue(response.getRequiresSecondFactor());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertEquals("challenge-1", response.getSecondFactorOptions().getFirst().getChallengeId());
        assertNull(response.getUser().sessionId());
        verify(authSessionStore, never()).save(any(), eq(true));
        verify(jwtTokenService, never()).generateAccessToken(any());
    }
}
