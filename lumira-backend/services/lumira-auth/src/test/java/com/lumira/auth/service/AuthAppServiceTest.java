package com.lumira.auth.service;

import com.lumira.api.auth.LoginRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.auth.LoginCodeChallengeRequest;
import com.lumira.api.auth.LoginCodeChallengeDTO;
import com.lumira.api.auth.PasswordResetCompleteRequest;
import com.lumira.api.auth.RefreshTokenRequest;
import com.lumira.api.auth.RefreshTokenResponseDTO;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.SimulatedRoleSwitchRequest;
import com.lumira.api.auth.SimulatedRoleSwitchResponseDTO;
import com.lumira.api.auth.VerificationBindRequest;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.VerificationBindingChallengeDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.runtime.ReadModelVersionCache;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import com.lumira.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAppServiceTest {

    @Test
    void publicVerificationDtosShouldNotExposeDebugCodes() {
        assertFalse(Arrays.stream(LoginCodeChallengeDTO.class.getDeclaredFields()).map(Field::getName).toList().contains("debugCode"));
        assertFalse(Arrays.stream(VerificationChallengeDTO.class.getDeclaredFields()).map(Field::getName).toList().contains("debugCode"));
        assertFalse(Arrays.stream(VerificationBindingChallengeDTO.class.getDeclaredFields()).map(Field::getName).toList().contains("recoveryCodes"));
        assertFalse(Arrays.stream(VerificationProviderDTO.class.getDeclaredFields()).map(Field::getName).toList().contains("debugCode"));
    }

    private SystemInternalApi systemInternalApi;
    private LoginEncryptionService loginEncryptionService;
    private LoginProtectionService loginProtectionService;
    private AuthSessionStore authSessionStore;
    private JwtTokenService jwtTokenService;
    private PasswordEncoder passwordEncoder;
    private AuthAppService authAppService;
    private SecurityContextFacade securityContextFacade;
    private SecuritySettingsService securitySettingsService;
    private AuthSecurityProperties authSecurityProperties;

    @BeforeEach
    void setUp() {
        systemInternalApi = mock(SystemInternalApi.class);
        loginEncryptionService = mock(LoginEncryptionService.class);
        loginProtectionService = mock(LoginProtectionService.class);
        authSessionStore = mock(AuthSessionStore.class);
        jwtTokenService = mock(JwtTokenService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        WechatLoginService wechatLoginService = mock(WechatLoginService.class);
        authSecurityProperties = new AuthSecurityProperties();
        securitySettingsService = mock(SecuritySettingsService.class);

        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
        when(securitySettingsService.isAllowMultiDeviceLogin()).thenReturn(true);
        when(systemInternalApi.findUserById(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> enabledUser(invocation.getArgument(0)));
        when(systemInternalApi.findUserProfileById(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> enabledUser(invocation.getArgument(0)));
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(1L);
        when(systemInternalApi.requiresInitialPasswordChange(org.mockito.ArgumentMatchers.anyLong(), anyString()))
                .thenReturn(false);
        when(systemInternalApi.verifyPasswordLogin(anyString(), anyString()))
                .thenAnswer(invocation -> verifiedPasswordLogin(enabledUser(42L), true, false));
        when(securitySettingsService.getIdleTimeoutSeconds()).thenReturn(1800L);
        SecuritySettingsDTO securitySettings = new SecuritySettingsDTO(
                1800L,
                7200L,
                129600L,
                true,
                false,
                "IMAGE",
                10L,
                5L,
                20L,
                300L,
                60L
        );
        when(securitySettingsService.snapshot()).thenReturn(securitySettings);

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
                authSecurityProperties,
                securitySettingsService
        );
    }

    @Test
    void loginShouldRequireCaptchaWhenCaptchaIsEnabled() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        when(securitySettingsService.isCaptchaEnabled()).thenReturn(true);

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> authAppService.login(new LoginRequest("jane", null, "ciphertext", null, null, null), httpRequest)
        );

        verify(systemInternalApi, never()).verifyPasswordLogin("jane", "password");
        verify(systemInternalApi, never()).validateCaptcha(any());
        verify(loginProtectionService).recordFailure("jane", "127.0.0.1");
    }

    @Test
    void refreshTokenShouldRejectStaleTokenId() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setTokenId("stale-refresh-id");
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(
                com.lumira.common.exception.BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest("refresh-token"))
        );

        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void refreshTokenShouldRejectClaimsWithoutUserUuid() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(
                BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest("refresh-token"))
        );

        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void refreshTokenShouldAcceptStalePermissionsVersionAfterSessionSnapshotRefresh() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        session.setPermissionsVersion("v2");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion("v1");
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(2L);
        seedPermissionVersionCache(42L, "v2");
        when(jwtTokenService.generateAccessToken(session)).thenReturn("access-token-2");
        when(jwtTokenService.generateRefreshToken(eq(session), anyString())).thenReturn("refresh-token-2");
        when(jwtTokenService.getAccessTokenExpireSeconds()).thenReturn(1800L);

        RefreshTokenResponseDTO response = authAppService.refreshToken(new RefreshTokenRequest("refresh-token"));

        assertEquals("access-token-2", response.accessToken());
        assertEquals("refresh-token-2", response.refreshToken());
        assertEquals("v2", session.getPermissionsVersion());
        assertEquals(session.getSessionVersion(), response.sessionVersion());
        assertEquals("v2", response.permissionsVersion());
        verify(authSessionStore).save(session, true);
    }

    @Test
    void refreshTokenShouldHydrateLatestPermissionsBeforeIssuingTokens() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        session.setPermissionsVersion("v1");
        session.setPermissions(List.of("dashboard:view"));
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion("v1");
        claims.setTokenId(session.getRefreshTokenId());
        PermissionSnapshotDTO refreshedSnapshot = new PermissionSnapshotDTO(
                "v2",
                List.of("dashboard:view", "competition:registration:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/competitions/register"
        );
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(2L);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(refreshedSnapshot);
        when(jwtTokenService.generateAccessToken(session)).thenReturn("access-token-v2");
        when(jwtTokenService.generateRefreshToken(eq(session), anyString())).thenReturn("refresh-token-v2");
        when(jwtTokenService.getAccessTokenExpireSeconds()).thenReturn(1800L);

        RefreshTokenResponseDTO response = authAppService.refreshToken(new RefreshTokenRequest("refresh-token"));

        assertEquals("v2", session.getPermissionsVersion());
        assertEquals(List.of("dashboard:view", "competition:registration:view"), session.getPermissions());
        assertEquals("/competitions/register", session.getDefaultHomePath());
        assertEquals("access-token-v2", response.accessToken());
        assertEquals("refresh-token-v2", response.refreshToken());
        assertEquals(session.getSessionVersion(), response.sessionVersion());
        assertEquals("v2", response.permissionsVersion());
        verify(authSessionStore).save(session, false);
        verify(authSessionStore).save(session, true);
    }

    @Test
    void refreshTokenShouldRejectClaimsWithoutPermissionsVersion() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));

        assertThrows(
                BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest("refresh-token"))
        );

        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void refreshTokenShouldReportTemporaryFailureWhenLatestPermissionsAreUnavailable() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        session.setPermissionsVersion("v1");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion("v1");
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(2L);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest("refresh-token"))
        );

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, exception.getErrorCode());
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any(), anyString());
    }

    @Test
    void refreshTokenShouldRejectBlankTokenBeforeParsing() {
        assertThrows(
                BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest(" "))
        );

        verify(jwtTokenService, never()).parseToken(anyString());
        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void refreshTokenShouldRejectIdleExpiredSession() {
        AuthSession session = cachedSession();
        session.setLastActivityAt(Instant.now().minusSeconds(1801));
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion(session.getPermissionsVersion());
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));

        BizException exception = assertThrows(
                BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest("refresh-token"))
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        verify(authSessionStore).removeIfUnchanged(session, true);
        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void refreshTokenShouldRotateTokenWithoutAdvancingLastActivity() {
        AuthSession session = cachedSession();
        Instant originalLastActivityAt = Instant.now().minusSeconds(120);
        session.setLastActivityAt(originalLastActivityAt);
        session.setRefreshTokenId("current-refresh-id");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion(session.getPermissionsVersion());
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));
        when(jwtTokenService.generateAccessToken(session)).thenReturn("access-token-2");
        when(jwtTokenService.generateRefreshToken(eq(session), anyString())).thenReturn("refresh-token-2");
        when(jwtTokenService.getAccessTokenExpireSeconds()).thenReturn(1800L);

        RefreshTokenResponseDTO response = authAppService.refreshToken(new RefreshTokenRequest("refresh-token"));

        assertEquals("access-token-2", response.accessToken());
        assertEquals("refresh-token-2", response.refreshToken());
        assertEquals(originalLastActivityAt, session.getLastActivityAt());
        verify(authSessionStore).save(session, true);
    }

    @Test
    void refreshTokenShouldNotIssueTokensWhenSessionMutationLosesRace() {
        AuthSession session = cachedSession();
        session.setRefreshTokenId("current-refresh-id");
        JwtTokenClaims claims = new JwtTokenClaims();
        claims.setTokenType(JwtTokenType.REFRESH);
        claims.setSessionId(session.getSessionId());
        claims.setUserId(session.getUserId());
        claims.setUserUuid(session.getUserUuid());
        claims.setSessionVersion(session.getSessionVersion());
        claims.setPermissionsVersion(session.getPermissionsVersion());
        claims.setTokenId(session.getRefreshTokenId());
        when(jwtTokenService.parseToken("refresh-token")).thenReturn(claims);
        when(authSessionStore.findBySessionId(session.getSessionId())).thenReturn(Optional.of(session));
        org.mockito.Mockito.doThrow(new BizException(
                ErrorCode.SESSION_EXPIRED,
                "Session changed concurrently"
        )).when(authSessionStore).save(session, true);

        BizException exception = assertThrows(
                BizException.class,
                () -> authAppService.refreshToken(new RefreshTokenRequest("refresh-token"))
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any(), anyString());
    }

    @Test
    void loginVerifiedUserShouldNotExposeNumericOnlyVerifiedIdentity() {
        org.assertj.core.api.Assertions.assertThat(Arrays.stream(AuthAppService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(AuthAppService.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("loginVerifiedUser(java.lang.Long,jakarta.servlet.http.HttpServletRequest)"))
                .toList())
                .isEmpty();

        verify(systemInternalApi, never()).findUserById(any());
        verify(systemInternalApi, never()).permissionSnapshot(any(), any());
        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void loginVerifiedUserShouldRejectUserUuidMismatchBeforeSessionCreation() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "user-uuid-42",
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
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(user);

        assertThrows(BizException.class, () -> authAppService.loginVerifiedUser(42L, "other-user-uuid", httpRequest));

        verify(systemInternalApi).findUserProfileById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(any(), any());
        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void loginVerifiedUserShouldRejectWhenTrustedPermissionSnapshotIsUnavailable() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(enabledUser(42L));
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> authAppService.loginVerifiedUser(42L, "user-uuid-42", httpRequest));

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertEquals("Session permissions are unavailable", exception.getMessage());
        verify(authSessionStore, never()).save(any(), anyBoolean());
    }

    @Test
    void verificationUnbindShouldRejectMismatchedFactorCodeBeforeDelegating() {
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("email", "challenge-1", "123456");
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser());

        assertThrows(BizException.class, () -> authAppService.verificationUnbind("totp", request));

        verify(systemInternalApi, never()).unbindVerificationProvider(any(), anyString(), anyString(), any());
    }

    @Test
    void verificationUnbindShouldDelegateVerifiedChallenge() {
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("totp", "challenge-1", "123456");
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser());
        when(systemInternalApi.unbindVerificationProvider(42L, "user-uuid-42", "totp", request)).thenReturn(true);

        assertTrue(authAppService.verificationUnbind("totp", request));

        verify(systemInternalApi).unbindVerificationProvider(42L, "user-uuid-42", "totp", request);
    }

    @Test
    void verificationBindShouldDelegateVerificationRequest() {
        VerificationBindRequest request = new VerificationBindRequest("Password!23", null, null, null);
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser());
        VerificationBindingChallengeDTO challenge = new VerificationBindingChallengeDTO();
        challenge.setFactorCode("totp");
        challenge.setFactorName("2FA");
        challenge.setChallengeId("challenge-1");
        challenge.setMaskedContact("***");
        when(systemInternalApi.bindVerificationProvider(42L, "user-uuid-42", "totp", request))
                .thenReturn(challenge);

        VerificationBindingChallengeDTO result = authAppService.verificationBind("totp", request);

        assertEquals("challenge-1", result.getChallengeId());
        verify(systemInternalApi).bindVerificationProvider(42L, "user-uuid-42", "totp", request);
    }

    @Test
    void completeLoginCodeLoginShouldUseVerifiedUserUuidWhenCreatingSession() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "user-uuid-42",
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
        com.lumira.api.auth.LoginCodeCompleteRequest request = new com.lumira.api.auth.LoginCodeCompleteRequest("challenge-1", "123456");
        when(systemInternalApi.completeLoginCodeLogin(request))
                .thenReturn(new VerificationVerificationDTO(true, "ok", 42L, "user-uuid-42", "email", null));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(user);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);

        LoginResponseDTO response = authAppService.completeLoginCodeLogin(request, httpRequest);

        assertEquals(42L, response.getUser().userId());
        verify(systemInternalApi).permissionSnapshot(42L, "user-uuid-42");
        verify(authSessionStore).save(any(AuthSession.class), eq(true));
    }

    @Test
    void loginCodeChallengeShouldApplyAttemptProtectionAndRecordFailure() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        LoginCodeChallengeRequest request = new LoginCodeChallengeRequest("sms", "13800138000");
        when(systemInternalApi.loginCodeChallenge("13800138000", "sms"))
                .thenThrow(new BizException(ErrorCode.LOGIN_RATE_LIMITED, "rate limited"));

        assertThrows(
                BizException.class,
                () -> authAppService.loginCodeChallenge(request, httpRequest)
        );

        verify(loginProtectionService).ensureCanAttempt("13800138000", "127.0.0.1");
        verify(loginProtectionService).recordAttempt("13800138000", "127.0.0.1");
        verify(loginProtectionService).recordFailure("13800138000", "127.0.0.1");
    }

    @Test
    void completeLoginCodeLoginShouldProtectChallengeAttemptsAndClearFailureStateOnSuccess() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        SystemUserSnapshotDTO user = enabledUser(42L);
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("dashboard:view"), List.of(), null, List.of(), List.of(), List.of(), "/dashboard/home");
        com.lumira.api.auth.LoginCodeCompleteRequest request = new com.lumira.api.auth.LoginCodeCompleteRequest("challenge-1", "123456");
        when(systemInternalApi.completeLoginCodeLogin(request))
                .thenReturn(new VerificationVerificationDTO(true, "ok", 42L, "user-uuid-42", "email", null));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(user);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);

        LoginResponseDTO response = authAppService.completeLoginCodeLogin(request, httpRequest);

        assertEquals(42L, response.getUser().userId());
        verify(loginProtectionService).ensureCanAttempt("challenge-1", "127.0.0.1");
        verify(loginProtectionService).recordAttempt("challenge-1", "127.0.0.1");
        verify(loginProtectionService).clearFailureState("challenge-1", "127.0.0.1");
    }

    @Test
    void completeSecondFactorLoginShouldRecordFailureWhenVerificationFails() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        SecondFactorCompleteRequest request = new SecondFactorCompleteRequest("totp", "challenge-2", "123456");
        when(systemInternalApi.completeSecondFactorLogin(request))
                .thenThrow(new BizException(ErrorCode.VALIDATION_ERROR, "invalid code"));

        assertThrows(
                BizException.class,
                () -> authAppService.completeSecondFactorLogin(request, httpRequest)
        );

        verify(loginProtectionService).ensureCanAttempt("challenge-2", "127.0.0.1");
        verify(loginProtectionService).recordAttempt("challenge-2", "127.0.0.1");
        verify(loginProtectionService).recordFailure("challenge-2", "127.0.0.1");
    }

    @Test
    void completePasswordResetShouldProtectChallengeAttemptsAndClearFailureStateOnSuccess() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        PasswordResetCompleteRequest request = new PasswordResetCompleteRequest("challenge-reset-1", "123456", "NewPassword!123");
        when(systemInternalApi.completePasswordReset(request)).thenReturn(true);

        assertTrue(authAppService.completePasswordReset(request, httpRequest));

        verify(loginProtectionService).ensureCanAttempt("challenge-reset-1", "127.0.0.1");
        verify(loginProtectionService).recordAttempt("challenge-reset-1", "127.0.0.1");
        verify(loginProtectionService).clearFailureState("challenge-reset-1", "127.0.0.1");
    }

    @Test
    void passwordLoginRequiresBoundTotpEvenWhenSmsAndEmailLoginAreDisabled() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "user-uuid-42",
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
        totpOption.setMaskedContact("Authenticator");
        totpOption.setPromptMessage("Enter the 6 digit code");

        when(systemInternalApi.verifyPasswordLogin("jane", "password"))
                .thenReturn(verifiedPasswordLogin(user, true, false));
        when(systemInternalApi.loginCapabilities())
                .thenReturn(new LoginCapabilitiesDTO(true, false, false, false, false, false, List.of("password")));
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);
        when(systemInternalApi.listLoginSecondFactorOptions(42L, "user-uuid-42")).thenReturn(List.of(totpOption));
        when(loginEncryptionService.decryptPassword("ciphertext")).thenReturn("password");

        LoginResponseDTO response = authAppService.login(
                new LoginRequest("jane", null, "ciphertext", null, null, null),
                httpRequest
        );

        assertTrue(Boolean.TRUE.equals(response.getRequiresSecondFactor()));
        assertNull(response.getAccessToken());
        assertNull(response.getRefreshToken());
        assertEquals(1, response.getSecondFactorOptions().size());
        assertEquals("challenge-1", response.getSecondFactorOptions().get(0).getChallengeId());
        verify(systemInternalApi).listLoginSecondFactorOptions(42L, "user-uuid-42");
        verify(authSessionStore, never()).save(any(), anyBoolean());
        verify(loginProtectionService, never()).clearFailureState("jane", "127.0.0.1");
    }

    @Test
    void bootstrapCachesBySessionWithinTtlWithoutSystemRoundTrip() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        seedPermissionVersionCache(42L, "v1");

        var firstBootstrap = authAppService.bootstrap();
        var secondBootstrap = authAppService.bootstrap();

        assertNotNull(firstBootstrap.currentUser());
        assertNotNull(secondBootstrap.currentUser());
        assertEquals(42L, secondBootstrap.currentUser().userId());
        assertEquals(1, authAppService.authBootstrapCacheMisses());
        assertEquals(1, authAppService.authBootstrapCacheHits());
        assertEquals(1, authAppService.authBootstrapCacheRefreshes());
        assertEquals(0, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi, times(1)).findUserProfileById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
        verify(securitySettingsService, times(1)).snapshot();
    }

    @Test
    void verificationProvidersShouldRejectUnauthenticatedUserBeforeInternalLookup() {
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(1001L, "alice", null, "sid", 1, false, Set.of("*"))
        );

        BizException exception = assertThrows(BizException.class, authAppService::verificationProviders);

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(systemInternalApi, never()).listVerificationProviders(any(), any());
    }

    @Test
    void bootstrapIncludesPostLoginResourcesWhenProviderIsAvailable() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        AuthPostLoginBootstrapProvider provider = currentUser -> new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(
                List.of(Map.of("menuCode", "dashboard.home", "name", "Workspace", "path", "/dashboard/home")),
                List.of(Map.of("pluginCode", "work-order-feedback", "pluginName", "Feedback", "version", "1.0.0"))
        );
        AuthAppService serviceWithProvider = createAuthAppService(provider);
        seedPermissionVersionCache(serviceWithProvider, 42L, "v1");

        var bootstrap = serviceWithProvider.bootstrap();

        assertEquals(1, bootstrap.menuTree().size());
        assertEquals("dashboard.home", bootstrap.menuTree().getFirst().get("menuCode"));
        assertEquals(1, bootstrap.availablePlugins().size());
        verify(securitySettingsService).snapshot();
    }

    @Test
    void bootstrapSkipsPostLoginResourcesWhenPasswordChangeIsRequired() {
        AuthSession session = cachedSession();
        session.setUserId(1001L);
        session.setUserUuid("user-uuid-1001");
        session.setUsername("admin");
        session.setRequiresPasswordChange(Boolean.TRUE);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        CurrentUser adminCurrentUser = trustedJaneCurrentUser();
        adminCurrentUser.setUserId(1001L);
        adminCurrentUser.setUserUuid("user-uuid-1001");
        adminCurrentUser.setUsername("admin");
        when(securityContextFacade.getCurrentUser()).thenReturn(adminCurrentUser);
        when(systemInternalApi.findUserById(1001L)).thenReturn(initialPasswordAdminUser(1001L));
        when(systemInternalApi.requiresInitialPasswordChange(1001L, "user-uuid-1001")).thenReturn(true);
        seedPermissionVersionCache(1001L, "v1");
        AtomicInteger providerCalls = new AtomicInteger();
        AuthPostLoginBootstrapProvider provider = currentUser -> {
            providerCalls.incrementAndGet();
            return new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(
                    List.of(Map.of("menuCode", "dashboard.home")),
                    List.of(Map.of("pluginCode", "work-order-feedback"))
            );
        };
        AuthAppService serviceWithProvider = createAuthAppService(provider);
        seedPermissionVersionCache(serviceWithProvider, 1001L, "v1");

        var bootstrap = serviceWithProvider.bootstrap();

        assertTrue(Boolean.TRUE.equals(bootstrap.currentUser().requiresPasswordChange()));
        assertTrue(bootstrap.menuTree().isEmpty());
        assertTrue(bootstrap.availablePlugins().isEmpty());
        assertEquals(0, providerCalls.get());
        assertEquals(0, serviceWithProvider.authBootstrapCacheRefreshes());
        verify(securitySettingsService).snapshot();
    }

    @Test
    void bootstrapClearsPasswordChangeFlagWhenSystemReportsPasswordUpdated() {
        AuthSession session = cachedSession();
        session.setUserId(1001L);
        session.setUserUuid("user-uuid-1001");
        session.setUsername("admin");
        session.setRequiresPasswordChange(Boolean.TRUE);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        CurrentUser adminCurrentUser = trustedJaneCurrentUser();
        adminCurrentUser.setUserId(1001L);
        adminCurrentUser.setUserUuid("user-uuid-1001");
        adminCurrentUser.setUsername("admin");
        when(securityContextFacade.getCurrentUser()).thenReturn(adminCurrentUser);
        when(systemInternalApi.findUserById(1001L)).thenReturn(initialPasswordAdminUser(1001L));
        when(systemInternalApi.requiresInitialPasswordChange(1001L, "user-uuid-1001")).thenReturn(false);
        seedPermissionVersionCache(1001L, "v1");
        AtomicInteger providerCalls = new AtomicInteger();
        AuthPostLoginBootstrapProvider provider = ignored -> {
            providerCalls.incrementAndGet();
            return new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(
                    List.of(Map.of("menuCode", "dashboard.home")),
                    List.of(Map.of("pluginCode", "work-order-feedback"))
            );
        };
        AuthAppService serviceWithProvider = createAuthAppService(provider);
        seedPermissionVersionCache(serviceWithProvider, 1001L, "v1");

        var bootstrap = serviceWithProvider.bootstrap();

        assertTrue(Boolean.FALSE.equals(bootstrap.currentUser().requiresPasswordChange()));
        assertEquals(1, providerCalls.get());
        verify(authSessionStore, atLeast(1)).save(session, false);
    }

    @Test
    void bootstrapShouldNotForcePasswordChangeForNonDefaultUserNamedAdmin() {
        AuthSession session = cachedSession();
        session.setUsername("admin");
        session.setRequiresPasswordChange(Boolean.TRUE);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        CurrentUser adminLikeCurrentUser = trustedJaneCurrentUser();
        adminLikeCurrentUser.setUsername("admin");
        when(securityContextFacade.getCurrentUser()).thenReturn(adminLikeCurrentUser);
        when(systemInternalApi.findUserById(42L)).thenReturn(initialPasswordAdminUser(42L));
        seedPermissionVersionCache(42L, "v1");
        AuthAppService serviceWithProvider = createAuthAppService(ignored ->
                new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(List.of(), List.of()));
        seedPermissionVersionCache(serviceWithProvider, 42L, "v1");

        var bootstrap = serviceWithProvider.bootstrap();

        assertFalse(Boolean.TRUE.equals(bootstrap.currentUser().requiresPasswordChange()));
        verify(systemInternalApi, never()).requiresInitialPasswordChange(42L, "user-uuid-42");
    }

    @Test
    void bootstrapDoesNotClearPasswordChangeFlagWhenSessionUserUuidMismatchesSystemUser() {
        AuthSession session = cachedSession();
        session.setRequiresPasswordChange(Boolean.TRUE);
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "other-user-uuid",
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
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser());
        when(systemInternalApi.findUserById(42L)).thenReturn(user);
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(user);
        seedPermissionVersionCache(42L, "v1");

        CompletionException exception = assertThrows(CompletionException.class, () -> authAppService.bootstrap());
        assertTrue(exception.getCause() instanceof BizException);
        assertEquals("Session user changed", exception.getCause().getMessage());

        verify(authSessionStore, never()).save(session, false);
    }

    @Test
    void bootstrapIncludesRuntimeAppearanceSettingsWhenProviderSuppliesThem() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        AuthPostLoginBootstrapProvider provider = currentUser -> new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(
                List.of(),
                List.of(),
                Map.of(
                        "brandingSettings", Map.of("websiteName", "Lumira Fast"),
                        "watermarkSettings", Map.of("enabled", Boolean.FALSE),
                        "floatingWindowSettings", Map.of("apiDocsQrEnabled", Boolean.FALSE)
                )
        );
        AuthAppService serviceWithProvider = createAuthAppService(provider);
        seedPermissionVersionCache(serviceWithProvider, 42L, "v1");

        var bootstrap = serviceWithProvider.bootstrap();

        assertEquals("Lumira Fast", ((Map<?, ?>) bootstrap.runtimeAppearanceSettings().get("brandingSettings")).get("websiteName"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) bootstrap.runtimeAppearanceSettings().get("watermarkSettings")).get("enabled"));
    }

    @Test
    void currentUserCachesBySessionAndPermissionVersionWithinShortTtl() {
        AuthSession session = cachedSession();
        CurrentUser currentUser = new CurrentUser(
                42L,
                "jane",
                "session-1",
                1,
                true,
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L),
                List.of()
        );
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("v1");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        seedPermissionVersionCache(42L, "v1");

        CurrentUserDTO first = authAppService.currentUser();
        CurrentUserDTO second = authAppService.currentUser();

        assertEquals(42L, first.userId());
        assertEquals("v1", second.permissionsVersion());
        verify(authSessionStore, times(2)).findBySessionId("session-1");
        verify(systemInternalApi, times(1)).findUserProfileById(42L);
        verify(systemInternalApi, times(1)).findUserById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserRefreshesSessionActivityWhenTheSessionIsStale() {
        AuthSession session = cachedSession();
        session.setLastActivityAt(Instant.now().minusSeconds(31));
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser(
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L)
        ));
        seedPermissionVersionCache(42L, "v1");

        CurrentUserDTO currentUser = authAppService.currentUser();

        assertEquals(42L, currentUser.userId());
        verify(authSessionStore).save(session, false);
    }

    @Test
    void currentUserUsesTheWinningConcurrentActivityRefresh() {
        AuthSession baselineSession = cachedSession();
        baselineSession.setMutationRevision(4L);
        baselineSession.setLastActivityAt(Instant.now().minusSeconds(31));
        AuthSession activityUpdatedSession = cachedSession();
        activityUpdatedSession.setMutationRevision(5L);
        activityUpdatedSession.setLoginTime(baselineSession.getLoginTime());
        activityUpdatedSession.setExpireTime(baselineSession.getExpireTime());
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(baselineSession), Optional.of(activityUpdatedSession));
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser(
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L)
        ));
        doThrow(new BizException(ErrorCode.SESSION_EXPIRED, "Session changed concurrently"))
                .when(authSessionStore).save(baselineSession, false);
        seedPermissionVersionCache(42L, "v1");

        CurrentUserDTO currentUser = authAppService.currentUser();

        assertEquals(42L, currentUser.userId());
        verify(authSessionStore, times(2)).findBySessionId("session-1");
        verify(authSessionStore).save(baselineSession, false);
        verify(authSessionStore, never()).save(activityUpdatedSession, false);
    }

    @Test
    void currentUserRejectsDisabledUserBeforeServingCachedCurrentUserSnapshot() {
        AuthSession session = cachedSession();
        CurrentUser currentUser = new CurrentUser(
                42L,
                "jane",
                "session-1",
                1,
                true,
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L),
                List.of()
        );
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("v1");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        seedPermissionVersionCache(42L, "v1");

        CurrentUserDTO first = authAppService.currentUser();
        assertEquals(42L, first.userId());

        when(systemInternalApi.findUserById(42L)).thenReturn(disabledUser(42L));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(disabledUser(42L));

        assertThrows(BizException.class, () -> authAppService.currentUser());

        verify(authSessionStore, times(2)).findBySessionId("session-1");
        verify(systemInternalApi, times(2)).findUserProfileById(42L);
        verify(systemInternalApi, times(1)).findUserById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserFallsBackToLivePermissionSnapshotWhenReadModelVersionIsUnavailable() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser(
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L)
        ));
        seedPermissionVersionCache(42L, "v1");
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot"))
                .thenThrow(new RuntimeException("permission read-model unavailable"));
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(new PermissionSnapshotDTO(
                "v1",
                List.of("dashboard:view"),
                List.of(7L),
                9L,
                List.of(9L),
                List.of(10L),
                List.of(),
                "/dashboard/home"
        ));

        CurrentUserDTO currentUser = authAppService.currentUser();

        assertEquals(42L, currentUser.userId());
        assertEquals("v1", currentUser.permissionsVersion());
        verify(systemInternalApi).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserRefreshesCachedSnapshotWhenPermissionVersionChanges() {
        AuthSession session = cachedSession();
        CurrentUser currentUser = trustedJaneCurrentUser(
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L)
        );
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        seedPermissionVersionCache(42L, "v1");

        CurrentUserDTO first = authAppService.currentUser();
        assertEquals("v1", first.permissionsVersion());

        seedPermissionVersionCache(42L, "v2");
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(new PermissionSnapshotDTO(
                "v2",
                List.of("dashboard:view", "project:view"),
                List.of(7L),
                9L,
                List.of(9L),
                List.of(10L),
                List.of(),
                "/dashboard/home"
        ));

        CurrentUserDTO second = authAppService.currentUser();

        assertEquals("v2", second.permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), second.permissions());
        verify(systemInternalApi, times(2)).permissionSnapshot(42L, "user-uuid-42");
        verify(authSessionStore, atLeast(1)).save(session, false);
    }

    @Test
    void currentUserRejectsCachedSnapshotWhenRefreshedPermissionSnapshotIsUnavailable() {
        AuthSession session = cachedSession();
        CurrentUser currentUser = trustedJaneCurrentUser(
                Set.of("dashboard:view"),
                Set.of(7L),
                9L,
                Set.of(9L),
                Set.of(10L)
        );
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        seedPermissionVersionCache(42L, "v1");

        CurrentUserDTO first = authAppService.currentUser();
        assertEquals("v1", first.permissionsVersion());

        seedPermissionVersionCache(42L, "v2");
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> authAppService.currentUser());

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        assertEquals("Session permissions are unavailable", exception.getMessage());
        verify(systemInternalApi, times(2)).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserRejectsAnonymousPrincipalBeforeSessionLookup() {
        when(securityContextFacade.getCurrentUser()).thenReturn(new CurrentUser(0L, "anonymous", null, null, 0, false, Set.of()));

        assertThrows(BizException.class, () -> authAppService.currentUser());

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void currentUserRejectsBlankUsernameBeforeSessionLookup() {
        CurrentUser currentUser = new CurrentUser(42L, " ", null, "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setPermissionsVersion("v1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThrows(BizException.class, () -> authAppService.currentUser());

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void currentUserRejectsMissingSessionVersionBeforeSessionLookup() {
        CurrentUser currentUser = new CurrentUser(42L, "jane", null, "session-1", null, true, Set.of("dashboard:view"));
        currentUser.setPermissionsVersion("v1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThrows(BizException.class, () -> authAppService.currentUser());

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void keepaliveRefreshesSessionActivityWhenTheSessionIsStale() {
        AuthSession session = cachedSession();
        session.setLastActivityAt(Instant.now().minusSeconds(31));
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser());

        assertTrue(authAppService.keepalive());

        verify(authSessionStore).findBySessionId("session-1");
        verify(authSessionStore).save(session, false);
    }

    @Test
    void keepaliveRejectsMissingUserUuidBeforeSessionLookup() {
        CurrentUser currentUser = new CurrentUser(42L, "jane", null, "session-1", 1, true, Set.of("dashboard:view"));
        currentUser.setPermissionsVersion("v1");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);

        assertThrows(BizException.class, () -> authAppService.keepalive());

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void bootstrapRejectsAuthenticatedPrincipalWithoutSessionIdBeforeSessionLookup() {
        when(securityContextFacade.getCurrentUser()).thenReturn(new CurrentUser(42L, "jane", null, null, 1, true, Set.of("dashboard:view")));

        assertThrows(BizException.class, () -> authAppService.bootstrap());

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void loginCapabilitiesAreReusedWhenPublicBootstrapVersionStable() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");

        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "user-uuid-42",
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
        LoginCapabilitiesDTO capabilities = new LoginCapabilitiesDTO(true, false, false, false, false, false, List.of("password"));

        when(systemInternalApi.verifyPasswordLogin("jane", "password"))
                .thenReturn(verifiedPasswordLogin(user, true, false));
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L, 11L, 11L);
        when(systemInternalApi.loginCapabilities()).thenReturn(capabilities);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);
        when(loginEncryptionService.decryptPassword("ciphertext")).thenReturn("password");

        LoginRequest request = new LoginRequest("jane", null, "ciphertext", null, null, null);

        LoginResponseDTO firstLogin = authAppService.login(request, httpRequest);
        LoginResponseDTO secondLogin = authAppService.login(request, httpRequest);

        assertTrue(Boolean.FALSE.equals(firstLogin.getRequiresSecondFactor()));
        assertTrue(Boolean.FALSE.equals(secondLogin.getRequiresSecondFactor()));
        verify(systemInternalApi, times(2)).verifyPasswordLogin("jane", "password");
        verify(systemInternalApi, times(1)).loginCapabilities();
        verify(systemInternalApi, times(1)).readModelVersion("platform", "public-bootstrap");
    }

    @Test
    void loginCapabilitiesReloadWhenPublicBootstrapVersionChanges() throws Exception {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");

        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "user-uuid-42",
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
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("dashboard:view"), List.of(1002L), null, List.of(), List.of(), List.of(), "/dashboard/home");
        LoginCapabilitiesDTO capabilities = new LoginCapabilitiesDTO(true, false, false, false, false, false, List.of("password"));

        when(systemInternalApi.verifyPasswordLogin("jane", "password"))
                .thenReturn(verifiedPasswordLogin(user, true, false));
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L, 12L);
        when(systemInternalApi.loginCapabilities()).thenReturn(capabilities, capabilities);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);
        when(loginEncryptionService.decryptPassword("ciphertext")).thenReturn("password");

        LoginRequest request = new LoginRequest("jane", null, "ciphertext", null, null, null);

        LoginResponseDTO firstLogin = authAppService.login(request, httpRequest);
        Thread.sleep(2100L);
        LoginResponseDTO secondLogin = authAppService.login(request, httpRequest);

        assertTrue(Boolean.FALSE.equals(firstLogin.getRequiresSecondFactor()));
        assertTrue(Boolean.FALSE.equals(secondLogin.getRequiresSecondFactor()));
        verify(systemInternalApi, times(2)).loginCapabilities();
        verify(systemInternalApi, times(2)).readModelVersion("platform", "public-bootstrap");
    }

    @Test
    void bootstrapRefreshesWhenPublicBootstrapVersionChanges() throws Exception {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L, 12L);
        seedPermissionVersionCache(42L, "v1");

        authAppService.bootstrap();
        Thread.sleep(2100L);
        var secondBootstrap = authAppService.bootstrap();

        assertEquals(42L, secondBootstrap.currentUser().userId());
        assertEquals(2, authAppService.authBootstrapCacheMisses());
        assertEquals(0, authAppService.authBootstrapCacheHits());
        verify(securitySettingsService, times(2)).snapshot();
    }

    @Test
    void bootstrapWithProviderRefreshesWhenPluginBootstrapVersionChanges() throws Exception {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        AtomicInteger providerLoads = new AtomicInteger();
        AuthPostLoginBootstrapProvider provider = currentUser -> {
            int loadNumber = providerLoads.incrementAndGet();
            return new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(
                    List.of(Map.of("menuCode", "dashboard.home", "name", "menu-" + loadNumber, "path", "/dashboard/home")),
                    List.of(Map.of("pluginCode", "work-order-feedback", "pluginName", "plugin-" + loadNumber, "version", "1.0." + loadNumber)),
                    Map.of("brandingSettings", Map.of("websiteName", "Lumira " + loadNumber))
            );
        };
        AuthAppService serviceWithProvider = createAuthAppService(provider);
        when(systemInternalApi.readModelVersion("platform", "public-bootstrap")).thenReturn(11L, 11L);
        when(systemInternalApi.readModelVersion("platform", "runtime-appearance")).thenReturn(21L, 21L);
        when(systemInternalApi.readModelVersion("plugin", "bootstrap")).thenReturn(31L, 32L);
        when(systemInternalApi.readModelVersion("platform", "menu-tree")).thenReturn(41L, 41L);
        seedPermissionVersionCache(serviceWithProvider, 42L, "v1");

        var firstBootstrap = serviceWithProvider.bootstrap();
        Thread.sleep(2100L);
        var secondBootstrap = serviceWithProvider.bootstrap();

        assertEquals("menu-1", firstBootstrap.menuTree().getFirst().get("name"));
        assertEquals("menu-2", secondBootstrap.menuTree().getFirst().get("name"));
        assertEquals("Lumira 2", ((Map<?, ?>) secondBootstrap.runtimeAppearanceSettings().get("brandingSettings")).get("websiteName"));
        assertEquals(2, serviceWithProvider.authBootstrapCacheMisses());
        assertEquals(0, serviceWithProvider.authBootstrapCacheHits());
        assertEquals(2, providerLoads.get());
    }

    @Test
    void bootstrapUsesBatchReadModelVersionProviderWhenAvailable() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        AuthPostLoginBootstrapProvider provider = currentUser -> new AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload(
                List.of(Map.of("menuCode", "dashboard.home", "name", "workspace", "path", "/dashboard/home")),
                List.of(Map.of("pluginCode", "work-order-feedback", "pluginName", "feedback", "version", "1.0.0"))
        );
        AtomicInteger versionLoads = new AtomicInteger();
        AuthReadModelVersionProvider readModelVersionProvider = () -> {
            versionLoads.incrementAndGet();
            return new AuthReadModelVersionProvider.AuthBootstrapReadModelVersions(11L, 21L, 31L, 41L);
        };
        AuthAppService serviceWithProvider = createAuthAppService(provider, readModelVersionProvider);
        seedPermissionVersionCache(serviceWithProvider, 42L, "v1");

        serviceWithProvider.bootstrap();
        serviceWithProvider.bootstrap();

        assertEquals(1, versionLoads.get());
        verify(systemInternalApi, times(1)).readModelVersion("IAM", "permission-snapshot");
        verify(systemInternalApi, never()).readModelVersion("platform", "public-bootstrap");
        verify(systemInternalApi, never()).readModelVersion("platform", "runtime-appearance");
        verify(systemInternalApi, never()).readModelVersion("plugin", "bootstrap");
        verify(systemInternalApi, never()).readModelVersion("platform", "menu-tree");
    }

    @Test    void bootstrapRefreshesWhenPermissionVersionChanges() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        seedPermissionVersionCache(42L, "v1");
        PermissionSnapshotDTO refreshedSnapshot = new PermissionSnapshotDTO(
                "v2",
                List.of("dashboard:view", "project:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        );
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(refreshedSnapshot);

        authAppService.bootstrap();
        session.setPermissionsVersion("v0");
        var secondBootstrap = authAppService.bootstrap();

        assertEquals("v2", secondBootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), secondBootstrap.currentUser().permissions());
        assertEquals(2, authAppService.authBootstrapCacheMisses());
        assertEquals(0, authAppService.authBootstrapCacheHits());
        assertEquals(0, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void bootstrapInvalidatesCachedBootstrapWhenPermissionVersionCacheDrifts() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        seedPermissionVersionCache(42L, "v1");

        var firstBootstrap = authAppService.bootstrap();
        assertEquals("v1", firstBootstrap.currentUser().permissionsVersion());

        seedPermissionVersionCache(42L, "v2");
        PermissionSnapshotDTO refreshedSnapshot = new PermissionSnapshotDTO(
                "v2",
                List.of("dashboard:view", "project:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        );
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(refreshedSnapshot);

        var secondBootstrap = authAppService.bootstrap();

        assertEquals("v2", secondBootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), secondBootstrap.currentUser().permissions());
        assertEquals(1, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi).permissionSnapshot(42L, "user-uuid-42");
        verify(systemInternalApi, times(2)).findUserProfileById(42L);
    }

    @Test
    void bootstrapSkipsPermissionSnapshotWhenReadModelVersionMatchesSessionVersion() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(1L);

        var bootstrap = authAppService.bootstrap();

        assertEquals(42L, bootstrap.currentUser().userId());
        assertEquals("v1", bootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view"), bootstrap.currentUser().permissions());
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void bootstrapDoesNotServeCachedBootstrapWhenPermissionReadModelVersionIsUnavailable() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        seedPermissionVersionCache(42L, "v1");
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot"))
                .thenThrow(new RuntimeException("permission read-model unavailable"));
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(new PermissionSnapshotDTO(
                "v1",
                List.of("dashboard:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        ));

        authAppService.bootstrap();
        var secondBootstrap = authAppService.bootstrap();

        assertEquals("v1", secondBootstrap.currentUser().permissionsVersion());
        assertEquals(2, authAppService.authBootstrapCacheMisses());
        assertEquals(0, authAppService.authBootstrapCacheHits());
        assertEquals(1, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi, times(2)).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void bootstrapRefreshesPermissionSnapshotWhenReadModelVersionHasChanged() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                trustedJaneCurrentUser()
        );
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(3L);
        PermissionSnapshotDTO refreshedSnapshot = new PermissionSnapshotDTO(
                "v3",
                List.of("dashboard:view", "project:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        );
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(refreshedSnapshot);

        var bootstrap = authAppService.bootstrap();

        assertEquals("v3", bootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), bootstrap.currentUser().permissions());
        verify(systemInternalApi).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserBySessionIdUsesCachedPermissionSnapshotWithTrustedProfileReload() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(enabledUser(42L));
        seedPermissionVersionCache(42L, "v1");

        var currentUser = authAppService.currentUserBySessionId("session-1");

        assertEquals(42L, currentUser.userId());
        assertEquals("jane", currentUser.username());
        assertEquals(List.of("dashboard:view"), currentUser.permissions());
        assertEquals("/dashboard/home", currentUser.defaultHomePath());
        verify(systemInternalApi, times(1)).findUserProfileById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserBySessionIdRejectsDisabledUserEvenWhenSessionSnapshotIsCached() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(disabledUser(42L));
        seedPermissionVersionCache(42L, "v1");

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1"));

        verify(systemInternalApi, times(1)).findUserProfileById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserBySessionIdRejectsIdleExpiredSession() {
        AuthSession session = cachedSession();
        session.setLastActivityAt(Instant.now().minusSeconds(1801));
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));

        BizException exception = assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1"));

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        verify(authSessionStore).removeIfUnchanged(session, true);
        verify(systemInternalApi, never()).findUserProfileById(42L);
    }

    @Test
    void currentUserBySessionIdRejectsBlankSessionIdBeforeSessionLookup() {
        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId(" "));

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void currentUserBySessionIdRejectsUnsafeSessionIdBeforeSessionLookup() {
        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("../session"));

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void currentUserBySessionIdRejectsInvalidExpectedUserIdBeforeSessionLookup() {
        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1", 0L, 1));

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void currentUserBySessionIdRejectsInvalidExpectedSessionVersionBeforeSessionLookup() {
        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1", 42L, 0));

        verify(authSessionStore, never()).findBySessionId(anyString());
    }

    @Test
    void currentUserBySessionIdRejectsUnexpectedUserId() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1", 51L, session.getSessionVersion()));
    }

    @Test
    void currentUserBySessionIdRejectsUnexpectedSessionVersion() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1", session.getUserId(), session.getSessionVersion() + 1));
    }

    @Test
    void currentUserBySessionIdRequiresFullExpectedSessionSnapshot() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(enabledUser(42L));
        seedPermissionVersionCache(42L, "v1");

        var currentUser = authAppService.currentUserBySessionId(
                "session-1",
                session.getUserId(),
                session.getUserUuid(),
                session.getSessionVersion(),
                session.getPermissionsVersion(),
                null
        );

        assertEquals("user-uuid-42", currentUser.userUuid());
        assertEquals("v1", currentUser.permissionsVersion());
        verify(systemInternalApi, times(1)).findUserProfileById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
    }

    @Test
    void currentUserBySessionIdHydratesSimulatedRoleSnapshotWhenSessionSnapshotMissing() {
        AuthSession session = cachedSession();
        session.setSimulatedRoleId(9L);
        session.setPermissions(null);
        session.setRoleIds(null);
        session.setDeptIds(null);
        session.setDescendantDeptIds(null);
        session.setDataScopes(null);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(enabledUser(42L));
        when(systemInternalApi.simulatedRolePermissionSnapshot(42L, "user-uuid-42", 9L)).thenReturn(
                new PermissionSnapshotDTO(
                        "role-v1",
                        List.of("team:view"),
                        List.of(9L),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        "/team/home"
                )
        );
        when(systemInternalApi.userRoleOptions(42L, "user-uuid-42")).thenReturn(
                List.of(new CurrentUserRoleOptionDTO(9L, "team_operator", "Team Operator", "FUNCTIONAL", 1, "/workflows/tasks"))
        );

        var currentUser = authAppService.currentUserBySessionId(
                "session-1",
                session.getUserId(),
                session.getUserUuid(),
                session.getSessionVersion(),
                "role-v1",
                9L
        );

        assertEquals(9L, currentUser.simulatedRoleId());
        assertEquals(List.of("team:view"), currentUser.permissions());
        assertEquals(List.of(9L), currentUser.roleIds());
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
        verify(systemInternalApi).simulatedRolePermissionSnapshot(42L, "user-uuid-42", 9L);
        verify(authSessionStore).save(session, false);
    }

    @Test
    void switchSimulatedRoleShouldHydrateSessionAndRotateTokens() {
        AuthSession session = cachedSession();
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser(Set.of("dashboard:view"), Set.of(3L), null, Set.of(), Set.of()));
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(enabledUser(42L));
        when(systemInternalApi.simulatedRolePermissionSnapshot(42L, "user-uuid-42", 9L)).thenReturn(
                new PermissionSnapshotDTO(
                        "role-v2",
                        List.of("team:view", "team:update"),
                        List.of(9L),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        "/team"
                )
        );
        when(systemInternalApi.userRoleOptions(42L, "user-uuid-42")).thenReturn(
                List.of(new CurrentUserRoleOptionDTO(9L, "team_operator", "Team Operator", "FUNCTIONAL", 2, "/workflows/tasks"))
        );
        when(jwtTokenService.generateAccessToken(session)).thenReturn("access-role");
        when(jwtTokenService.generateRefreshToken(eq(session), anyString())).thenReturn("refresh-role");
        when(jwtTokenService.getAccessTokenExpireSeconds()).thenReturn(1800L);

        SimulatedRoleSwitchResponseDTO response = authAppService.switchSimulatedRole(new SimulatedRoleSwitchRequest(9L));

        assertEquals("access-role", response.accessToken());
        assertEquals("refresh-role", response.refreshToken());
        assertEquals(9L, response.currentUser().simulatedRoleId());
        assertEquals(List.of("team:view", "team:update"), response.currentUser().permissions());
        assertEquals("role-v2", response.currentUser().permissionsVersion());
        assertEquals("/team", response.currentUser().defaultHomePath());
        assertEquals(9L, session.getSimulatedRoleId());
        verify(authSessionStore).save(session, true);
        verify(systemInternalApi).simulatedRolePermissionSnapshot(42L, "user-uuid-42", 9L);
    }

    @Test
    void switchSimulatedRoleShouldRetryOnceWhenOnlyConcurrentActivityChanged() {
        AuthSession baselineSession = cachedSession();
        baselineSession.setMutationRevision(4L);
        AuthSession activityUpdatedSession = cachedSession();
        activityUpdatedSession.setMutationRevision(5L);
        activityUpdatedSession.setLoginTime(baselineSession.getLoginTime());
        activityUpdatedSession.setExpireTime(baselineSession.getExpireTime());
        activityUpdatedSession.setLastActivityAt(Instant.now().plusSeconds(1));
        PermissionSnapshotDTO roleSnapshot = new PermissionSnapshotDTO(
                "role-v2",
                List.of("team:view", "team:update"),
                List.of(9L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/team"
        );
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser(Set.of("dashboard:view"), Set.of(3L), null, Set.of(), Set.of()));
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(baselineSession), Optional.of(activityUpdatedSession));
        when(systemInternalApi.simulatedRolePermissionSnapshot(42L, "user-uuid-42", 9L)).thenReturn(roleSnapshot);
        when(systemInternalApi.userRoleOptions(42L, "user-uuid-42")).thenReturn(
                List.of(new CurrentUserRoleOptionDTO(9L, "team_operator", "Team Operator", "FUNCTIONAL", 2, "/workflows/tasks"))
        );
        doThrow(new BizException(ErrorCode.SESSION_EXPIRED, "Session changed concurrently"))
                .doNothing()
                .when(authSessionStore).save(any(AuthSession.class), eq(true));
        when(jwtTokenService.generateAccessToken(activityUpdatedSession)).thenReturn("access-role");
        when(jwtTokenService.generateRefreshToken(eq(activityUpdatedSession), anyString())).thenReturn("refresh-role");
        when(jwtTokenService.getAccessTokenExpireSeconds()).thenReturn(1800L);

        SimulatedRoleSwitchResponseDTO response = authAppService.switchSimulatedRole(new SimulatedRoleSwitchRequest(9L));

        assertEquals("access-role", response.accessToken());
        assertEquals(9L, activityUpdatedSession.getSimulatedRoleId());
        assertEquals("role-v2", activityUpdatedSession.getPermissionsVersion());
        verify(authSessionStore).save(baselineSession, true);
        verify(authSessionStore).save(activityUpdatedSession, true);
        verify(authSessionStore, times(2)).findBySessionId("session-1");
    }

    @Test
    void switchSimulatedRoleShouldNotRetryOverAnotherSecurityMutation() {
        AuthSession baselineSession = cachedSession();
        baselineSession.setMutationRevision(4L);
        AuthSession refreshRotatedSession = cachedSession();
        refreshRotatedSession.setMutationRevision(5L);
        refreshRotatedSession.setLoginTime(baselineSession.getLoginTime());
        refreshRotatedSession.setExpireTime(baselineSession.getExpireTime());
        refreshRotatedSession.setRefreshTokenId("refresh-rotated-elsewhere");
        when(securityContextFacade.getCurrentUser()).thenReturn(trustedJaneCurrentUser(Set.of("dashboard:view"), Set.of(3L), null, Set.of(), Set.of()));
        when(authSessionStore.findBySessionId("session-1"))
                .thenReturn(Optional.of(baselineSession), Optional.of(refreshRotatedSession));
        when(systemInternalApi.simulatedRolePermissionSnapshot(42L, "user-uuid-42", 9L)).thenReturn(
                new PermissionSnapshotDTO(
                        "role-v2",
                        List.of("team:view"),
                        List.of(9L),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        "/team"
                )
        );
        doThrow(new BizException(ErrorCode.SESSION_EXPIRED, "Session changed concurrently"))
                .when(authSessionStore).save(any(AuthSession.class), eq(true));

        BizException exception = assertThrows(
                BizException.class,
                () -> authAppService.switchSimulatedRole(new SimulatedRoleSwitchRequest(9L))
        );

        assertEquals(ErrorCode.SESSION_EXPIRED, exception.getErrorCode());
        verify(authSessionStore, times(1)).save(any(AuthSession.class), eq(true));
        verify(jwtTokenService, never()).generateAccessToken(any(AuthSession.class));
        verify(jwtTokenService, never()).generateRefreshToken(any(AuthSession.class), anyString());
    }

    @Test
    void currentUserBySessionIdRejectsUnexpectedUserUuid() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId(
                "session-1",
                session.getUserId(),
                "other-user-uuid",
                session.getSessionVersion(),
                session.getPermissionsVersion(),
                null
        ));
    }

    @Test
    void currentUserBySessionIdRejectsUnexpectedPermissionsVersion() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        seedPermissionVersionCache(42L, "v1");

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId(
                "session-1",
                session.getUserId(),
                session.getUserUuid(),
                session.getSessionVersion(),
                "stale-permissions",
                null
        ));
    }

    @Test
    void currentUserBySessionIdRejectsUnexpectedSimulatedRoleId() {
        AuthSession session = cachedSession();
        session.setSimulatedRoleId(9L);
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId(
                "session-1",
                session.getUserId(),
                session.getUserUuid(),
                session.getSessionVersion(),
                session.getPermissionsVersion(),
                null
        ));
    }

    @Test
    void currentUserBySessionIdRefreshesPermissionSnapshotByVersionMismatch() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        session.setPermissionsVersion("v0");
        session.setPermissions(List.of("dashboard:view"));
        seedPermissionVersionCache(42L, "v1");
        PermissionSnapshotDTO refreshedSnapshot = new PermissionSnapshotDTO(
                "v1",
                List.of("dashboard:view", "project:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        );
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(refreshedSnapshot);
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(enabledUser(42L));

        var currentUser = authAppService.currentUserBySessionId("session-1");

        assertEquals(42L, currentUser.userId());
        assertEquals(List.of("dashboard:view", "project:view"), currentUser.permissions());
        verify(systemInternalApi, times(1)).findUserProfileById(42L);
        verify(systemInternalApi).permissionSnapshot(42L, "user-uuid-42");
        verify(authSessionStore).save(session, false);
    }

    @Test
    void currentUserBySessionIdUsesUserScopedCacheWithoutCrossUserVersionContamination() {
        AuthSession user42Session = cachedSession();
        user42Session.setSessionId("session-42");
        user42Session.setUserId(42L);
        user42Session.setPermissionsVersion("v10");
        user42Session.setPermissions(List.of("dashboard:view"));
        user42Session.setRoleIds(List.of(3L));
        user42Session.setDeptIds(List.of(100L));
        user42Session.setDescendantDeptIds(List.of(100L));
        user42Session.setDataScopes(List.of());

        AuthSession user51Session = cachedSession();
        user51Session.setSessionId("session-51");
        user51Session.setUserId(51L);
        user51Session.setUserUuid("user-uuid-51");
        user51Session.setPermissionsVersion("v20");
        user51Session.setPermissions(List.of("dashboard:view"));
        user51Session.setRoleIds(List.of(7L));
        user51Session.setDeptIds(List.of(200L));
        user51Session.setDescendantDeptIds(List.of(200L));
        user51Session.setDataScopes(List.of());

        seedPermissionVersionCache(42L, "v10");
        seedPermissionVersionCache(51L, "v20");
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(10L);
        when(systemInternalApi.permissionSnapshot(51L, "user-uuid-51")).thenReturn(new PermissionSnapshotDTO(
                "v21",
                List.of("report:view"),
                List.of(7L),
                null,
                List.of(200L),
                List.of(200L),
                List.of(),
                "/report/home"
        ));

        when(authSessionStore.findBySessionId("session-42")).thenReturn(Optional.of(user42Session));
        when(authSessionStore.findBySessionId("session-51")).thenReturn(Optional.of(user51Session));

        var currentUser42 = authAppService.currentUserBySessionId("session-42");
        var currentUser51 = authAppService.currentUserBySessionId("session-51");

        assertEquals(42L, currentUser42.userId());
        assertEquals(List.of("dashboard:view"), currentUser42.permissions());
        assertEquals(51L, currentUser51.userId());
        assertEquals(List.of("report:view"), currentUser51.permissions());
        verify(systemInternalApi, never()).permissionSnapshot(42L, "user-uuid-42");
        verify(systemInternalApi).permissionSnapshot(51L, "user-uuid-51");
    }

    @Test
    void currentUserBySessionIdRefreshesPermissionSnapshotSingleFlightUnderConcurrency() throws Exception {
        AuthSession session = cachedSession();
        session.setPermissionsVersion("v0");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.readModelVersion("IAM", "permission-snapshot")).thenReturn(1L);
        CountDownLatch remoteCallStarted = new CountDownLatch(1);
        CountDownLatch releaseRemoteCall = new CountDownLatch(1);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenAnswer(invocation -> {
            remoteCallStarted.countDown();
            assertTrue(releaseRemoteCall.await(5, TimeUnit.SECONDS));
            return new PermissionSnapshotDTO(
                    "v1",
                    List.of("dashboard:view", "project:view"),
                    List.of(3L),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    "/dashboard/home"
            );
        });

        int threadCount = 24;
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ArrayList<CompletableFuture<CurrentUserDTO>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    startSignal.await();
                    return authAppService.currentUserBySessionId("session-1");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }, executor));
        }
        startSignal.countDown();
        assertTrue(remoteCallStarted.await(5, TimeUnit.SECONDS));
        TimeUnit.MILLISECONDS.sleep(100);
        releaseRemoteCall.countDown();
        for (CompletableFuture<CurrentUserDTO> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));

        for (CompletableFuture<CurrentUserDTO> future : futures) {
            var currentUser = future.get();
            assertNotNull(currentUser);
            assertEquals("v1", currentUser.permissionsVersion());
            assertEquals(List.of("dashboard:view", "project:view"), currentUser.permissions());
        }
        verify(systemInternalApi, times(1)).permissionSnapshot(42L, "user-uuid-42");
        verify(systemInternalApi, times(1)).readModelVersion("IAM", "permission-snapshot");
        verify(authSessionStore, atLeast(1)).save(session, false);
    }

    @Test
    void currentUserBySessionIdRejectsLegacySessionWithoutUserUuid() {
        AuthSession session = new AuthSession();
        session.setSessionId("legacy-session");
        session.setUserId(42L);
        session.setUsername("jane");
        session.setSessionVersion(1);
        when(authSessionStore.findBySessionId("legacy-session")).thenReturn(Optional.of(session));

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("legacy-session"));

        verify(systemInternalApi, never()).findUserById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(any(), anyString());
        verify(authSessionStore, never()).save(session, false);
    }

    @Test
    void currentUserBySessionIdRejectsHydratedSnapshotWithMismatchedUserUuid() {
        AuthSession session = cachedSession();
        session.setPermissionsVersion(null);
        session.setPermissions(null);
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "other-user-uuid",
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
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(user);

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1"));

        verify(systemInternalApi).findUserProfileById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(any(), anyString());
        verify(authSessionStore, never()).save(session, false);
    }

    @Test
    void currentUserBySessionIdRejectsCachedSnapshotWithBlankUsername() {
        AuthSession session = cachedSession();
        session.setUsername(" ");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(null);

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1"));

        verify(systemInternalApi).findUserProfileById(42L);
        verify(authSessionStore, never()).save(session, false);
    }

    @Test
    void currentUserBySessionIdRejectsHydratedSnapshotWithoutSessionVersion() {
        AuthSession session = cachedSession();
        session.setSessionVersion(null);
        session.setPermissionsVersion(null);
        session.setPermissions(null);
        SystemUserSnapshotDTO user = new SystemUserSnapshotDTO(
                42L,
                "user-uuid-42",
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
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("dashboard:view"), List.of(3L), null, List.of(), List.of(), List.of(), "/dashboard/home");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserProfileById(42L)).thenReturn(user);
        when(systemInternalApi.permissionSnapshot(42L, "user-uuid-42")).thenReturn(snapshot);

        assertThrows(BizException.class, () -> authAppService.currentUserBySessionId("session-1"));

        verify(authSessionStore).save(session, false);
    }

    private AuthSession cachedSession() {
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(42L);
        session.setUserUuid("user-uuid-42");
        session.setUsername("jane");
        session.setLoginTime(now.minusSeconds(10));
        session.setLastActivityAt(now);
        session.setExpireTime(now.plusSeconds(129600));
        session.setSessionVersion(1);
        session.setRefreshTokenId("refresh-token-1");
        session.setPermissionsVersion("v1");
        session.setPermissions(List.of("dashboard:view"));
        session.setRoleIds(List.of(3L));
        session.setDeptIds(List.of());
        session.setDescendantDeptIds(List.of());
        session.setDataScopes(List.of());
        session.setRequiresPasswordChange(false);
        session.setDefaultHomePath("/dashboard/home");
        return session;
    }

    private SystemUserSnapshotDTO enabledUser(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return new SystemUserSnapshotDTO(
                userId,
                "user-uuid-" + userId,
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
    }

    private SystemUserSnapshotDTO disabledUser(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return new SystemUserSnapshotDTO(
                userId,
                "user-uuid-" + userId,
                "jane",
                "encoded-password",
                "DISABLED",
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
    }

    private SystemUserSnapshotDTO initialPasswordAdminUser(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return new SystemUserSnapshotDTO(
                userId,
                "user-uuid-" + userId,
                "admin",
                "encoded-password",
                "ENABLED",
                null,
                "admin@example.com",
                "Admin",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "zh-CN"
        );
    }

    private PasswordLoginVerificationDTO verifiedPasswordLogin(
            SystemUserSnapshotDTO user,
            boolean passwordMatched,
            boolean requiresPasswordChange
    ) {
        return user == null ? null : new PasswordLoginVerificationDTO(user, passwordMatched, requiresPasswordChange);
    }

    private CurrentUser trustedJaneCurrentUser() {
        return trustedJaneCurrentUser(Set.of(), Set.of(), null, Set.of(), Set.of());
    }

    private CurrentUser trustedJaneCurrentUser(
            Set<String> permissions,
            Set<Long> roleIds,
            Long deptId,
            Set<Long> deptIds,
            Set<Long> descendantDeptIds
    ) {
        CurrentUser currentUser = new CurrentUser(
                42L,
                "jane",
                "session-1",
                1,
                true,
                permissions,
                roleIds,
                deptId,
                deptIds,
                descendantDeptIds,
                List.of()
        );
        currentUser.setUserUuid("user-uuid-42");
        currentUser.setPermissionsVersion("v1");
        return currentUser;
    }

    private void seedPermissionVersionCache(Long userId, String version) {
        seedPermissionVersionCache(authAppService, userId, "user-uuid-" + userId, version);
    }

    private void seedPermissionVersionCache(AuthAppService targetService, Long userId, String version) {
        seedPermissionVersionCache(targetService, userId, "user-uuid-" + userId, version);
    }

    private void seedPermissionVersionCache(AuthAppService targetService, Long userId, String userUuid, String version) {
        try {
            Field cacheField = AuthAppService.class.getDeclaredField("permissionSnapshotVersionCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            com.google.common.cache.Cache<Object, Object> cache = (com.google.common.cache.Cache<Object, Object>) cacheField.get(targetService);
            Class<?> cacheKeyType = Class.forName("com.lumira.auth.service.AuthAppService$PermissionSnapshotCacheKey");
            Constructor<?> keyConstructor = cacheKeyType.getDeclaredConstructor(Long.class, String.class, Long.class);
            keyConstructor.setAccessible(true);
            Object cacheKey = keyConstructor.newInstance(userId, userUuid, null);
            Class<?> cacheEntryType = Class.forName("com.lumira.auth.service.AuthAppService$PermissionSnapshotVersionCache");
            Constructor<?> entryConstructor = cacheEntryType.getDeclaredConstructor(String.class);
            entryConstructor.setAccessible(true);
            Object cacheEntry = entryConstructor.newInstance(version);
            cache.put(cacheKey, cacheEntry);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AuthAppService createAuthAppService(AuthPostLoginBootstrapProvider authPostLoginBootstrapProvider) {
        try {
            Constructor<AuthAppService> constructor = AuthAppService.class.getDeclaredConstructor(
                    SystemInternalApi.class,
                    LoginEncryptionService.class,
                    LoginProtectionService.class,
                    AuthSessionStore.class,
                    JwtTokenService.class,
                    PasswordEncoder.class,
                    SecurityContextFacade.class,
                    ClientIpResolver.class,
                    WechatLoginService.class,
                    AuthSecurityProperties.class,
                    SecuritySettingsService.class,
                    AuthPostLoginBootstrapProvider.class,
                    MeterRegistry.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    systemInternalApi,
                    loginEncryptionService,
                    loginProtectionService,
                    authSessionStore,
                    jwtTokenService,
                    passwordEncoder,
                    securityContextFacade,
                    mock(ClientIpResolver.class),
                    mock(WechatLoginService.class),
                    authSecurityProperties,
                    securitySettingsService,
                    authPostLoginBootstrapProvider,
                    null
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AuthAppService createAuthAppService(
            AuthPostLoginBootstrapProvider authPostLoginBootstrapProvider,
            AuthReadModelVersionProvider authReadModelVersionProvider
    ) {
        try {
            Constructor<AuthAppService> constructor = AuthAppService.class.getDeclaredConstructor(
                    SystemInternalApi.class,
                    LoginEncryptionService.class,
                    LoginProtectionService.class,
                    AuthSessionStore.class,
                    JwtTokenService.class,
                    PasswordEncoder.class,
                    SecurityContextFacade.class,
                    ClientIpResolver.class,
                    WechatLoginService.class,
                    AuthSecurityProperties.class,
                    SecuritySettingsService.class,
                    AuthPostLoginBootstrapProvider.class,
                    AuthReadModelVersionProvider.class,
                    MeterRegistry.class,
                    ReadModelVersionCache.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    systemInternalApi,
                    loginEncryptionService,
                    loginProtectionService,
                    authSessionStore,
                    jwtTokenService,
                    passwordEncoder,
                    securityContextFacade,
                    mock(ClientIpResolver.class),
                    mock(WechatLoginService.class),
                    authSecurityProperties,
                    securitySettingsService,
                    authPostLoginBootstrapProvider,
                    authReadModelVersionProvider,
                    null,
                    new ReadModelVersionCache(2000L)
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
