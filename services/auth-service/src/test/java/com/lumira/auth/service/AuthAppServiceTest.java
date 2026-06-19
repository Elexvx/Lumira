package com.lumira.auth.service;

import com.lumira.api.auth.LoginRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.auth.RefreshTokenRequest;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import com.lumira.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAppServiceTest {

    private SystemInternalApi systemInternalApi;
    private LoginEncryptionService loginEncryptionService;
    private LoginProtectionService loginProtectionService;
    private AuthSessionStore authSessionStore;
    private JwtTokenService jwtTokenService;
    private PasswordEncoder passwordEncoder;
    private AuthAppService authAppService;
    private SecurityContextFacade securityContextFacade;
    private SecuritySettingsService securitySettingsService;

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
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        securitySettingsService = mock(SecuritySettingsService.class);

        when(clientIpResolver.resolve(any(HttpServletRequest.class))).thenReturn("127.0.0.1");
        when(securitySettingsService.isAllowMultiDeviceLogin()).thenReturn(true);
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
                securityProperties,
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

        verify(systemInternalApi, never()).findLoginUser("jane");
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

    @Test
    void bootstrapCachesBySessionWithinTtlWithoutSystemRoundTrip() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(
                        42L,
                        "jane",
                        PlatformConstants.PLATFORM_TENANT_ID,
                        "session-1",
                        1,
                        true,
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of()
                )
        );
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v1");

        var firstBootstrap = authAppService.bootstrap();
        var secondBootstrap = authAppService.bootstrap();

        assertNotNull(firstBootstrap.currentUser());
        assertNotNull(secondBootstrap.currentUser());
        assertEquals(42L, secondBootstrap.currentUser().userId());
        assertEquals(1, authAppService.authBootstrapCacheMisses());
        assertEquals(1, authAppService.authBootstrapCacheHits());
        assertEquals(1, authAppService.authBootstrapCacheRefreshes());
        assertEquals(0, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi, never()).findUserById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
        verify(securitySettingsService, times(1)).snapshot();
    }

    @Test
    void currentUserCachesBySessionAndPermissionVersionWithinShortTtl() {
        AuthSession session = cachedSession();
        CurrentUser currentUser = new CurrentUser(
                42L,
                "jane",
                PlatformConstants.PLATFORM_TENANT_ID,
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
        currentUser.setPermissionsVersion("v1");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v1");

        CurrentUserDTO first = authAppService.currentUser();
        CurrentUserDTO second = authAppService.currentUser();

        assertEquals(42L, first.userId());
        assertEquals("v1", second.permissionsVersion());
        verify(authSessionStore, times(1)).findBySessionId("session-1");
        verify(systemInternalApi, never()).findUserById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
    }

    @Test
    void loginCapabilitiesAreCachedToReducePerRequestLoad() {
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
        LoginCapabilitiesDTO capabilities = new LoginCapabilitiesDTO(true, false, false, false, false, false, List.of("password"));

        when(systemInternalApi.findLoginUser("jane")).thenReturn(user);
        when(systemInternalApi.loginCapabilities(PlatformConstants.PLATFORM_TENANT_ID)).thenReturn(capabilities);
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(snapshot);
        when(loginEncryptionService.decryptPassword("ciphertext")).thenReturn("password");
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        LoginRequest request = new LoginRequest("jane", null, "ciphertext", null, null, null);

        LoginResponseDTO firstLogin = authAppService.login(request, httpRequest);
        LoginResponseDTO secondLogin = authAppService.login(request, httpRequest);

        assertTrue(Boolean.FALSE.equals(firstLogin.getRequiresSecondFactor()));
        assertTrue(Boolean.FALSE.equals(secondLogin.getRequiresSecondFactor()));
        verify(systemInternalApi, times(2)).findLoginUser("jane");
        verify(systemInternalApi, times(1)).loginCapabilities(PlatformConstants.PLATFORM_TENANT_ID);
    }

    @Test
    void bootstrapRefreshesWhenPermissionVersionChanges() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(
                        42L,
                        "jane",
                        PlatformConstants.PLATFORM_TENANT_ID,
                        "session-1",
                        1,
                        true,
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of()
                )
        );
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v1");
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
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(refreshedSnapshot);

        authAppService.bootstrap();
        session.setPermissionsVersion("v0");
        var secondBootstrap = authAppService.bootstrap();

        assertEquals("v2", secondBootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), secondBootstrap.currentUser().permissions());
        assertEquals(2, authAppService.authBootstrapCacheMisses());
        assertEquals(0, authAppService.authBootstrapCacheHits());
        assertEquals(0, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
    }

    @Test
    void bootstrapInvalidatesCachedBootstrapWhenPermissionVersionCacheDrifts() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(
                        42L,
                        "jane",
                        PlatformConstants.PLATFORM_TENANT_ID,
                        "session-1",
                        1,
                        true,
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of()
                )
        );
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v1");

        var firstBootstrap = authAppService.bootstrap();
        assertEquals("v1", firstBootstrap.currentUser().permissionsVersion());

        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v2");
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
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(refreshedSnapshot);

        var secondBootstrap = authAppService.bootstrap();

        assertEquals("v2", secondBootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), secondBootstrap.currentUser().permissions());
        assertEquals(1, authAppService.authBootstrapCacheAlignmentRejects());
        verify(systemInternalApi).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
        verify(systemInternalApi, times(0)).findUserById(42L);
    }

    @Test
    void bootstrapSkipsPermissionSnapshotWhenReadModelVersionMatchesSessionVersion() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(
                        42L,
                        "jane",
                        PlatformConstants.PLATFORM_TENANT_ID,
                        "session-1",
                        1,
                        true,
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of()
                )
        );
        when(systemInternalApi.readModelVersion(PlatformConstants.PLATFORM_TENANT_ID, "IAM", "permission-snapshot")).thenReturn(1L);

        var bootstrap = authAppService.bootstrap();

        assertEquals(42L, bootstrap.currentUser().userId());
        assertEquals("v1", bootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view"), bootstrap.currentUser().permissions());
        verify(systemInternalApi, never()).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
    }

    @Test
    void bootstrapRefreshesPermissionSnapshotWhenReadModelVersionHasChanged() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(securityContextFacade.getCurrentUser()).thenReturn(
                new CurrentUser(
                        42L,
                        "jane",
                        PlatformConstants.PLATFORM_TENANT_ID,
                        "session-1",
                        1,
                        true,
                        Set.of(),
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of(),
                        List.of()
                )
        );
        when(systemInternalApi.readModelVersion(PlatformConstants.PLATFORM_TENANT_ID, "IAM", "permission-snapshot")).thenReturn(3L);
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
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(refreshedSnapshot);

        var bootstrap = authAppService.bootstrap();

        assertEquals("v3", bootstrap.currentUser().permissionsVersion());
        assertEquals(List.of("dashboard:view", "project:view"), bootstrap.currentUser().permissions());
        verify(systemInternalApi).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
    }

    @Test
    void currentUserBySessionIdUsesCachedSessionSnapshotWithoutSystemRoundTrip() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v1");

        var currentUser = authAppService.currentUserBySessionId("session-1");

        assertEquals(42L, currentUser.userId());
        assertEquals("jane", currentUser.username());
        assertEquals(List.of("dashboard:view"), currentUser.permissions());
        assertEquals("/dashboard/home", currentUser.defaultHomePath());
        verify(systemInternalApi, never()).findUserById(42L);
        verify(systemInternalApi, never()).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
    }

    @Test
    void currentUserBySessionIdRefreshesPermissionSnapshotByVersionMismatch() {
        AuthSession session = cachedSession();
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        session.setPermissionsVersion("v0");
        session.setPermissions(List.of("dashboard:view"));
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v1");
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
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(refreshedSnapshot);

        var currentUser = authAppService.currentUserBySessionId("session-1");

        assertEquals(42L, currentUser.userId());
        assertEquals(List.of("dashboard:view", "project:view"), currentUser.permissions());
        verify(systemInternalApi, never()).findUserById(42L);
        verify(systemInternalApi).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
        verify(authSessionStore).save(session, false);
    }

    @Test
    void currentUserBySessionIdUsesTenantUserScopedCacheWithoutCrossUserVersionContamination() {
        AuthSession user42Session = cachedSession();
        user42Session.setSessionId("session-42");
        user42Session.setCurrentTenantId(PlatformConstants.PLATFORM_TENANT_ID);
        user42Session.setUserId(42L);
        user42Session.setPermissionsVersion("v10");
        user42Session.setPermissions(List.of("dashboard:view"));
        user42Session.setRoleIds(List.of(3L));
        user42Session.setDeptIds(List.of(100L));
        user42Session.setDescendantDeptIds(List.of(100L));
        user42Session.setDataScopes(List.of());

        AuthSession user51Session = cachedSession();
        user51Session.setSessionId("session-51");
        user51Session.setCurrentTenantId(PlatformConstants.PLATFORM_TENANT_ID);
        user51Session.setUserId(51L);
        user51Session.setPermissionsVersion("v20");
        user51Session.setPermissions(List.of("report:view"));
        user51Session.setRoleIds(List.of(7L));
        user51Session.setDeptIds(List.of(200L));
        user51Session.setDescendantDeptIds(List.of(200L));
        user51Session.setDataScopes(List.of());

        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 42L, "v10");
        seedPermissionVersionCache(PlatformConstants.PLATFORM_TENANT_ID, 51L, "v20");

        when(authSessionStore.findBySessionId("session-42")).thenReturn(Optional.of(user42Session));
        when(authSessionStore.findBySessionId("session-51")).thenReturn(Optional.of(user51Session));

        var currentUser42 = authAppService.currentUserBySessionId("session-42");
        var currentUser51 = authAppService.currentUserBySessionId("session-51");

        assertEquals(42L, currentUser42.userId());
        assertEquals(List.of("dashboard:view"), currentUser42.permissions());
        assertEquals(51L, currentUser51.userId());
        assertEquals(List.of("report:view"), currentUser51.permissions());
        verify(systemInternalApi, never()).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
        verify(systemInternalApi, never()).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 51L);
    }

    @Test
    void currentUserBySessionIdRefreshesPermissionSnapshotSingleFlightUnderConcurrency() throws Exception {
        AuthSession session = cachedSession();
        session.setPermissionsVersion("v0");
        when(authSessionStore.findBySessionId("session-1")).thenReturn(Optional.of(session));
        when(systemInternalApi.readModelVersion(PlatformConstants.PLATFORM_TENANT_ID, "IAM", "permission-snapshot")).thenReturn(1L);
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(new PermissionSnapshotDTO(
                "v1",
                List.of("dashboard:view", "project:view"),
                List.of(3L),
                null,
                List.of(),
                List.of(),
                List.of(),
                "/dashboard/home"
        ));

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
        verify(systemInternalApi, times(1)).permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L);
        verify(systemInternalApi, times(1)).readModelVersion(PlatformConstants.PLATFORM_TENANT_ID, "IAM", "permission-snapshot");
        verify(authSessionStore, atLeast(1)).save(session, false);
    }

    @Test
    void currentUserBySessionIdHydratesLegacySessionSnapshotOnce() {
        AuthSession session = new AuthSession();
        session.setSessionId("legacy-session");
        session.setUserId(42L);
        session.setUsername("jane");
        session.setSessionVersion(1);
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
        PermissionSnapshotDTO snapshot = new PermissionSnapshotDTO("v1", List.of("dashboard:view"), List.of(3L), null, List.of(), List.of(), List.of(), "/dashboard/home");
        when(authSessionStore.findBySessionId("legacy-session")).thenReturn(Optional.of(session));
        when(systemInternalApi.findUserById(42L)).thenReturn(user);
        when(systemInternalApi.permissionSnapshot(PlatformConstants.PLATFORM_TENANT_ID, 42L)).thenReturn(snapshot);

        var currentUser = authAppService.currentUserBySessionId("legacy-session");

        assertEquals("v1", currentUser.permissionsVersion());
        assertEquals(List.of("dashboard:view"), currentUser.permissions());
        verify(authSessionStore).save(session, false);
    }

    private AuthSession cachedSession() {
        AuthSession session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(42L);
        session.setCurrentTenantId(PlatformConstants.PLATFORM_TENANT_ID);
        session.setUsername("jane");
        session.setNickname("Jane");
        session.setEmail("jane@example.com");
        session.setLocale("zh-CN");
        session.setSessionVersion(1);
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

    private void seedPermissionVersionCache(Long tenantId, Long userId, String version) {
        try {
            Field cacheField = AuthAppService.class.getDeclaredField("permissionSnapshotVersionCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            com.google.common.cache.Cache<Object, Object> cache = (com.google.common.cache.Cache<Object, Object>) cacheField.get(authAppService);
            Class<?> cacheKeyType = Class.forName("com.lumira.auth.service.AuthAppService$PermissionSnapshotCacheKey");
            Class<?> cacheEntryType = Class.forName("com.lumira.auth.service.AuthAppService$PermissionSnapshotVersionCache");
            Constructor<?> keyConstructor = cacheKeyType.getDeclaredConstructor(Long.class, Long.class);
            Constructor<?> entryConstructor = cacheEntryType.getDeclaredConstructor(String.class);
            keyConstructor.setAccessible(true);
            entryConstructor.setAccessible(true);
            Object key = keyConstructor.newInstance(tenantId, userId);
            Object cacheEntry = entryConstructor.newInstance(version);
            cache.put(key, cacheEntry);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
