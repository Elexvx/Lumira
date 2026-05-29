package com.legendary.invention.auth.service;

import com.legendary.invention.api.auth.LoginRequest;
import com.legendary.invention.api.auth.LoginResponseDTO;
import com.legendary.invention.api.client.SystemInternalApi;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.VerificationChallengeDTO;
import com.legendary.invention.api.system.VerificationProviderDTO;
import com.legendary.invention.auth.config.SecurityProperties;
import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.auth.support.ClientIpResolver;
import com.legendary.invention.common.constant.PlatformConstants;
import com.legendary.invention.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAppServiceTest {

    private SystemInternalApi systemInternalApi;
    private LoginEncryptionService loginEncryptionService;
    private LoginProtectionService loginProtectionService;
    private AuthSessionStore authSessionStore;
    private JwtTokenService jwtTokenService;
    private PasswordEncoder passwordEncoder;
    private ClientIpResolver clientIpResolver;
    private SecuritySettingsService securitySettingsService;
    private AuthAppService authAppService;

    @BeforeEach
    void setUp() {
        systemInternalApi = mock(SystemInternalApi.class);
        loginEncryptionService = mock(LoginEncryptionService.class);
        loginProtectionService = mock(LoginProtectionService.class);
        authSessionStore = mock(AuthSessionStore.class);
        jwtTokenService = mock(JwtTokenService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        clientIpResolver = mock(ClientIpResolver.class);
        securitySettingsService = mock(SecuritySettingsService.class);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setAllowUnsafeDefaultAdminLogin(true);

        authAppService = new AuthAppService(
                systemInternalApi,
                loginEncryptionService,
                loginProtectionService,
                authSessionStore,
                jwtTokenService,
                passwordEncoder,
                mock(SecurityContextFacade.class),
                clientIpResolver,
                mock(WechatLoginService.class),
                securityProperties,
                securitySettingsService
        );
    }

    @Test
    void loginShouldRequireSecondFactorWhenBoundProviderExists() {
        LoginRequest request = new LoginRequest("admin", null, "encrypted", null, null, null);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        SystemUserSnapshotDTO user = user();
        VerificationProviderDTO provider = provider(true, true);
        when(clientIpResolver.resolve(httpRequest)).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(systemInternalApi.findLoginUser("admin")).thenReturn(user);
        when(systemInternalApi.loginCapabilities(PlatformConstants.PLATFORM_TENANT_ID)).thenReturn(passwordLoginCapabilities());
        when(loginEncryptionService.decryptPassword("encrypted")).thenReturn("correct-password");
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(systemInternalApi.listVerificationProviders(PlatformConstants.PLATFORM_TENANT_ID, user.userId())).thenReturn(List.of(provider));
        when(systemInternalApi.verificationChallenge(PlatformConstants.PLATFORM_TENANT_ID, user.userId(), "TOTP")).thenReturn(challenge());

        LoginResponseDTO response = authAppService.login(request, httpRequest);

        assertTrue(response.getRequiresSecondFactor());
        assertFalse(response.getSecondFactorOptions().isEmpty());
        assertEquals("TOTP", response.getSecondFactorOptions().get(0).getFactorCode());
        assertEquals("challenge-1", response.getSecondFactorOptions().get(0).getChallengeId());
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertNull(response.getUser());
        verify(systemInternalApi, never()).permissionSnapshot(any(), any());
        verify(authSessionStore, never()).save(any(AuthSession.class), eq(true));
        verify(loginProtectionService).clearFailureState("admin", "127.0.0.1");
    }

    @Test
    void loginShouldIssueTokensWhenNoBoundProviderExists() {
        LoginRequest request = new LoginRequest("admin", null, "encrypted", null, null, null);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        SystemUserSnapshotDTO user = user();
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("system:read"), List.of(), null, List.of(), List.of(), List.of());
        when(clientIpResolver.resolve(httpRequest)).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(systemInternalApi.findLoginUser("admin")).thenReturn(user);
        when(systemInternalApi.loginCapabilities(PlatformConstants.PLATFORM_TENANT_ID)).thenReturn(passwordLoginCapabilities());
        when(loginEncryptionService.decryptPassword("encrypted")).thenReturn("correct-password");
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(systemInternalApi.listVerificationProviders(PlatformConstants.PLATFORM_TENANT_ID, user.userId())).thenReturn(List.of(provider(false, true)));
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, user.userId())).thenReturn(snapshot);
        when(jwtTokenService.getRefreshTokenExpireSeconds()).thenReturn(3600L);
        when(jwtTokenService.getAccessTokenExpireSeconds()).thenReturn(300L);
        when(jwtTokenService.generateAccessToken(any(AuthSession.class))).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(any(AuthSession.class), any())).thenReturn("refresh-token");
        when(securitySettingsService.isAllowMultiDeviceLogin()).thenReturn(true);

        LoginResponseDTO response = authAppService.login(request, httpRequest);

        assertFalse(response.getRequiresSecondFactor());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(authSessionStore).save(any(AuthSession.class), eq(true));
    }

    private LoginCapabilitiesDTO passwordLoginCapabilities() {
        return new LoginCapabilitiesDTO(true, true, true, true, true, true, List.of("PASSWORD"));
    }

    private SystemUserSnapshotDTO user() {
        return new SystemUserSnapshotDTO(42L, "admin", "hash", "ENABLED", null, null, "Admin", null, null, null, null, null, null, null, "zh-CN");
    }

    private VerificationProviderDTO provider(boolean enabled, boolean bound) {
        VerificationProviderDTO provider = new VerificationProviderDTO();
        provider.setFactorCode("TOTP");
        provider.setFactorName("2FA");
        provider.setEnabled(enabled);
        provider.setBound(bound);
        provider.setMaskedContact("authenticator app");
        provider.setPromptMessage("请输入认证器验证码");
        return provider;
    }

    private VerificationChallengeDTO challenge() {
        VerificationChallengeDTO challenge = new VerificationChallengeDTO();
        challenge.setFactorCode("TOTP");
        challenge.setFactorName("2FA");
        challenge.setChallengeId("challenge-1");
        challenge.setMaskedContact("authenticator app");
        challenge.setPromptMessage("请输入认证器验证码");
        return challenge;
    }
}
