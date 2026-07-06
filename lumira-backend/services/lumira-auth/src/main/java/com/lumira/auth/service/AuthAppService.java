package com.lumira.auth.service;

import com.lumira.api.auth.*;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;
import com.lumira.auth.config.AuthSecurityProperties;
import com.lumira.auth.model.AuthSession;
import com.lumira.common.web.repeatsubmit.ClientIpResolver;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ReadModelVersionCache;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.JwtTokenClaims;
import com.lumira.common.security.JwtTokenType;
import com.lumira.common.security.SecurityContextFacade;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lumira.auth.domain.model.AuthDomainModels.AuthSessionAggregate;
import jakarta.servlet.http.HttpServletRequest;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class AuthAppService {

    private static final Logger log = LoggerFactory.getLogger(AuthAppService.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String READ_MODEL_CONTEXT_IAM = "IAM";
    private static final String READ_MODEL_SCOPE_IAM_PERMISSION_SNAPSHOT = "permission-snapshot";
    private static final String READ_MODEL_CONTEXT_PLATFORM = "platform";
    private static final String READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP = "public-bootstrap";
    private static final String READ_MODEL_SCOPE_PLATFORM_RUNTIME_APPEARANCE = "runtime-appearance";
    private static final String READ_MODEL_SCOPE_PLATFORM_MENU_TREE = "menu-tree";
    private static final String READ_MODEL_CONTEXT_PLUGIN = "plugin";
    private static final String READ_MODEL_SCOPE_PLUGIN_BOOTSTRAP = "bootstrap";
    private static final String AUTH_LOGIN_TIMER = "auth.login";
    private static final String AUTH_CURRENT_USER_TIMER = "auth.current_user";
    private static final String AUTH_BOOTSTRAP_TIMER = "auth.bootstrap";
    private static final String AUTH_REFRESH_TOKEN_TIMER = "auth.refresh_token";
    private static final String PLAINTEXT_LOGIN_PASSWORD_HEADER = "X-Login-Password-Plaintext";
    private static final String GLOBAL_PERMISSION_READ_MODEL_VERSION_CACHE_KEY = "permission-snapshot";
    private static final String GLOBAL_PUBLIC_BOOTSTRAP_READ_MODEL_VERSION_CACHE_KEY = "platform/public-bootstrap";
    private static final String GLOBAL_PLATFORM_RUNTIME_APPEARANCE_READ_MODEL_VERSION_CACHE_KEY = "platform/runtime-appearance";
    private static final String GLOBAL_PLATFORM_MENU_TREE_READ_MODEL_VERSION_CACHE_KEY = "platform/menu-tree";
    private static final String GLOBAL_PLUGIN_BOOTSTRAP_READ_MODEL_VERSION_CACHE_KEY = "plugin/bootstrap";
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");
    private static final long CURRENT_USER_CACHE_TTL_MILLIS = 60_000L;
    private static final long BOOTSTRAP_READ_MODEL_VERSION_CACHE_TTL_MILLIS = 2_000L;
    private static final java.util.concurrent.Executor BLOCKING_IO_EXECUTOR = command -> Thread.ofVirtual().start(command);

    private final SystemInternalApi systemInternalApi;
    private final LoginEncryptionService loginEncryptionService;
    private final LoginProtectionService loginProtectionService;
    private final AuthSessionStore authSessionStore;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextFacade securityContextFacade;
    private final ClientIpResolver clientIpResolver;
    private final WechatLoginService wechatLoginService;
    private final AuthSecurityProperties securityProperties;
    private final SecuritySettingsService securitySettingsService;
    private final AuthPostLoginBootstrapProvider authPostLoginBootstrapProvider;
    private final AuthReadModelVersionProvider authReadModelVersionProvider;
    private final ReadModelVersionCache readModelVersionCache;
    private final Cache<PermissionSnapshotCacheKey, PermissionSnapshotVersionCache> permissionSnapshotVersionCache;
    private final LongAdder permissionSnapshotVersionCacheHits = new LongAdder();
    private final LongAdder permissionSnapshotVersionCacheMisses = new LongAdder();
    private final LongAdder permissionSnapshotVersionCacheRefreshes = new LongAdder();
    private final LongAdder permissionSnapshotVersionCacheFallbacks = new LongAdder();
    private final LongAdder authBootstrapCacheHits = new LongAdder();
    private final LongAdder authBootstrapCacheMisses = new LongAdder();
    private final LongAdder authBootstrapCacheRefreshes = new LongAdder();
    private final LongAdder authBootstrapCacheAlignmentRejects = new LongAdder();
    private final Cache<String, CurrentUserDTO> currentUserCache;
    private final Cache<String, AuthBootstrapCache> authBootstrapCache;
    private final Cache<String, String> authBootstrapSessionCacheKeys;
    private final Cache<String, Long> permissionReadModelVersionCache;
    private final Cache<PermissionSnapshotLoadInFlightKey, CompletableFuture<PermissionSnapshotDTO>> permissionSnapshotLoadInFlight;
    private final Cache<String, CompletableFuture<Long>> permissionReadModelVersionLoadInFlight;
    private final long permissionSnapshotVersionCacheTtlMillis;
    private final long authBootstrapCacheTtlMillis;
    private final long loginCapabilitiesCacheTtlMillis;
    private final long authBootstrapCacheMaxEntries;
    private volatile LoginCapabilitiesCache loginCapabilitiesCache;
    private volatile AuthBootstrapReadModelVersionsCache authBootstrapReadModelVersionsCache;
    private final Timer authLoginTimer;
    private final Timer authCurrentUserTimer;
    private final Timer authBootstrapTimer;
    private final Timer authRefreshTokenTimer;
    private final MeterRegistry meterRegistry;

    public AuthAppService(
            SystemInternalApi systemInternalApi,
            LoginEncryptionService loginEncryptionService,
            LoginProtectionService loginProtectionService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            SecurityContextFacade securityContextFacade,
            ClientIpResolver clientIpResolver,
            WechatLoginService wechatLoginService,
            AuthSecurityProperties securityProperties,
            SecuritySettingsService securitySettingsService
    ) {
        this(
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
                securitySettingsService,
                (AuthPostLoginBootstrapProvider) null,
                (AuthReadModelVersionProvider) null,
                (MeterRegistry) null,
                new ReadModelVersionCache(BOOTSTRAP_READ_MODEL_VERSION_CACHE_TTL_MILLIS)
        );
    }

    @Autowired
    public AuthAppService(
            SystemInternalApi systemInternalApi,
            LoginEncryptionService loginEncryptionService,
            LoginProtectionService loginProtectionService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            SecurityContextFacade securityContextFacade,
            ClientIpResolver clientIpResolver,
            WechatLoginService wechatLoginService,
            AuthSecurityProperties securityProperties,
            SecuritySettingsService securitySettingsService,
            ObjectProvider<AuthPostLoginBootstrapProvider> authPostLoginBootstrapProvider,
            ObjectProvider<AuthReadModelVersionProvider> authReadModelVersionProvider,
            ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<ReadModelVersionCache> readModelVersionCache
    ) {
        this(
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
                securitySettingsService,
                authPostLoginBootstrapProvider.getIfAvailable(),
                authReadModelVersionProvider.getIfAvailable(),
                meterRegistry.getIfAvailable(),
                readModelVersionCache.getIfAvailable(() -> new ReadModelVersionCache(BOOTSTRAP_READ_MODEL_VERSION_CACHE_TTL_MILLIS))
        );
    }

    private AuthAppService(
            SystemInternalApi systemInternalApi,
            LoginEncryptionService loginEncryptionService,
            LoginProtectionService loginProtectionService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            SecurityContextFacade securityContextFacade,
            ClientIpResolver clientIpResolver,
            WechatLoginService wechatLoginService,
            AuthSecurityProperties securityProperties,
            SecuritySettingsService securitySettingsService,
            AuthPostLoginBootstrapProvider authPostLoginBootstrapProvider,
            MeterRegistry meterRegistry
    ) {
        this(
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
                securitySettingsService,
                authPostLoginBootstrapProvider,
                null,
                meterRegistry,
                new ReadModelVersionCache(BOOTSTRAP_READ_MODEL_VERSION_CACHE_TTL_MILLIS)
        );
    }

    private AuthAppService(
            SystemInternalApi systemInternalApi,
            LoginEncryptionService loginEncryptionService,
            LoginProtectionService loginProtectionService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            SecurityContextFacade securityContextFacade,
            ClientIpResolver clientIpResolver,
            WechatLoginService wechatLoginService,
            AuthSecurityProperties securityProperties,
            SecuritySettingsService securitySettingsService,
            AuthPostLoginBootstrapProvider authPostLoginBootstrapProvider,
            AuthReadModelVersionProvider authReadModelVersionProvider,
            MeterRegistry meterRegistry,
            ReadModelVersionCache readModelVersionCache
    ) {
        this.systemInternalApi = systemInternalApi;
        this.loginEncryptionService = loginEncryptionService;
        this.loginProtectionService = loginProtectionService;
        this.authSessionStore = authSessionStore;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.securityContextFacade = securityContextFacade;
        this.clientIpResolver = clientIpResolver;
        this.wechatLoginService = wechatLoginService;
        this.securityProperties = securityProperties;
        this.securitySettingsService = securitySettingsService;
        this.authPostLoginBootstrapProvider = authPostLoginBootstrapProvider;
        this.authReadModelVersionProvider = authReadModelVersionProvider;
        this.readModelVersionCache = readModelVersionCache;
        this.permissionSnapshotVersionCacheTtlMillis = Math.max(1, securityProperties.getPermissionSnapshotVersionCacheTtlSeconds()) * 1000L;
        this.authBootstrapCacheTtlMillis = Math.max(
                1,
                securityProperties.getAuthBootstrapCacheTtlSeconds()
        ) * 1000L;
        this.loginCapabilitiesCacheTtlMillis = Math.max(1, securityProperties.getLoginCapabilitiesCacheTtlSeconds()) * 1000L;
        this.authBootstrapCacheMaxEntries = Math.max(1, securityProperties.getPermissionSnapshotVersionCacheMaxEntries());
        this.permissionSnapshotVersionCache = CacheBuilder.newBuilder()
                .maximumSize(Math.max(1L, securityProperties.getPermissionSnapshotVersionCacheMaxEntries()))
                .expireAfterWrite(permissionSnapshotVersionCacheTtlMillis, TimeUnit.MILLISECONDS)
                .build();
        this.currentUserCache = CacheBuilder.newBuilder()
                .maximumSize(authBootstrapCacheMaxEntries)
                .expireAfterWrite(CURRENT_USER_CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
                .build();
        this.authBootstrapCache = CacheBuilder.newBuilder()
                .maximumSize(authBootstrapCacheMaxEntries)
                .expireAfterWrite(authBootstrapCacheTtlMillis, TimeUnit.MILLISECONDS)
                .build();
        this.authBootstrapSessionCacheKeys = CacheBuilder.newBuilder()
                .maximumSize(authBootstrapCacheMaxEntries)
                .expireAfterWrite(authBootstrapCacheTtlMillis, TimeUnit.MILLISECONDS)
                .build();
        this.permissionReadModelVersionCache = CacheBuilder.newBuilder()
                .maximumSize(Math.max(1L, securityProperties.getPermissionSnapshotVersionCacheMaxEntries()))
                .expireAfterWrite(permissionSnapshotVersionCacheTtlMillis, TimeUnit.MILLISECONDS)
                .build();
        this.permissionSnapshotLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(Math.max(1L, securityProperties.getPermissionSnapshotVersionCacheMaxEntries()))
                .expireAfterWrite(permissionSnapshotVersionCacheTtlMillis, TimeUnit.MILLISECONDS)
                .build();
        this.permissionReadModelVersionLoadInFlight = CacheBuilder.newBuilder()
                .maximumSize(Math.max(1L, securityProperties.getPermissionSnapshotVersionCacheMaxEntries()))
                .expireAfterWrite(permissionSnapshotVersionCacheTtlMillis, TimeUnit.MILLISECONDS)
                .build();
        this.meterRegistry = meterRegistry;
        this.authLoginTimer = createTimerIfAvailable(meterRegistry, AUTH_LOGIN_TIMER);
        this.authCurrentUserTimer = createTimerIfAvailable(meterRegistry, AUTH_CURRENT_USER_TIMER);
        this.authBootstrapTimer = createTimerIfAvailable(meterRegistry, AUTH_BOOTSTRAP_TIMER);
        this.authRefreshTokenTimer = createTimerIfAvailable(meterRegistry, AUTH_REFRESH_TOKEN_TIMER);
    }

    public LoginEncryptionKeyDTO loginEncryptionKey() {
        return loginEncryptionService.getPublicKeyInfo();
    }
    public LoginResponseDTO login(LoginRequest request, HttpServletRequest httpServletRequest) {
        long start = System.nanoTime();
        try {
            String loginIp = clientIpResolver.resolve(httpServletRequest);
            String userAgent = httpServletRequest.getHeader("User-Agent");
            String account = request.account();
            loginProtectionService.ensureCanAttempt(account, loginIp);
            loginProtectionService.recordAttempt(account, loginIp);

            if (securitySettingsService.isCaptchaEnabled() || hasCaptchaEvidence(request)) {
                if (!hasCaptchaEvidence(request)) {
                    loginProtectionService.recordFailure(account, loginIp);
                    recordLoginAudit(null, null, account, "CAPTCHA", "FAIL", "CAPTCHA_REQUIRED", loginIp, userAgent);
                    throw new BizException(ErrorCode.CAPTCHA_INVALID, "captcha required");
                }
                boolean captchaValid = Boolean.TRUE.equals(systemInternalApi.validateCaptcha(new com.lumira.api.system.CaptchaValidationRequestDTO(request.captchaId(), request.captchaCode(), request.captchaProof())));
                if (!captchaValid) {
                    loginProtectionService.recordFailure(account, loginIp);
                    recordLoginAudit(null, null, account, "CAPTCHA", "FAIL", "CAPTCHA_INVALID", loginIp, userAgent);
                    throw new BizException(ErrorCode.CAPTCHA_INVALID, "captcha invalid");
                }
            }

            String loginPassword = resolveLoginPassword(request, httpServletRequest);
            CompletableFuture<PasswordLoginVerificationDTO> verificationFuture =
                    supplyBlockingIo(() -> systemInternalApi.verifyPasswordLogin(account, loginPassword));
            CompletableFuture<LoginCapabilitiesDTO> loginCapabilitiesFuture = supplyBlockingIo(this::loadLoginCapabilities);
            PasswordLoginVerificationDTO verification = verificationFuture.join();
            SystemUserSnapshotDTO user = verification == null ? null : verification.user();
            LoginCapabilitiesDTO loginCapabilities = loginCapabilitiesFuture.join();
            if (loginCapabilities != null && !loginCapabilities.passwordLoginAvailable()) {
                loginProtectionService.recordFailure(account, loginIp);
                recordLoginAudit(null, null, account, "PASSWORD", "FAIL", "PASSWORD_LOGIN_DISABLED", loginIp, userAgent);
                throw new BizException(ErrorCode.FORBIDDEN, "Password login is disabled");
            }
            if (user == null) {
                loginProtectionService.recordFailure(account, loginIp);
                recordLoginAudit(null, null, account, "PASSWORD", "FAIL", "ACCOUNT_NOT_FOUND", loginIp, userAgent);
                throw loginFailed();
            }
            if (!"ENABLED".equalsIgnoreCase(user.status())) {
                loginProtectionService.recordFailure(account, loginIp);
                recordLoginAudit(user.userId(), user.userUuid(), user.username(), "PASSWORD", "FAIL", "ACCOUNT_DISABLED", loginIp, userAgent);
                throw loginFailed();
            }
            if (verification == null || !Boolean.TRUE.equals(verification.passwordMatched())) {
                loginProtectionService.recordFailure(account, loginIp);
                recordLoginAudit(user.userId(), user.userUuid(), user.username(), "PASSWORD", "FAIL", "PASSWORD_MISMATCH", loginIp, userAgent);
                throw loginFailed();
            }
            boolean requiresPasswordChange = Boolean.TRUE.equals(verification.requiresPasswordChange());

            CompletableFuture<List<LoginResponseDTO.SecondFactorOptionDTO>> secondFactorOptionsFuture =
                    supplyBlockingIo(() -> loadLoginSecondFactorOptions(user.userId(), user.userUuid()));
            CompletableFuture<PermissionSnapshotDTO> snapshotFuture =
                    supplyBlockingIo(() -> systemInternalApi.permissionSnapshot(user.userId(), user.userUuid()));
            List<LoginResponseDTO.SecondFactorOptionDTO> secondFactorOptions = secondFactorOptionsFuture.join();
            if (secondFactorOptions != null && !secondFactorOptions.isEmpty()) {
                LoginResponseDTO response = new LoginResponseDTO();
                response.setRequiresSecondFactor(Boolean.TRUE);
                response.setSecondFactorOptions(secondFactorOptions);
                recordLoginAudit(user.userId(), user.userUuid(), user.username(), "PASSWORD", "PENDING", "SECOND_FACTOR_REQUIRED", loginIp, userAgent);
                return response;
            }

            PermissionSnapshotDTO snapshot = snapshotFuture.join();
            cachePermissionSnapshotVersion(user.userId(), user.userUuid(), snapshot);

            AuthSession session = buildSession(user, loginIp, userAgent, snapshot, requiresPasswordChange, "PASSWORD");
            saveSessionWithMultiDevicePolicy(session);
            loginProtectionService.clearFailureState(account, loginIp);
            recordLoginAudit(user.userId(), user.userUuid(), user.username(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
            return toLoginResponse(session, user, snapshot, requiresPasswordChange);
        } finally {
            stopAuthTimer(authLoginTimer, start);
        }
    }

    private String resolveLoginPassword(LoginRequest request, HttpServletRequest httpServletRequest) {
        if (securityProperties.isAllowPlaintextLoginPassword()
                && "true".equalsIgnoreCase(httpServletRequest.getHeader(PLAINTEXT_LOGIN_PASSWORD_HEADER))) {
            return request.password();
        }
        return loginEncryptionService.decryptPassword(request.password());
    }

    private BizException loginFailed() {
        return new BizException(
                ErrorCode.LOGIN_FAILED,
                ErrorCode.LOGIN_FAILED.getDefaultUserMessage(),
                ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
        );
    }

    private boolean hasCaptchaEvidence(LoginRequest request) {
        return StringUtils.hasText(request.captchaId())
                || StringUtils.hasText(request.captchaCode())
                || StringUtils.hasText(request.captchaProof());
    }

    private boolean requiresInitialAdminPasswordChange(SystemUserSnapshotDTO user) {
        if (user == null || !DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(user.username())) {
            return false;
        }
        if (user.userId() == null || !StringUtils.hasText(user.userUuid())) {
            return true;
        }
        return Boolean.TRUE.equals(systemInternalApi.requiresInitialPasswordChange(user.userId(), user.userUuid()));
    }

    public LoginCodeChallengeDTO loginCodeChallenge(LoginCodeChallengeRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String challengeScope = request.account();
        loginProtectionService.ensureCanAttempt(challengeScope, loginIp);
        loginProtectionService.recordAttempt(challengeScope, loginIp);
        try {
            return systemInternalApi.loginCodeChallenge(request.account(), request.loginType());
        } catch (RuntimeException exception) {
            loginProtectionService.recordFailure(challengeScope, loginIp);
            throw exception;
        }
    }

    public LoginCodeChallengeDTO passwordResetChallenge(PasswordResetChallengeRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String challengeScope = request.contact();
        loginProtectionService.ensureCanAttempt(challengeScope, loginIp);
        loginProtectionService.recordAttempt(challengeScope, loginIp);
        try {
            return systemInternalApi.passwordResetChallenge(request);
        } catch (RuntimeException exception) {
            loginProtectionService.recordFailure(challengeScope, loginIp);
            throw exception;
        }
    }

    public Boolean completePasswordReset(PasswordResetCompleteRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String challengeScope = request.challengeId();
        loginProtectionService.ensureCanAttempt(challengeScope, loginIp);
        loginProtectionService.recordAttempt(challengeScope, loginIp);
        try {
            Boolean completed = systemInternalApi.completePasswordReset(request);
            if (Boolean.TRUE.equals(completed)) {
                loginProtectionService.clearFailureState(challengeScope, loginIp);
            }
            return completed;
        } catch (RuntimeException exception) {
            loginProtectionService.recordFailure(challengeScope, loginIp);
            throw exception;
        }
    }

    public LoginResponseDTO completeLoginCodeLogin(LoginCodeCompleteRequest request, HttpServletRequest httpServletRequest) {
        return completeLoginCodeLoginProtected(request, httpServletRequest);
    }

    private LoginResponseDTO completeLoginCodeLoginProtected(LoginCodeCompleteRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String challengeScope = request.challengeId();
        loginProtectionService.ensureCanAttempt(challengeScope, loginIp);
        loginProtectionService.recordAttempt(challengeScope, loginIp);
        try {
            var verification = systemInternalApi.completeLoginCodeLogin(request);
            if (verification == null || !Boolean.TRUE.equals(verification.verified())) {
                throw new BizException(ErrorCode.BIZ_ERROR, verification == null ? "Verification failed" : verification.message());
            }
            loginProtectionService.clearFailureState(challengeScope, loginIp);
            return loginVerifiedUser(verification.userId(), verification.userUuid(), httpServletRequest, "LOGIN_CODE");
        } catch (RuntimeException exception) {
            loginProtectionService.recordFailure(challengeScope, loginIp);
            throw exception;
        }
    }

    public WechatAuthorizeUrlDTO wechatAuthorizeUrl() {
        return wechatLoginService.createAuthorizeUrl();
    }

    public LoginResponseDTO wechatLogin(WechatLoginRequest request, HttpServletRequest httpServletRequest) {
        WechatLoginService.WechatOAuthUser wechatUser = wechatLoginService.exchangeCode(request.code(), request.state());
        SystemUserSnapshotDTO user = systemInternalApi.resolveWechatLoginUser(
                new WechatLoginUserRequestDTO(
                        wechatUser.openid(),
                        wechatUser.unionid(),
                        wechatUser.scope(),
                        wechatUser.nickname(),
                        wechatUser.avatarUrl(),
                        wechatUser.country(),
                        wechatUser.province(),
                        wechatUser.city(),
                        wechatUser.sex()
                )
        );
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "Wechat login user creation failed");
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "Login failed: account is disabled: " + user.username(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }
        CompletableFuture<PermissionSnapshotDTO> snapshotFuture = supplyBlockingIo(() -> systemInternalApi.permissionSnapshot(user.userId(), user.userUuid()));
        CompletableFuture<List<LoginResponseDTO.SecondFactorOptionDTO>> secondFactorOptionsFuture = supplyBlockingIo(
                () -> loadLoginSecondFactorOptions(user.userId(), user.userUuid())
        );
        PermissionSnapshotDTO snapshot = snapshotFuture.join();
        cachePermissionSnapshotVersion(user.userId(), user.userUuid(), snapshot);
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String userAgent = httpServletRequest.getHeader("User-Agent");
        List<LoginResponseDTO.SecondFactorOptionDTO> secondFactorOptions = secondFactorOptionsFuture.join();
        if (!secondFactorOptions.isEmpty()) {
            recordLoginAudit(user.userId(), user.userUuid(), user.username(), "WECHAT", "PENDING", "SECOND_FACTOR_REQUIRED", loginIp, userAgent);
            return toPendingSecondFactorResponse(user, snapshot, secondFactorOptions);
        }

        AuthSession session = buildSession(user, loginIp, userAgent, snapshot, requiresInitialAdminPasswordChange(user), "WECHAT");
        saveSessionWithMultiDevicePolicy(session);
        recordLoginAudit(user.userId(), user.userUuid(), user.username(), "WECHAT", "SUCCESS", null, loginIp, userAgent);
        return toLoginResponse(session, user, snapshot);
    }
    public LoginResponseDTO completeSecondFactorLogin(SecondFactorCompleteRequest request, HttpServletRequest httpServletRequest) {
        return completeSecondFactorLoginProtected(request, httpServletRequest);
    }

    private LoginResponseDTO completeSecondFactorLoginProtected(SecondFactorCompleteRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String challengeScope = request.challengeId();
        loginProtectionService.ensureCanAttempt(challengeScope, loginIp);
        loginProtectionService.recordAttempt(challengeScope, loginIp);
        try {
            var verification = systemInternalApi.completeSecondFactorLogin(request);
            if (verification == null || !Boolean.TRUE.equals(verification.verified())) {
                throw new BizException(ErrorCode.BIZ_ERROR, verification == null ? "Second-factor verification failed" : verification.message());
            }
            loginProtectionService.clearFailureState(challengeScope, loginIp);
            return loginVerifiedUser(verification.userId(), verification.userUuid(), httpServletRequest, "SECOND_FACTOR");
        } catch (RuntimeException exception) {
            loginProtectionService.recordFailure(challengeScope, loginIp);
            throw exception;
        }
    }

    public void logout(HttpServletRequest httpServletRequest) {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        authSessionStore.findBySessionId(currentUser.getSessionId()).ifPresent(session -> {
            invalidateAuthBootstrapCache(session);
            AuthSessionAggregate sessionAggregate = new AuthSessionAggregate(
                    session.getSessionId(),
                    session.getUserId(),
                    session.getUserUuid(),
                    session.getLastActivityAt()
            );
            sessionAggregate.revoke("logout");
            authSessionStore.remove(session, true);
        });
    }

    public boolean keepalive() {
        requireAuthenticatedCurrentUser();
        return true;
    }

    public RefreshTokenResponseDTO refreshToken(RefreshTokenRequest request) {
        long start = System.nanoTime();
        try {
            if (request == null || !StringUtils.hasText(request.refreshToken())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "refresh token invalid");
            }
            JwtTokenClaims claims = jwtTokenService.parseToken(request.refreshToken().trim());
            if (claims.getTokenType() != JwtTokenType.REFRESH) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "refresh token invalid");
            }
            AuthSession session = authSessionStore.findBySessionId(claims.getSessionId())
                    .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session has expired"));
            validateRefreshTokenClaims(claims, session);
            AuthSessionAggregate sessionAggregate = new AuthSessionAggregate(
                    session.getSessionId(),
                    session.getUserId(),
                    session.getUserUuid(),
                    session.getLastActivityAt()
            );
            sessionAggregate.touch(Instant.now());
            String refreshTokenId = UUID.randomUUID().toString();
            session.setRefreshTokenId(refreshTokenId);
            session.setLastActivityAt(Instant.now());
            authSessionStore.save(session, true);
            return new RefreshTokenResponseDTO(
                    jwtTokenService.generateAccessToken(session),
                    jwtTokenService.generateRefreshToken(session, refreshTokenId),
                    "Bearer",
                    jwtTokenService.getAccessTokenExpireSeconds()
            );
        } finally {
            stopAuthTimer(authRefreshTokenTimer, start);
        }
    }

    private void validateRefreshTokenClaims(JwtTokenClaims claims, AuthSession session) {
        String claimUserUuid = normalizeText(claims.getUserUuid());
        String sessionUserUuid = normalizeText(session.getUserUuid());
        String claimPermissionsVersion = normalizeText(claims.getPermissionsVersion());
        String sessionPermissionsVersion = normalizeText(session.getPermissionsVersion());
        Long claimSimulatedRoleId = normalizeSimulatedRoleId(claims.getSimulatedRoleId());
        Long sessionSimulatedRoleId = normalizeSimulatedRoleId(session.getSimulatedRoleId());
        if (!StringUtils.hasText(claims.getTokenId())
                || !StringUtils.hasText(claimUserUuid)
                || !StringUtils.hasText(sessionUserUuid)
                || !StringUtils.hasText(claimPermissionsVersion)
                || !StringUtils.hasText(sessionPermissionsVersion)
                || !Objects.equals(claims.getTokenId(), session.getRefreshTokenId())
                || !Objects.equals(claims.getUserId(), session.getUserId())
                || !Objects.equals(claimUserUuid, sessionUserUuid)
                || !Objects.equals(claimSimulatedRoleId, sessionSimulatedRoleId)
                || !Objects.equals(claims.getSessionVersion(), session.getSessionVersion())
                || !Objects.equals(claimPermissionsVersion, sessionPermissionsVersion)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refresh token invalid");
        }
    }

    public CurrentUserDTO currentUser() {
        long start = System.nanoTime();
        try {
            CurrentUser currentUser = requireAuthenticatedCurrentUser();
            CurrentUserDTO cached = getCachedCurrentUser(currentUser);
            if (cached != null) {
                return cached;
            }
            AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                    .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session has expired"));
            CurrentUserDTO resolved = resolveCurrentUserFromSession(session);
            putCachedCurrentUser(session, resolved);
            return resolved;
        } finally {
            stopAuthTimer(authCurrentUserTimer, start);
        }
    }

    public CurrentUserDTO currentUserBySessionId(String sessionId) {
        String normalizedSessionId = requireSessionId(sessionId);
        AuthSession session = authSessionStore.findBySessionId(normalizedSessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session has expired"));
        return resolveCurrentUserFromSession(session);
    }

    public CurrentUserDTO currentUserBySessionId(String sessionId, Long expectedUserId, Integer expectedSessionVersion) {
        if (System.nanoTime() >= 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Full session identity snapshot is required");
        }
        String normalizedSessionId = requireSessionId(sessionId);
        Long normalizedExpectedUserId = normalizeExpectedUserId(expectedUserId);
        Integer normalizedExpectedSessionVersion = normalizeExpectedSessionVersion(expectedSessionVersion);
        AuthSession session = authSessionStore.findBySessionId(normalizedSessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session has expired"));
        if (normalizedExpectedUserId != null && !Objects.equals(normalizedExpectedUserId, session.getUserId())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Token user does not match session");
        }
        if (normalizedExpectedSessionVersion != null && !Objects.equals(normalizedExpectedSessionVersion, session.getSessionVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Session version does not match");
        }
        return resolveCurrentUserFromSession(session);
    }

    public CurrentUserDTO currentUserBySessionId(
            String sessionId,
            Long expectedUserId,
            String expectedUserUuid,
            Integer expectedSessionVersion,
            String expectedPermissionsVersion,
            Long expectedSimulatedRoleId
    ) {
        String normalizedSessionId = requireSessionId(sessionId);
        Long normalizedExpectedUserId = requireExpectedUserId(expectedUserId);
        String normalizedExpectedUserUuid = requireExpectedText(expectedUserUuid, "Expected user uuid is required");
        Integer normalizedExpectedSessionVersion = requireExpectedSessionVersion(expectedSessionVersion);
        String normalizedExpectedPermissionsVersion = requireExpectedText(expectedPermissionsVersion, "Expected permissions version is required");
        Long normalizedExpectedSimulatedRoleId = normalizeSimulatedRoleId(expectedSimulatedRoleId);
        AuthSession session = authSessionStore.findBySessionId(normalizedSessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session expired"));
        if (!Objects.equals(normalizedExpectedUserId, session.getUserId())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token/session user mismatch");
        }
        if (!normalizedExpectedUserUuid.equals(session.getUserUuid())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token/session user mismatch");
        }
        if (!Objects.equals(normalizedExpectedSessionVersion, session.getSessionVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token/session version mismatch");
        }
        if (!Objects.equals(normalizedExpectedSimulatedRoleId, normalizeSimulatedRoleId(session.getSimulatedRoleId()))) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token/session simulated role mismatch");
        }
        CurrentUserDTO currentUser = resolveCurrentUserFromSession(session);
        if (!normalizedExpectedPermissionsVersion.equals(currentUser.permissionsVersion())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "token/session permissions mismatch");
        }
        return currentUser;
    }

    public SimulatedRoleSwitchResponseDTO switchSimulatedRole(SimulatedRoleSwitchRequest request) {
        long start = System.nanoTime();
        try {
            CurrentUser currentUser = requireAuthenticatedCurrentUser();
            AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                    .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session has expired"));
            SystemUserSnapshotDTO user = requireTrustedActiveSessionProfile(session);
            Long requestedRoleId = normalizeSimulatedRoleId(request == null ? null : request.roleId());
            PermissionSnapshotDTO snapshot = requestedRoleId == null
                    ? systemInternalApi.permissionSnapshot(user.userId(), user.userUuid())
                    : systemInternalApi.simulatedRolePermissionSnapshot(user.userId(), user.userUuid(), requestedRoleId);
            String previousCurrentUserCacheKey = currentUserCacheKey(session);
            session.setSimulatedRoleId(requestedRoleId);
            session.setLastActivityAt(Instant.now());
            session.setRefreshTokenId(UUID.randomUUID().toString());
            hydrateSessionPermissionSnapshotOnly(session, snapshot);
            authSessionStore.save(session, true);
            if (StringUtils.hasText(previousCurrentUserCacheKey)) {
                currentUserCache.invalidate(previousCurrentUserCacheKey);
            }
            invalidateAuthBootstrapCache(session);
            CurrentUserDTO resolved = currentUserFromProfileAndSession(user, session);
            putCachedCurrentUser(session, resolved);
            return new SimulatedRoleSwitchResponseDTO(
                    resolved,
                    jwtTokenService.generateAccessToken(session),
                    jwtTokenService.generateRefreshToken(session, session.getRefreshTokenId()),
                    "Bearer",
                    jwtTokenService.getAccessTokenExpireSeconds()
            );
        } finally {
            stopAuthTimer(authCurrentUserTimer, start);
        }
    }

    public AuthBootstrapDTO bootstrap() {
        long start = System.nanoTime();
        try {
            CurrentUser currentUser = requireAuthenticatedCurrentUser();
            AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                    .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "Session has expired"));

            reconcileInitialPasswordState(session);
            ResolvedAuthBootstrapCacheKey authBootstrapCacheKey = resolveAuthBootstrapCacheKey(session);
            AuthBootstrapDTO cachedBootstrap = getAuthBootstrap(session, authBootstrapCacheKey);
            if (cachedBootstrap != null) {
                return cachedBootstrap;
            }

            CompletableFuture<CurrentUserDTO> bootstrapUserFuture = CompletableFuture.supplyAsync(() -> resolveCurrentUserFromSession(session), BLOCKING_IO_EXECUTOR);
            CompletableFuture<com.lumira.api.system.SecuritySettingsDTO> securitySettingsFuture = CompletableFuture.supplyAsync(securitySettingsService::snapshot, BLOCKING_IO_EXECUTOR);
            CurrentUserDTO bootstrapUser = bootstrapUserFuture.join();
            com.lumira.api.system.SecuritySettingsDTO securitySettings = securitySettingsFuture.join();
            if (Boolean.TRUE.equals(bootstrapUser.requiresPasswordChange())) {
                return new AuthBootstrapDTO(bootstrapUser, securitySettings);
            }
            AuthBootstrapDTO bootstrap = withPostLoginBootstrap(
                    bootstrapUser,
                    securitySettings,
                    authBootstrapCacheKey == null ? null : authBootstrapCacheKey.readModelVersions()
            );
            authBootstrapCacheRefreshes.increment();
            putAuthBootstrap(authBootstrapCacheKey, bootstrap);
            return bootstrap;
        } finally {
            stopAuthTimer(authBootstrapTimer, start);
        }
    }

    private CurrentUserDTO resolveCurrentUserFromSession(AuthSession session) {
        reconcileInitialPasswordState(session);
        if (hasCurrentUserSnapshot(session) && refreshPermissionSnapshotVersionIfNeeded(session)) {
            return currentUserFromProfileAndSession(requireTrustedActiveSessionProfile(session), session);
        }
        CurrentUserSessionHydration hydration = loadCurrentUserSessionHydration(session);
        SystemUserSnapshotDTO user = hydration.user();
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "Session user was not found");
        }
        requireEnabledSessionUser(user);
        return currentUserBySession(session, user, hydration.snapshot());
    }

    private AuthBootstrapDTO withPostLoginBootstrap(
            CurrentUserDTO currentUser,
            com.lumira.api.system.SecuritySettingsDTO securitySettings
    ) {
        return withPostLoginBootstrap(currentUser, securitySettings, null);
    }

    private AuthBootstrapDTO withPostLoginBootstrap(
            CurrentUserDTO currentUser,
            com.lumira.api.system.SecuritySettingsDTO securitySettings,
            AuthReadModelVersionProvider.AuthBootstrapReadModelVersions readModelVersions
    ) {
        if (authPostLoginBootstrapProvider == null) {
            return new AuthBootstrapDTO(currentUser, securitySettings);
        }
        AuthPostLoginBootstrapProvider.AuthPostLoginBootstrapPayload payload = authPostLoginBootstrapProvider.load(
                currentUser,
                readModelVersions
        );
        return new AuthBootstrapDTO(
                currentUser,
                securitySettings,
                payload == null || payload.menuTree() == null ? List.of() : List.copyOf(payload.menuTree()),
                payload == null || payload.availablePlugins() == null ? List.of() : List.copyOf(payload.availablePlugins()),
                payload == null || payload.runtimeAppearanceSettings() == null
                        ? java.util.Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(payload.runtimeAppearanceSettings()))
        );
    }

    public List<LoginResponseDTO.SecondFactorOptionDTO> verificationProviders() {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        List<com.lumira.api.system.VerificationProviderDTO> providers = systemInternalApi.listVerificationProviders(
                currentUser.getUserId(),
                currentUser.getUserUuid()
        );
        if (providers == null) {
            return List.of();
        }
        return providers.stream().map(this::toOption).toList();
    }

    public com.lumira.api.system.VerificationProviderDTO verificationProvider(String factorCode) {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        return systemInternalApi.verificationProvider(currentUser.getUserId(), currentUser.getUserUuid(), factorCode);
    }

    public com.lumira.api.system.VerificationBindingChallengeDTO verificationBind(String factorCode, VerificationBindRequest request) {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        return systemInternalApi.bindVerificationProvider(currentUser.getUserId(), currentUser.getUserUuid(), factorCode, request);
    }

    public Boolean verificationUnbind(String factorCode, SecondFactorCompleteRequest request) {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        if (!factorCode.equalsIgnoreCase(request.factorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "婵°倗濮撮惌渚€鎯佹径鎰闁荤喐澹嗙涵鈧繛鎴炴尭缁夊浜搁鈧弻?");
        }
        return systemInternalApi.unbindVerificationProvider(currentUser.getUserId(), currentUser.getUserUuid(), factorCode, request);
    }

    public com.lumira.api.system.VerificationChallengeDTO verificationChallenge(String factorCode) {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        return systemInternalApi.verificationChallenge(currentUser.getUserId(), currentUser.getUserUuid(), factorCode);
    }

    public com.lumira.api.system.VerificationVerificationDTO verificationVerify(String factorCode, SecondFactorCompleteRequest request) {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        if (!factorCode.equalsIgnoreCase(request.factorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Verification factor does not match request");
        }
        return systemInternalApi.verificationVerify(
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                factorCode,
                request.challengeId(),
                request.verificationCode()
        );
    }

    public LoginResponseDTO loginVerifiedUser(Long userId, String userUuid, HttpServletRequest request) {
        return loginVerifiedUser(userId, userUuid, request, "PASSKEY");
    }

    private LoginResponseDTO loginVerifiedUser(Long userId, String userUuid, HttpServletRequest request, String loginType) {
        Long normalizedUserId = requireVerifiedLoginUserId(userId, userUuid);
        String normalizedUserUuid = userUuid.trim();
        CompletableFuture<SystemUserSnapshotDTO> userFuture = supplyBlockingIo(() -> systemInternalApi.findUserProfileById(normalizedUserId));
        SystemUserSnapshotDTO user = userFuture.join();
        if (user != null && (!StringUtils.hasText(user.userUuid()) || !user.userUuid().trim().equals(normalizedUserUuid))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Verified user identity mismatch");
        }
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "Verified login user was not found");
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "Verified login user is disabled: " + user.username(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(user.userId(), user.userUuid());
        cachePermissionSnapshotVersion(user.userId(), user.userUuid(), snapshot);
        String loginIp = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");
        AuthSession session = buildSession(user, loginIp, userAgent, snapshot, requiresInitialAdminPasswordChange(user), loginType);
        saveSessionWithMultiDevicePolicy(session);
        recordLoginAudit(user.userId(), user.userUuid(), user.username(), loginType, "SUCCESS", null, loginIp, userAgent);
        return toLoginResponse(session, user, snapshot);
    }

    private Long requireVerifiedLoginUserId(Long userId, String userUuid) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Verified login user is required");
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Verified login user uuid is required");
        }
        return userId;
    }

    private void recordLoginAudit(
            Long userId,
            String userUuid,
            String username,
            String loginType,
            String loginResult,
            String failReason,
            String loginIp,
            String userAgent
    ) {
        try {
            systemInternalApi.recordLoginAudit(new LoginAuditRecordRequestDTO(
                    userId,
                    userUuid,
                    username,
                    loginType,
                    loginResult,
                    failReason,
                    loginIp,
                    userAgent
            ));
        } catch (Exception ex) {
            log.warn("Failed to record login audit username={} loginType={} loginResult={}", username, loginType, loginResult, ex);
        }
    }

    private LoginResponseDTO toPendingSecondFactorResponse(
            SystemUserSnapshotDTO user,
            PermissionSnapshotDTO snapshot,
            List<LoginResponseDTO.SecondFactorOptionDTO> secondFactorOptions
    ) {
        LoginResponseDTO pending = new LoginResponseDTO();
        pending.setUser(toAuthUser(user, snapshot, null));
        pending.setRequiresSecondFactor(Boolean.TRUE);
        pending.setSecondFactorOptions(secondFactorOptions);
        pending.setRequiresCaptcha(Boolean.FALSE);
        return pending;
    }

    private List<LoginResponseDTO.SecondFactorOptionDTO> loadLoginSecondFactorOptions(Long userId, String userUuid) {
        List<LoginResponseDTO.SecondFactorOptionDTO> options = systemInternalApi.listLoginSecondFactorOptions(userId, userUuid);
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        return options.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private AuthSession buildSession(
            SystemUserSnapshotDTO user,
            String loginIp,
            String userAgent,
            PermissionSnapshotDTO snapshot,
            boolean requiresPasswordChange,
            String loginType
    ) {
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.userId());
        session.setUserUuid(user.userUuid());
        session.setUsername(user.username());
        session.setLoginType(loginType);
        session.setLoginTime(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setExpireTime(Instant.now().plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
        session.setSessionVersion(1);
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        session.setRefreshTokenId(UUID.randomUUID().toString());
        hydrateSessionSnapshot(session, user, snapshot, requiresPasswordChange);
        return session;
    }

    private void hydrateSessionSnapshot(AuthSession session, SystemUserSnapshotDTO user, PermissionSnapshotDTO snapshot, boolean requiresPasswordChange) {
        session.setUserUuid(user.userUuid());
        session.setUsername(user.username());
        session.setPermissionsVersion(snapshot.version());
        session.setPermissions(snapshot.permissions() == null ? List.of() : List.copyOf(snapshot.permissions()));
        session.setRoleIds(snapshot.roleIds() == null ? List.of() : List.copyOf(snapshot.roleIds()));
        session.setPrimaryDeptId(snapshot.primaryDeptId());
        session.setDeptIds(snapshot.deptIds() == null ? List.of() : List.copyOf(snapshot.deptIds()));
        session.setDescendantDeptIds(snapshot.descendantDeptIds() == null ? List.of() : List.copyOf(snapshot.descendantDeptIds()));
        session.setDataScopes(snapshot.dataScopes() == null ? List.of() : List.copyOf(snapshot.dataScopes()));
        session.setRequiresPasswordChange(requiresPasswordChange);
        session.setDefaultHomePath(snapshot.defaultHomePath());
    }

    private void saveSessionWithMultiDevicePolicy(AuthSession session) {
        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(session.getUserId(), session.getUserUuid(), true);
        }
        authSessionStore.save(session, true);
    }

    private LoginResponseDTO toLoginResponse(AuthSession session, SystemUserSnapshotDTO user, PermissionSnapshotDTO snapshot) {
        return toLoginResponse(session, user, snapshot, false);
    }

    private LoginResponseDTO toLoginResponse(AuthSession session, SystemUserSnapshotDTO user, PermissionSnapshotDTO snapshot, boolean requiresPasswordChange) {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, session.getRefreshTokenId()));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user, snapshot, session.getSessionId()));
        response.setRequiresSecondFactor(Boolean.FALSE);
        response.setRequiresCaptcha(Boolean.FALSE);
        response.setRequiresPasswordChange(requiresPasswordChange);
        return response;
    }

    private AuthUserDTO toAuthUser(SystemUserSnapshotDTO user, PermissionSnapshotDTO snapshot, String sessionId) {
        return new AuthUserDTO(
                user.userId(),
                user.userUuid(),
                user.username(),
                user.nickname(),
                user.realName(),
                user.avatarUrl(),
                user.mobile(),
                user.email(),
                user.birthMonth(),
                user.gender(),
                user.region(),
                user.availableTime(),
                user.idCardNumber(),
                user.locale(),
                sessionId,
                snapshot.version(),
                1,
                snapshot.permissions(),
                snapshot.roleIds()
        );
    }

    private LoginResponseDTO.SecondFactorOptionDTO toOption(com.lumira.api.system.VerificationProviderDTO provider) {
        LoginResponseDTO.SecondFactorOptionDTO option = new LoginResponseDTO.SecondFactorOptionDTO();
        option.setFactorCode(provider.getFactorCode());
        option.setFactorName(provider.getFactorName());
        option.setChallengeId(provider.getChallengeId());
        option.setMaskedContact(provider.getMaskedContact());
        option.setPromptMessage(provider.getPromptMessage());
        return option;
    }

    private <T> CompletableFuture<T> supplyBlockingIo(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, BLOCKING_IO_EXECUTOR);
    }

    private CurrentUserDTO currentUserBySession(AuthSession session, SystemUserSnapshotDTO user, PermissionSnapshotDTO snapshot) {
        hydrateSessionSnapshot(session, user, snapshot, requiresInitialAdminPasswordChange(user));
        authSessionStore.save(session, false);
        return currentUserFromProfileAndSession(user, session);
    }

    private CurrentUserSessionHydration loadCurrentUserSessionHydration(AuthSession session) {
        Long userId = session == null ? null : session.getUserId();
        String normalizedUserUuid = normalizeText(session == null ? null : session.getUserUuid());
        if (userId == null || !StringUtils.hasText(normalizedUserUuid)) {
            return new CurrentUserSessionHydration(null, null);
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserProfileById(userId);
        if (user == null || !normalizedUserUuid.equals(normalizeText(user.userUuid()))) {
            return new CurrentUserSessionHydration(null, null);
        }
        PermissionSnapshotDTO snapshot = loadPermissionSnapshotFromSystemRemote(user.userId(), user.userUuid(), session == null ? null : session.getSimulatedRoleId());
        return new CurrentUserSessionHydration(user, snapshot);
    }

    private SystemUserSnapshotDTO requireTrustedActiveSessionProfile(AuthSession session) {
        String sessionUserUuid = normalizeText(session == null ? null : session.getUserUuid());
        if (session == null || session.getUserId() == null || !StringUtils.hasText(sessionUserUuid)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Session user identity is invalid");
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserProfileById(session.getUserId());
        if (user == null || !sessionUserUuid.equals(normalizeText(user.userUuid()))) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Session user changed");
        }
        requireEnabledSessionUser(user);
        return user;
    }

    private void requireEnabledSessionUser(SystemUserSnapshotDTO user) {
        if (user == null || !"ENABLED".equalsIgnoreCase(normalizeText(user.status()))) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Session user is disabled");
        }
    }

    private List<CurrentUserRoleOptionDTO> loadCurrentUserRoleOptions(Long userId, String userUuid) {
        if (userId == null || !StringUtils.hasText(userUuid)) {
            return List.of();
        }
        List<CurrentUserRoleOptionDTO> roleOptions = systemInternalApi.userRoleOptions(userId, userUuid);
        return roleOptions == null ? List.of() : List.copyOf(roleOptions);
    }

    private boolean isTrustedActiveUser(Long userId, String userUuid) {
        String normalizedUserUuid = normalizeText(userUuid);
        if (userId == null || !StringUtils.hasText(normalizedUserUuid)) {
            return false;
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(userId);
        return user != null
                && normalizedUserUuid.equals(normalizeText(user.userUuid()))
                && "ENABLED".equalsIgnoreCase(normalizeText(user.status()));
    }

    public double authLoginP95Millis() {
        return timerP95Millis(authLoginTimer);
    }

    public double authCurrentUserP95Millis() {
        return timerP95Millis(authCurrentUserTimer);
    }

    public double authBootstrapP95Millis() {
        return timerP95Millis(authBootstrapTimer);
    }

    public double authRefreshTokenP95Millis() {
        return timerP95Millis(authRefreshTokenTimer);
    }

    private double timerP95Millis(Timer timer) {
        if (timer == null || timer.count() == 0) {
            return 0.0;
        }
        for (var percentile : timer.takeSnapshot().percentileValues()) {
            if (Double.compare(percentile.percentile(), 0.95) == 0) {
                return percentile.value(TimeUnit.MILLISECONDS);
            }
        }
        return timer.max(TimeUnit.MILLISECONDS);
    }

    private void stopAuthTimer(Timer timer, long startNanos) {
        if (timer == null) {
            return;
        }
        timer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private Timer createTimerIfAvailable(MeterRegistry meterRegistry, String timerName) {
        if (meterRegistry == null) {
            return null;
        }
        return Timer.builder(timerName)
                .publishPercentiles(0.95)
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public long permissionSnapshotVersionCacheHits() {
        return permissionSnapshotVersionCacheHits.sum();
    }

    public long permissionSnapshotVersionCacheMisses() {
        return permissionSnapshotVersionCacheMisses.sum();
    }

    public long permissionSnapshotVersionCacheRefreshes() {
        return permissionSnapshotVersionCacheRefreshes.sum();
    }

    public long permissionSnapshotVersionCacheFallbacks() {
        return permissionSnapshotVersionCacheFallbacks.sum();
    }

    public double permissionSnapshotVersionCacheHitRatio() {
        long misses = permissionSnapshotVersionCacheMisses.sum();
        long hits = permissionSnapshotVersionCacheHits.sum();
        long total = misses + hits;
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }

    public long authBootstrapCacheHits() {
        return authBootstrapCacheHits.sum();
    }

    public long authBootstrapCacheMisses() {
        return authBootstrapCacheMisses.sum();
    }

    public long authBootstrapCacheRefreshes() {
        return authBootstrapCacheRefreshes.sum();
    }

    public long authBootstrapCacheAlignmentRejects() {
        return authBootstrapCacheAlignmentRejects.sum();
    }

    public double authBootstrapCacheHitRatio() {
        long misses = authBootstrapCacheMisses.sum();
        long hits = authBootstrapCacheHits.sum();
        long total = misses + hits;
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }

    private CurrentUserDTO getCachedCurrentUser(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)
                || Boolean.TRUE.equals(currentUser.getRequiresPasswordChange())) {
            return null;
        }
        String key = currentUserCacheKey(currentUser);
        if (!StringUtils.hasText(key)) {
            return null;
        }
        CurrentUserDTO cached = currentUserCache.getIfPresent(key);
        if (cached == null) {
            return null;
        }
        if (!isTrustedActiveUser(currentUser.getUserId(), currentUser.getUserUuid())) {
            currentUserCache.invalidate(key);
            return null;
        }
        return cached;
    }

    private void putCachedCurrentUser(AuthSession session, CurrentUserDTO currentUser) {
        String key = currentUserCacheKey(session);
        if (StringUtils.hasText(key) && currentUser != null) {
            currentUserCache.put(key, currentUser);
        }
    }

    private String currentUserCacheKey(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)
                || !StringUtils.hasText(currentUser.getPermissionsVersion())) {
            return null;
        }
        return currentUser.getSessionId()
                + "#" + normalizeSimulatedRoleIdForCache(currentUser.getSimulatedRoleId())
                + "#" + currentUser.getPermissionsVersion();
    }

    private Long currentUserId() {
        CurrentUser currentUser = requireAuthenticatedCurrentUser();
        return currentUser.getUserId();
    }

    private CurrentUser requireAuthenticatedCurrentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser;
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private Long normalizeSimulatedRoleId(Long roleId) {
        if (roleId == null) {
            return null;
        }
        if (roleId <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "roleId must be positive");
        }
        return roleId;
    }

    private String normalizeSimulatedRoleIdForCache(Long roleId) {
        return roleId == null ? "self" : Long.toString(roleId);
    }

    private String requireSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Session id is required");
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Session id is invalid");
        }
        return normalized;
    }

    private Long normalizeExpectedUserId(Long expectedUserId) {
        if (expectedUserId == null) {
            return null;
        }
        if (expectedUserId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected user id is invalid");
        }
        return expectedUserId;
    }

    private Integer normalizeExpectedSessionVersion(Integer expectedSessionVersion) {
        if (expectedSessionVersion == null) {
            return null;
        }
        if (expectedSessionVersion <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected session version is invalid");
        }
        return expectedSessionVersion;
    }

    private Long requireExpectedUserId(Long expectedUserId) {
        Long normalized = normalizeExpectedUserId(expectedUserId);
        if (normalized == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected user id is required");
        }
        return normalized;
    }

    private Integer requireExpectedSessionVersion(Integer expectedSessionVersion) {
        Integer normalized = normalizeExpectedSessionVersion(expectedSessionVersion);
        if (normalized == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected session version is required");
        }
        return normalized;
    }

    private String requireExpectedText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, message);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentUserCacheKey(AuthSession session) {
        if (session == null
                || !StringUtils.hasText(session.getSessionId())
                || !StringUtils.hasText(session.getPermissionsVersion())) {
            return null;
        }
        return session.getSessionId()
                + "#" + normalizeSimulatedRoleIdForCache(session.getSimulatedRoleId())
                + "#" + session.getPermissionsVersion();
    }

    private AuthBootstrapDTO getAuthBootstrap(AuthSession session, ResolvedAuthBootstrapCacheKey resolvedCacheKey) {
        String key = resolvedCacheKey == null ? null : resolvedCacheKey.cacheKey();
        if (!StringUtils.hasText(key)) {
            authBootstrapCacheMisses.increment();
            return null;
        }
        AuthBootstrapCache cache = authBootstrapCache.getIfPresent(key);
        if (cache == null) {
            invalidateStaleAuthBootstrapKeyIfNeeded(
                    resolvedCacheKey == null ? null : resolvedCacheKey.sessionId(),
                    key
            );
            authBootstrapCacheMisses.increment();
            return null;
        }
        if (cache.isExpired()) {
            authBootstrapCache.invalidate(key);
            String sessionId = session.getSessionId();
            if (StringUtils.hasText(sessionId)) {
                String currentSessionKey = authBootstrapSessionCacheKeys.getIfPresent(sessionId);
                if (key.equals(currentSessionKey)) {
                    authBootstrapSessionCacheKeys.invalidate(sessionId);
                }
            }
            authBootstrapCacheMisses.increment();
            return null;
        }
        if (!isAuthBootstrapCacheAligned(session)) {
            authBootstrapCacheAlignmentRejects.increment();
            invalidateAuthBootstrapCache(session);
            authBootstrapCacheMisses.increment();
            return null;
        }
        authBootstrapCacheHits.increment();
        return cache.bootstrap;
    }

    private boolean isAuthBootstrapCacheAligned(AuthSession session) {
        if (session == null || !hasCurrentUserSnapshot(session)) {
            return false;
        }

        Long userId = session.getUserId();
        if (userId == null) {
            return false;
        }

        String sessionPermissionsVersion = session.getPermissionsVersion();
        if (!StringUtils.hasText(sessionPermissionsVersion)) {
            return false;
        }

        PermissionSnapshotVersionCache cachedVersion = getPermissionSnapshotVersionCache(session);
        if (cachedVersion != null) {
            if (sessionPermissionsVersion.equals(cachedVersion.version)) {
                return true;
            }
            Long readModelVersion = getPermissionReadModelVersion();
            Long parsedSessionVersion = parsePermissionSnapshotVersion(sessionPermissionsVersion);
            return readModelVersion != null && parsedSessionVersion != null && readModelVersion.equals(parsedSessionVersion);
        }

        Long readModelVersion = getPermissionReadModelVersion();
        Long parsedSessionVersion = parsePermissionSnapshotVersion(sessionPermissionsVersion);
        if (readModelVersion == null || parsedSessionVersion == null) {
            return true;
        }
        if (readModelVersion.equals(parsedSessionVersion)) {
            cachePermissionSnapshotVersion(session, sessionPermissionsVersion);
            return true;
        }
        return false;
    }

    private void putAuthBootstrap(ResolvedAuthBootstrapCacheKey resolvedCacheKey, AuthBootstrapDTO bootstrap) {
        if (resolvedCacheKey == null || !StringUtils.hasText(resolvedCacheKey.cacheKey()) || bootstrap == null) {
            return;
        }
        String sessionId = resolvedCacheKey.sessionId();
        String key = resolvedCacheKey.cacheKey();
        String previousKey = authBootstrapSessionCacheKeys.getIfPresent(sessionId);
        if (previousKey != null && !previousKey.equals(key)) {
            authBootstrapCache.invalidate(previousKey);
        }
        authBootstrapCache.put(key, new AuthBootstrapCache(bootstrap, System.currentTimeMillis() + authBootstrapCacheTtlMillis));
        authBootstrapSessionCacheKeys.put(sessionId, key);
    }

    private void invalidateAuthBootstrapCache(AuthSession session) {
        if (session == null || !StringUtils.hasText(session.getSessionId())) {
            return;
        }
        String currentUserKey = currentUserCacheKey(session);
        if (StringUtils.hasText(currentUserKey)) {
            currentUserCache.invalidate(currentUserKey);
        }
        String key = authBootstrapSessionCacheKeys.getIfPresent(session.getSessionId());
        authBootstrapSessionCacheKeys.invalidate(session.getSessionId());
        if (StringUtils.hasText(key)) {
            authBootstrapCache.invalidate(key);
        }
    }

    private ResolvedAuthBootstrapCacheKey resolveAuthBootstrapCacheKey(AuthSession session) {
        if (session == null
                || !StringUtils.hasText(session.getSessionId())
                || !StringUtils.hasText(session.getPermissionsVersion())) {
            return null;
        }
        AuthReadModelVersionProvider.AuthBootstrapReadModelVersions readModelVersions = loadAuthBootstrapReadModelVersions();
        AuthBootstrapCacheVersion version = resolveAuthBootstrapCacheVersion(session, readModelVersions);
        return new ResolvedAuthBootstrapCacheKey(
                session.getSessionId(),
                session.getSessionId() + "#" + version.cacheKey(),
                readModelVersions
        );
    }

    private void invalidateStaleAuthBootstrapKeyIfNeeded(String sessionId, String key) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(key)) {
            return;
        }
        String previousKey = authBootstrapSessionCacheKeys.getIfPresent(sessionId);
        if (StringUtils.hasText(previousKey) && !previousKey.equals(key)) {
            authBootstrapCache.invalidate(previousKey);
            authBootstrapSessionCacheKeys.invalidate(sessionId);
        }
    }

    private AuthBootstrapCacheVersion resolveAuthBootstrapCacheVersion(AuthSession session) {
        return resolveAuthBootstrapCacheVersion(session, loadAuthBootstrapReadModelVersions());
    }

    private AuthBootstrapCacheVersion resolveAuthBootstrapCacheVersion(
            AuthSession session,
            AuthReadModelVersionProvider.AuthBootstrapReadModelVersions batchVersions
    ) {
        if (batchVersions != null) {
            return new AuthBootstrapCacheVersion(
                    session.getSimulatedRoleId(),
                    session.getPermissionsVersion(),
                    batchVersions.publicBootstrapVersion(),
                    batchVersions.runtimeAppearanceVersion(),
                    batchVersions.pluginBootstrapVersion(),
                    batchVersions.platformMenuTreeVersion()
            );
        }
        Long publicBootstrapVersion = readReadModelVersion(
                READ_MODEL_CONTEXT_PLATFORM,
                READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP
        );
        if (authPostLoginBootstrapProvider == null) {
            return new AuthBootstrapCacheVersion(
                    session.getSimulatedRoleId(),
                    session.getPermissionsVersion(),
                    publicBootstrapVersion,
                    null,
                    null,
                    null
            );
        }
        return new AuthBootstrapCacheVersion(
                session.getSimulatedRoleId(),
                session.getPermissionsVersion(),
                publicBootstrapVersion,
                readReadModelVersion(READ_MODEL_CONTEXT_PLATFORM, READ_MODEL_SCOPE_PLATFORM_RUNTIME_APPEARANCE),
                readReadModelVersion(READ_MODEL_CONTEXT_PLUGIN, READ_MODEL_SCOPE_PLUGIN_BOOTSTRAP),
                readReadModelVersion(READ_MODEL_CONTEXT_PLATFORM, READ_MODEL_SCOPE_PLATFORM_MENU_TREE)
        );
    }

    private AuthReadModelVersionProvider.AuthBootstrapReadModelVersions loadAuthBootstrapReadModelVersions() {
        if (authReadModelVersionProvider == null) {
            return null;
        }
        AuthBootstrapReadModelVersionsCache cached = authBootstrapReadModelVersionsCache;
        if (cached != null && !cached.isExpired()) {
            return cached.versions();
        }
        synchronized (this) {
            cached = authBootstrapReadModelVersionsCache;
            if (cached != null && !cached.isExpired()) {
                return cached.versions();
            }
            try {
                AuthReadModelVersionProvider.AuthBootstrapReadModelVersions loaded = authReadModelVersionProvider.loadBootstrapVersions();
                if (loaded == null) {
                    return null;
                }
                authBootstrapReadModelVersionsCache = new AuthBootstrapReadModelVersionsCache(
                        loaded,
                        System.currentTimeMillis() + BOOTSTRAP_READ_MODEL_VERSION_CACHE_TTL_MILLIS
                );
                return loaded;
            } catch (Exception exception) {
                log.debug("Failed to batch-load auth bootstrap read-model versions", exception);
                return null;
            }
        }
    }

    private void reconcileInitialPasswordState(AuthSession session) {
        if (session == null || !Boolean.TRUE.equals(session.getRequiresPasswordChange())) {
            return;
        }
        String sessionUserUuid = normalizeText(session.getUserUuid());
        if (session.getUserId() == null || !StringUtils.hasText(sessionUserUuid)) {
            return;
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(session.getUserId());
        if (user == null
                || !sessionUserUuid.equals(normalizeText(user.userUuid()))
                || requiresInitialAdminPasswordChange(user)) {
            return;
        }
        session.setRequiresPasswordChange(Boolean.FALSE);
        authSessionStore.save(session, false);
        invalidateAuthBootstrapCache(session);
    }

    private CurrentUserDTO currentUserFromProfileAndSession(SystemUserSnapshotDTO user, AuthSession session) {
        requireTrustedSessionSnapshot(session);
        if (user == null
                || user.userId() == null
                || !Objects.equals(user.userId(), session.getUserId())
                || !normalizeText(session.getUserUuid()).equals(normalizeText(user.userUuid()))
                || !StringUtils.hasText(user.username())) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Session user profile is invalid");
        }
        List<CurrentUserRoleOptionDTO> availableRoles = loadCurrentUserRoleOptions(user.userId(), user.userUuid());
        return new CurrentUserDTO(
                user.userId(),
                user.userUuid(),
                user.username(),
                user.nickname(),
                user.realName(),
                user.avatarUrl(),
                user.mobile(),
                user.email(),
                user.birthMonth(),
                user.gender(),
                user.region(),
                user.availableTime(),
                user.idCardNumber(),
                user.locale(),
                session.getSimulatedRoleId(),
                availableRoles,
                session.getSessionId(),
                session.getPermissionsVersion(),
                session.getSessionVersion(),
                session.getPermissions(),
                session.getRoleIds(),
                session.getPrimaryDeptId(),
                session.getDeptIds(),
                session.getDescendantDeptIds(),
                session.getDataScopes(),
                Boolean.TRUE.equals(session.getRequiresPasswordChange()),
                session.getDefaultHomePath()
        );
    }

    private boolean refreshPermissionSnapshotVersionIfNeeded(AuthSession session) {
        if (session == null || session.getUserId() == null) {
            return false;
        }
        if (!hasCurrentUserSnapshot(session)) {
            permissionSnapshotVersionCacheFallbacks.increment();
            return false;
        }
        String sessionPermissionsVersion = session.getPermissionsVersion();
        if (!StringUtils.hasText(sessionPermissionsVersion)) {
            permissionSnapshotVersionCacheFallbacks.increment();
            return false;
        }

        PermissionSnapshotVersionCache cachedVersion = getPermissionSnapshotVersionCache(session);
        if (cachedVersion != null) {
            permissionSnapshotVersionCacheHits.increment();
            if (sessionPermissionsVersion.equals(cachedVersion.version)) {
                return true;
            }
            Long readModelVersion = getPermissionReadModelVersion();
            Long sessionVersion = parsePermissionSnapshotVersion(sessionPermissionsVersion);
            if (readModelVersion != null && readModelVersion > 0 && sessionVersion != null && readModelVersion.equals(sessionVersion)) {
                cachePermissionSnapshotVersion(session, sessionPermissionsVersion);
                return true;
            }
            permissionSnapshotVersionCacheRefreshes.increment();
            return ensurePermissionSnapshotFromSystem(session, readModelVersion);
        }

        Long readModelVersion = getPermissionReadModelVersion();
        if (readModelVersion != null && readModelVersion > 0) {
            Long sessionVersion = parsePermissionSnapshotVersion(sessionPermissionsVersion);
            if (sessionVersion != null && readModelVersion.equals(sessionVersion)) {
                permissionSnapshotVersionCacheMisses.increment();
                cachePermissionSnapshotVersion(session, sessionPermissionsVersion);
                return true;
            }
            if (sessionVersion == null || !readModelVersion.equals(sessionVersion)) {
                permissionSnapshotVersionCacheMisses.increment();
                return ensurePermissionSnapshotFromSystem(session, readModelVersion);
            }
        }

        permissionSnapshotVersionCacheMisses.increment();
        return ensurePermissionSnapshotFromSystem(session, null);

    }

    private boolean ensurePermissionSnapshotFromSystem(AuthSession session, Long readModelVersion) {
        PermissionSnapshotDTO snapshot = loadPermissionSnapshotFromSystemWithSingleFlight(
                session.getUserId(),
                session.getUserUuid(),
                session.getSimulatedRoleId(),
                readModelVersion
        );
        if (snapshot == null || !StringUtils.hasText(snapshot.version())) {
            permissionSnapshotVersionCacheFallbacks.increment();
            return false;
        }

        if (!snapshot.version().equals(session.getPermissionsVersion())) {
            permissionSnapshotVersionCacheRefreshes.increment();
            String previousCurrentUserCacheKey = currentUserCacheKey(session);
            hydrateSessionPermissionSnapshotOnly(session, snapshot);
            authSessionStore.save(session, false);
            if (StringUtils.hasText(previousCurrentUserCacheKey)) {
                currentUserCache.invalidate(previousCurrentUserCacheKey);
            }
            invalidateAuthBootstrapCache(session);
        }
        cachePermissionSnapshotVersion(session.getUserId(), session.getUserUuid(), session.getSimulatedRoleId(), snapshot);
        return true;
    }

    private PermissionSnapshotDTO loadPermissionSnapshotFromSystemWithSingleFlight(
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Long readModelVersion
    ) {
        PermissionSnapshotLoadInFlightKey key = new PermissionSnapshotLoadInFlightKey(userId, userUuid, simulatedRoleId, readModelVersion);
        try {
            CompletableFuture<PermissionSnapshotDTO> inFlight = permissionSnapshotLoadInFlight.get(
                    key,
                    () -> CompletableFuture.completedFuture(loadPermissionSnapshotFromSystemRemote(userId, userUuid, simulatedRoleId))
            );
            try {
                return inFlight.join();
            } catch (CompletionException exception) {
                permissionSnapshotLoadInFlight.invalidate(key);
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                log.warn("Failed to load permission snapshot for userId={} with single-flight path", userId, cause);
                return null;
            }
        } catch (ExecutionException exception) {
            permissionSnapshotLoadInFlight.invalidate(key);
            log.warn("Failed to load permission snapshot single-flight for userId={}", userId, exception);
            return null;
        }
    }

    private PermissionSnapshotDTO loadPermissionSnapshotFromSystemRemote(Long userId, String userUuid, Long simulatedRoleId) {
        try {
            if (simulatedRoleId != null) {
                return systemInternalApi.simulatedRolePermissionSnapshot(userId, userUuid, simulatedRoleId);
            }
            return systemInternalApi.permissionSnapshot(userId, userUuid);
        } catch (Exception exception) {
            log.warn("Failed to load permission snapshot for userId={} while checking cache", userId, exception);
            return null;
        }
    }

    private Long getPermissionReadModelVersion() {
        return getReadModelVersion(
                GLOBAL_PERMISSION_READ_MODEL_VERSION_CACHE_KEY,
                READ_MODEL_CONTEXT_IAM,
                READ_MODEL_SCOPE_IAM_PERMISSION_SNAPSHOT
        );
    }

    private Long getPublicBootstrapReadModelVersion() {
        return readReadModelVersion(READ_MODEL_CONTEXT_PLATFORM, READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP);
    }

    private Long readReadModelVersion(String context, String scope) {
        try {
            String cacheKey = switch (context + "/" + scope) {
                case READ_MODEL_CONTEXT_PLATFORM + "/" + READ_MODEL_SCOPE_PUBLIC_BOOTSTRAP -> GLOBAL_PUBLIC_BOOTSTRAP_READ_MODEL_VERSION_CACHE_KEY;
                case READ_MODEL_CONTEXT_PLATFORM + "/" + READ_MODEL_SCOPE_PLATFORM_RUNTIME_APPEARANCE -> GLOBAL_PLATFORM_RUNTIME_APPEARANCE_READ_MODEL_VERSION_CACHE_KEY;
                case READ_MODEL_CONTEXT_PLATFORM + "/" + READ_MODEL_SCOPE_PLATFORM_MENU_TREE -> GLOBAL_PLATFORM_MENU_TREE_READ_MODEL_VERSION_CACHE_KEY;
                case READ_MODEL_CONTEXT_PLUGIN + "/" + READ_MODEL_SCOPE_PLUGIN_BOOTSTRAP -> GLOBAL_PLUGIN_BOOTSTRAP_READ_MODEL_VERSION_CACHE_KEY;
                default -> context + "/" + scope;
            };
            return readModelVersionCache.readValue(
                    cacheKey,
                    BOOTSTRAP_READ_MODEL_VERSION_CACHE_TTL_MILLIS,
                    () -> systemInternalApi.readModelVersion(context, scope)
            );
        } catch (Exception exception) {
            log.debug("Failed to read read-model version context={} scope={}", context, scope, exception);
            return null;
        }
    }

    private Long getReadModelVersion(String cacheKey, String context, String scope) {
        Long cachedVersion = permissionReadModelVersionCache.getIfPresent(cacheKey);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        Long version = loadReadModelVersionWithSingleFlight(cacheKey, context, scope);
        if (version != null) {
            permissionReadModelVersionCache.put(cacheKey, version);
        }
        return version;
    }

    private Long loadReadModelVersionWithSingleFlight(String cacheKey, String context, String scope) {
        try {
            CompletableFuture<Long> inFlight = permissionReadModelVersionLoadInFlight.get(
                    cacheKey,
                    () -> CompletableFuture.completedFuture(loadReadModelVersion(context, scope))
            );
            return inFlight.join();
        } catch (CompletionException exception) {
            permissionReadModelVersionLoadInFlight.invalidate(cacheKey);
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.debug("Failed to load read-model version context={} scope={}", context, scope, cause);
            return null;
        } catch (ExecutionException exception) {
            permissionReadModelVersionLoadInFlight.invalidate(cacheKey);
            log.debug("Failed to load read-model version single-flight context={} scope={}", context, scope, exception);
            return null;
        }
    }

    private Long loadReadModelVersion(String context, String scope) {
        try {
            return systemInternalApi.readModelVersion(context, scope);
        } catch (Exception exception) {
            log.debug("Failed to read read-model version context={} scope={}", context, scope, exception);
            return null;
        }
    }

    private LoginCapabilitiesDTO loadLoginCapabilities() {
        LoginCapabilitiesCache cached = loginCapabilitiesCache;
        Long publicBootstrapVersion = getPublicBootstrapReadModelVersion();
        if (isLoginCapabilitiesCacheCurrent(cached, publicBootstrapVersion)) {
            return cached.capabilities;
        }
        synchronized (this) {
            cached = loginCapabilitiesCache;
            publicBootstrapVersion = getPublicBootstrapReadModelVersion();
            if (isLoginCapabilitiesCacheCurrent(cached, publicBootstrapVersion)) {
                return cached.capabilities;
            }
            LoginCapabilitiesDTO loaded;
            try {
                loaded = systemInternalApi.loginCapabilities();
            } catch (Exception exception) {
                log.warn("Failed to load login capabilities", exception);
                return fallbackLoginCapabilities(cached);
            }
            loginCapabilitiesCache = new LoginCapabilitiesCache(
                    publicBootstrapVersion,
                    loaded,
                    System.currentTimeMillis() + loginCapabilitiesCacheTtlMillis
            );
            return loaded;
        }
    }

    private boolean isLoginCapabilitiesCacheCurrent(LoginCapabilitiesCache cached, Long publicBootstrapVersion) {
        if (cached == null || cached.capabilities == null) {
            return false;
        }
        if (publicBootstrapVersion == null) {
            return !cached.isExpired();
        }
        return Objects.equals(cached.publicBootstrapVersion, publicBootstrapVersion);
    }

    private LoginCapabilitiesDTO fallbackLoginCapabilities(LoginCapabilitiesCache cached) {
        if (cached != null && cached.capabilities != null && !cached.isExpired()) {
            return cached.capabilities;
        }
        return null;
    }

    private Long parsePermissionSnapshotVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }
        String trimmed = version.trim();
        int start = trimmed.startsWith("v") ? 1 : 0;
        int colonIndex = trimmed.indexOf(':', start);
        String numericPart = colonIndex >= start ? trimmed.substring(start, colonIndex == -1 ? trimmed.length() : colonIndex) : trimmed.substring(start);
        if (!StringUtils.hasText(numericPart)) {
            return null;
        }
        try {
            return Long.parseLong(numericPart);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void hydrateSessionPermissionSnapshotOnly(AuthSession session, PermissionSnapshotDTO snapshot) {
        if (session == null || snapshot == null) {
            return;
        }
        session.setPermissionsVersion(snapshot.version());
        session.setPermissions(snapshot.permissions() == null ? List.of() : List.copyOf(snapshot.permissions()));
        session.setRoleIds(snapshot.roleIds() == null ? List.of() : List.copyOf(snapshot.roleIds()));
        session.setPrimaryDeptId(snapshot.primaryDeptId());
        session.setDeptIds(snapshot.deptIds() == null ? List.of() : List.copyOf(snapshot.deptIds()));
        session.setDescendantDeptIds(snapshot.descendantDeptIds() == null ? List.of() : List.copyOf(snapshot.descendantDeptIds()));
        session.setDataScopes(snapshot.dataScopes() == null ? List.of() : List.copyOf(snapshot.dataScopes()));
        session.setDefaultHomePath(snapshot.defaultHomePath());
    }

    private void cachePermissionSnapshotVersion(Long userId, String userUuid, PermissionSnapshotDTO snapshot) {
        cachePermissionSnapshotVersion(userId, userUuid, null, snapshot);
    }

    private void cachePermissionSnapshotVersion(Long userId, String userUuid, Long simulatedRoleId, PermissionSnapshotDTO snapshot) {
        if (userId == null || !StringUtils.hasText(userUuid) || snapshot == null || !StringUtils.hasText(snapshot.version())) {
            return;
        }
        permissionSnapshotVersionCache.put(
                new PermissionSnapshotCacheKey(userId, userUuid, simulatedRoleId),
                new PermissionSnapshotVersionCache(snapshot.version())
        );
    }

    private void cachePermissionSnapshotVersion(AuthSession session, String version) {
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getUserUuid()) || !StringUtils.hasText(version)) {
            return;
        }
        permissionSnapshotVersionCache.put(
                new PermissionSnapshotCacheKey(session.getUserId(), session.getUserUuid(), session.getSimulatedRoleId()),
                new PermissionSnapshotVersionCache(version)
        );
    }

    private PermissionSnapshotVersionCache getPermissionSnapshotVersionCache(AuthSession session) {
        if (session == null || session.getUserId() == null || !StringUtils.hasText(session.getUserUuid())) {
            return null;
        }
        return permissionSnapshotVersionCache.getIfPresent(
                new PermissionSnapshotCacheKey(session.getUserId(), session.getUserUuid(), session.getSimulatedRoleId())
        );
    }

    private boolean hasCurrentUserSnapshot(AuthSession session) {
        return isTrustedSessionIdentity(session)
                && StringUtils.hasText(session.getPermissionsVersion())
                && session.getPermissions() != null
                && session.getRoleIds() != null
                && session.getDeptIds() != null
                && session.getDescendantDeptIds() != null
                && session.getDataScopes() != null;
    }

    private void requireTrustedSessionSnapshot(AuthSession session) {
        if (!hasCurrentUserSnapshot(session)) {
            throw new BizException(ErrorCode.SESSION_EXPIRED, "Session snapshot is invalid");
        }
    }

    private boolean isTrustedSessionIdentity(AuthSession session) {
        return session != null
                && session.getUserId() != null
                && session.getUserId() > 0
                && StringUtils.hasText(session.getUserUuid())
                && StringUtils.hasText(session.getUsername())
                && StringUtils.hasText(session.getSessionId())
                && session.getSessionVersion() != null
                && session.getSessionVersion() > 0
                && requireSafeSessionIdOrNull(session.getSessionId()) != null;
    }

    private String requireSafeSessionIdOrNull(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            return null;
        }
        return normalized;
    }

    private static final class AuthBootstrapCache {
        private final AuthBootstrapDTO bootstrap;
        private final long expireAtMillis;

        private AuthBootstrapCache(AuthBootstrapDTO bootstrap, long expireAtMillis) {
            this.bootstrap = bootstrap;
            this.expireAtMillis = expireAtMillis;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    private static final class PermissionSnapshotVersionCache {
        private final String version;

        private PermissionSnapshotVersionCache(String version) {
            this.version = version;
        }
    }

    private static final class LoginCapabilitiesCache {
        private final Long publicBootstrapVersion;
        private final LoginCapabilitiesDTO capabilities;
        private final long expireAtMillis;

        private LoginCapabilitiesCache(Long publicBootstrapVersion, LoginCapabilitiesDTO capabilities, long expireAtMillis) {
            this.publicBootstrapVersion = publicBootstrapVersion;
            this.capabilities = capabilities;
            this.expireAtMillis = expireAtMillis;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    private static final class AuthBootstrapReadModelVersionsCache {
        private final AuthReadModelVersionProvider.AuthBootstrapReadModelVersions versions;
        private final long expireAtMillis;

        private AuthBootstrapReadModelVersionsCache(
                AuthReadModelVersionProvider.AuthBootstrapReadModelVersions versions,
                long expireAtMillis
        ) {
            this.versions = versions;
            this.expireAtMillis = expireAtMillis;
        }

        private AuthReadModelVersionProvider.AuthBootstrapReadModelVersions versions() {
            return versions;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    private record AuthBootstrapCacheVersion(
            Long simulatedRoleId,
            String permissionsVersion,
            Long publicBootstrapVersion,
            Long runtimeAppearanceVersion,
            Long pluginBootstrapVersion,
            Long platformMenuTreeVersion
    ) {
        private String cacheKey() {
            return "role=" + normalize(simulatedRoleId)
                    + "#" + normalize(permissionsVersion)
                    + "#public=" + normalize(publicBootstrapVersion)
                    + "#appearance=" + normalize(runtimeAppearanceVersion)
                    + "#plugin=" + normalize(pluginBootstrapVersion)
                    + "#menu=" + normalize(platformMenuTreeVersion);
        }

        private static String normalize(String value) {
            return StringUtils.hasText(value) ? value.trim() : "na";
        }

        private static String normalize(Long value) {
            return value == null ? "na" : Long.toString(value);
        }
    }

    private record ResolvedAuthBootstrapCacheKey(
            String sessionId,
            String cacheKey,
            AuthReadModelVersionProvider.AuthBootstrapReadModelVersions readModelVersions
    ) {
    }

    private static final class PermissionSnapshotCacheKey {
        private final Long userId;
        private final String userUuid;
        private final Long simulatedRoleId;

        private PermissionSnapshotCacheKey(Long userId, String userUuid, Long simulatedRoleId) {
            this.userId = userId;
            this.userUuid = StringUtils.hasText(userUuid) ? userUuid.trim() : "";
            this.simulatedRoleId = simulatedRoleId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PermissionSnapshotCacheKey otherKey)) {
                return false;
            }
            return Objects.equals(userId, otherKey.userId)
                    && Objects.equals(userUuid, otherKey.userUuid)
                    && Objects.equals(simulatedRoleId, otherKey.simulatedRoleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, userUuid, simulatedRoleId);
        }
    }

    private static final class PermissionSnapshotLoadInFlightKey {
        private final Long userId;
        private final String userUuid;
        private final Long simulatedRoleId;
        private final Long readModelVersion;

        private PermissionSnapshotLoadInFlightKey(Long userId, String userUuid, Long simulatedRoleId, Long readModelVersion) {
            this.userId = userId;
            this.userUuid = StringUtils.hasText(userUuid) ? userUuid.trim() : "";
            this.simulatedRoleId = simulatedRoleId;
            this.readModelVersion = readModelVersion;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PermissionSnapshotLoadInFlightKey otherKey)) {
                return false;
            }
            return Objects.equals(userId, otherKey.userId)
                    && Objects.equals(userUuid, otherKey.userUuid)
                    && Objects.equals(simulatedRoleId, otherKey.simulatedRoleId)
                    && Objects.equals(readModelVersion, otherKey.readModelVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, userUuid, simulatedRoleId, readModelVersion);
        }
    }

    private record CurrentUserSessionHydration(
            SystemUserSnapshotDTO user,
            PermissionSnapshotDTO snapshot
    ) {
    }

}
