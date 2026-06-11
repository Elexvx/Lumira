package com.lumira.auth.service;

import com.lumira.api.auth.LoginRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAppServiceTest {

    private SystemInternalApi systemInternalApi;
    private LoginEncryptionService loginEncryptionService;
    private LoginProtectionService loginProtectionService;
    private AuthSessionStore authSessionStore;
    private PasswordEncoder passwordEncoder;
    private AuthAppService authAppService;

    @BeforeEach
    void setUp() {
        systemInternalApi = mock(SystemInternalApi.class);
        loginEncryptionService = mock(LoginEncryptionService.class);
        loginProtectionService = mock(LoginProtectionService.class);
        authSessionStore = mock(AuthSessionStore.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        WechatLoginService wechatLoginService = mock(WechatLoginService.class);
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        SecuritySettingsService securitySettingsService = mock(SecuritySettingsService.class);

        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
        when(securitySettingsService.isAllowMultiDeviceLogin()).thenReturn(true);

        authAppService = new AuthAppService(
                systemInternalApi,
                loginEncryptionService,
                loginProtectionService,
                authSessionStore,
                jwtTokenService,
                passwordEncoder,
                securityContextFacade,
                clientIpResolver,
                wechatLoginService,
                securityProperties,
                securitySettingsService
        );
    }

    @Test
    void passwordLoginRequiresBoundTotpEvenWhenSmsAndEmailLoginAreDisabled() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "jane",
                "encoded-password",
                "ENABLED",
                null,
                "jane@example.com",
                "Jane",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "zh-CN"
        );
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("dashboard:view"), List.of(), null, List.of(), List.of(), List.of(), "/dashboard/home");
        LoginResponseDTO.SecondFactorOptionDTO totpOption = new LoginResponseDTO.SecondFactorOptionDTO();
        totpOption.setFactorCode("totp");
        totpOption.setFactorName("2FA");
        totpOption.setChallengeId("challenge-1");
        totpOption.setMaskedContact("认证器");
        totpOption.setPromptMessage("请输入认证器中的 6 位验证码完成验证");

        when(systemInternalApi.findLoginUser("jane")).thenReturn(user);
        when(systemInternalApi.loginCapabilities(PlatformConstants.PLATFORM_TENANT_ID))
                .thenReturn(new LoginCapabilitiesDTO(true, false, false, false, false, false, List.of("password")));
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(snapshot);
        when(systemInternalApi.listLoginSecondFactorOptions(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(List.of(totpOption));
        when(loginEncryptionService.decryptPassword("ciphertext")).thenReturn("password");
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        LoginResponseDTO response = authAppService.login(
                new LoginRequest("jane", null, "ciphertext", null, null, null),
                httpRequest
        );

        assertTrue(Boolean.TRUE.equals(response.getRequiresSecondFactor()));
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertEquals(1, response.getSecondFactorOptions().size());
        assertEquals("challenge-1", response.getSecondFactorOptions().get(0).getChallengeId());
        verify(systemInternalApi).listLoginSecondFactorOptions(PlatformConstants.PLATFORM_TENANT_ID, 42L);
        verify(authSessionStore, never()).save(any(), anyBoolean());
        verify(loginProtectionService, never()).clearFailureState("jane", "127.0.0.1");
    }
}
