package com.legendary.invention.auth.service;

import com.legendary.invention.api.auth.*;
import com.legendary.invention.api.client.SystemInternalApi;
import com.legendary.invention.api.system.LoginAuditRecordRequestDTO;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.VerificationChallengeDTO;
import com.legendary.invention.api.system.VerificationProviderDTO;
import com.legendary.invention.api.system.WechatLoginUserRequestDTO;
import com.legendary.invention.auth.config.AuthSecurityProperties;
import com.legendary.invention.common.constant.PlatformConstants;
import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.common.web.repeatsubmit.ClientIpResolver;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.security.JwtTokenClaims;
import com.legendary.invention.common.security.JwtTokenType;
import com.legendary.invention.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthAppService {

    private static final Logger log = LoggerFactory.getLogger(AuthAppService.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String INITIAL_ADMIN_PASSWORD = "123456";
    private static final Set<String> UNSAFE_DEFAULT_ADMIN_PASSWORDS = Set.of("123456", "admin", "password");

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
    }

    public LoginEncryptionKeyDTO loginEncryptionKey() {
        return loginEncryptionService.getPublicKeyInfo();
    }
    public LoginResponseDTO login(LoginRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String userAgent = httpServletRequest.getHeader("User-Agent");
        String account = request.account();
        loginProtectionService.ensureCanAttempt(account, loginIp);
        loginProtectionService.recordAttempt(account, loginIp);

        if (request.captchaId() != null || request.captchaCode() != null || request.captchaProof() != null) {
            boolean captchaValid = Boolean.TRUE.equals(systemInternalApi.validateCaptcha(new com.legendary.invention.api.system.CaptchaValidationRequestDTO(request.captchaId(), request.captchaCode(), request.captchaProof())));
            if (!captchaValid) {
                loginProtectionService.recordFailure(account, loginIp);
                recordLoginAudit(null, PlatformConstants.PLATFORM_TENANT_ID, account, "CAPTCHA", "FAIL", "验证码错误", loginIp, userAgent);
                throw new BizException(ErrorCode.CAPTCHA_INVALID, "验证码错误，请重新输入");
            }
        }

        SystemUserSnapshotDTO user = systemInternalApi.findLoginUser(account);
        LoginCapabilitiesDTO loginCapabilities = systemInternalApi.loginCapabilities(PlatformConstants.PLATFORM_TENANT_ID);
        if (loginCapabilities != null && !loginCapabilities.passwordLoginAvailable()) {
            loginProtectionService.recordFailure(account, loginIp);
            recordLoginAudit(null, PlatformConstants.PLATFORM_TENANT_ID, account, "PASSWORD", "FAIL", "账号密码登录未启用", loginIp, userAgent);
            throw new BizException(ErrorCode.FORBIDDEN, "账号密码登录未启用");
        }
        if (user == null) {
            loginProtectionService.recordFailure(account, loginIp);
            recordLoginAudit(null, null, account, "PASSWORD", "FAIL", "用户不存在", loginIp, userAgent);
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "登录失败，账号不存在: " + account, ErrorCode.LOGIN_FAILED.getDefaultUserMessage());
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            loginProtectionService.recordFailure(account, loginIp);
            recordLoginAudit(user.userId(), null, user.username(), "PASSWORD", "FAIL", "账号已禁用", loginIp, userAgent);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "登录失败，账号已禁用: " + user.username(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }

        String loginPassword = loginEncryptionService.decryptPassword(request.password());
        if (!passwordEncoder.matches(loginPassword, user.passwordHash())) {
            loginProtectionService.recordFailure(account, loginIp);
            recordLoginAudit(user.userId(), null, user.username(), "PASSWORD", "FAIL", "密码错误", loginIp, userAgent);
            throw new BizException(ErrorCode.PASSWORD_ERROR, "登录失败，密码错误: " + user.username(), ErrorCode.LOGIN_FAILED.getDefaultUserMessage());
        }
        boolean requiresPasswordChange = requiresInitialAdminPasswordChange(account, user, loginPassword);
        rejectUnsafeDefaultAdminLogin(account, user, loginPassword, loginIp, userAgent);

        Long currentTenantId = PlatformConstants.PLATFORM_TENANT_ID;
        List<LoginResponseDTO.SecondFactorOptionDTO> secondFactorOptions = systemInternalApi.listLoginSecondFactorOptions(currentTenantId, user.userId());
        if (secondFactorOptions != null && !secondFactorOptions.isEmpty()) {
            LoginResponseDTO response = new LoginResponseDTO();
            response.setRequiresSecondFactor(Boolean.TRUE);
            response.setSecondFactorOptions(secondFactorOptions);
            recordLoginAudit(user.userId(), currentTenantId, user.username(), "PASSWORD", "PENDING", "SECOND_FACTOR_REQUIRED", loginIp, userAgent);
            return response;
        }

        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(currentTenantId, user.userId());

        AuthSession session = buildSession(user, currentTenantId, loginIp, userAgent, snapshot);
        saveSessionWithMultiDevicePolicy(session);
        loginProtectionService.clearFailureState(account, loginIp);
        recordLoginAudit(user.userId(), currentTenantId, user.username(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
        return toLoginResponse(session, user, snapshot, requiresPasswordChange);
    }

    private boolean requiresInitialAdminPasswordChange(String account, SystemUserSnapshotDTO user, String loginPassword) {
        boolean adminAccount = DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(account) || DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(user.username());
        return adminAccount && INITIAL_ADMIN_PASSWORD.equals(loginPassword);
    }

    private void rejectUnsafeDefaultAdminLogin(
            String account,
            SystemUserSnapshotDTO user,
            String loginPassword,
            String loginIp,
            String userAgent
    ) {
        if (securityProperties.isAllowUnsafeDefaultAdminLogin()) {
            return;
        }
        boolean adminAccount = DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(account) || DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(user.username());
        if (!adminAccount || !UNSAFE_DEFAULT_ADMIN_PASSWORDS.contains(loginPassword)) {
            return;
        }
        if (INITIAL_ADMIN_PASSWORD.equals(loginPassword)) {
            return;
        }
        loginProtectionService.recordFailure(account, loginIp);
        recordLoginAudit(user.userId(), PlatformConstants.PLATFORM_TENANT_ID, user.username(), "PASSWORD", "FAIL", "默认管理员弱密码已禁用", loginIp, userAgent);
        throw new BizException(
                ErrorCode.UNAUTHORIZED,
                "默认管理员弱密码登录已禁用，请通过部署初始化流程重置管理员密码",
                ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
        );
    }
    public LoginCodeChallengeDTO loginCodeChallenge(LoginCodeChallengeRequest request, HttpServletRequest httpServletRequest) {
        Long tenantId = PlatformConstants.PLATFORM_TENANT_ID;
        return systemInternalApi.loginCodeChallenge(tenantId, request.account(), request.loginType());
    }
    public LoginResponseDTO completeLoginCodeLogin(LoginCodeCompleteRequest request, HttpServletRequest httpServletRequest) {
        var verification = systemInternalApi.completeLoginCodeLogin(request);
        if (verification == null || !Boolean.TRUE.equals(verification.verified())) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification == null ? "验证码校验失败" : verification.message());
        }
        return loginVerifiedUser(verification.userId(), verification.tenantId(), httpServletRequest, "LOGIN_CODE");
    }

    public WechatAuthorizeUrlDTO wechatAuthorizeUrl() {
        return wechatLoginService.createAuthorizeUrl();
    }

    public LoginResponseDTO wechatLogin(WechatLoginRequest request, HttpServletRequest httpServletRequest) {
        WechatLoginService.WechatOAuthUser wechatUser = wechatLoginService.exchangeCode(request.code(), request.state());
        SystemUserSnapshotDTO user = systemInternalApi.resolveWechatLoginUser(
                new WechatLoginUserRequestDTO(wechatUser.openid(), wechatUser.unionid(), wechatUser.scope())
        );
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "微信登录用户创建失败");
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "登录失败，账号已禁用: " + user.username(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }
        Long currentTenantId = PlatformConstants.PLATFORM_TENANT_ID;
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(currentTenantId, user.userId());
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String userAgent = httpServletRequest.getHeader("User-Agent");
        List<LoginResponseDTO.SecondFactorOptionDTO> secondFactorOptions = collectSecondFactorOptions(currentTenantId, user.userId());
        if (!secondFactorOptions.isEmpty()) {
            recordLoginAudit(user.userId(), currentTenantId, user.username(), "WECHAT", "PENDING", "SECOND_FACTOR_REQUIRED", loginIp, userAgent);
            return toPendingSecondFactorResponse(user, snapshot, secondFactorOptions);
        }

        AuthSession session = buildSession(user, currentTenantId, loginIp, userAgent, snapshot);
        saveSessionWithMultiDevicePolicy(session);
        recordLoginAudit(user.userId(), currentTenantId, user.username(), "WECHAT", "SUCCESS", null, loginIp, userAgent);
        return toLoginResponse(session, user, snapshot);
    }
    public LoginResponseDTO completeSecondFactorLogin(SecondFactorCompleteRequest request, HttpServletRequest httpServletRequest) {
        var verification = systemInternalApi.completeSecondFactorLogin(request);
        if (verification == null || !Boolean.TRUE.equals(verification.verified())) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification == null ? "二次验证失败" : verification.message());
        }
        return loginVerifiedUser(verification.userId(), verification.tenantId(), httpServletRequest, "SECOND_FACTOR");
    }

    public void logout(HttpServletRequest httpServletRequest) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        authSessionStore.findBySessionId(currentUser.getSessionId()).ifPresent(session -> authSessionStore.remove(session, true));
    }
    public RefreshTokenResponseDTO refreshToken(RefreshTokenRequest request) {
        JwtTokenClaims claims = jwtTokenService.parseToken(request.refreshToken());
        if (claims.getTokenType() != JwtTokenType.REFRESH) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refresh token无效");
        }
        AuthSession session = authSessionStore.findBySessionId(claims.getSessionId())
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "会话已失效"));
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);
        authSessionStore.save(session, true);
        return new RefreshTokenResponseDTO(
                jwtTokenService.generateAccessToken(session),
                jwtTokenService.generateRefreshToken(session, refreshTokenId),
                "Bearer",
                jwtTokenService.getAccessTokenExpireSeconds()
        );
    }
    public CurrentUserDTO currentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "会话已失效"));
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(session.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "会话用户已不存在");
        }
        return currentUserBySession(session, user);
    }

    public CurrentUserDTO currentUserBySessionId(String sessionId) {
        AuthSession session = authSessionStore.findBySessionId(sessionId)
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "会话已失效"));
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(session.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "会话用户已不存在");
        }
        return currentUserBySession(session, user);
    }

    public List<LoginResponseDTO.SecondFactorOptionDTO> verificationProviders() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long currentTenantId = platformTenantId();
        List<com.legendary.invention.api.system.VerificationProviderDTO> providers = systemInternalApi.listVerificationProviders(currentTenantId, currentUser.getUserId());
        if (providers == null) {
            return List.of();
        }
        return providers.stream().map(this::toOption).toList();
    }

    public com.legendary.invention.api.system.VerificationProviderDTO verificationProvider(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.verificationProvider(platformTenantId(), currentUser.getUserId(), factorCode);
    }

    public com.legendary.invention.api.system.VerificationChallengeDTO verificationBind(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.bindVerificationProvider(platformTenantId(), currentUser.getUserId(), factorCode);
    }

    public Boolean verificationUnbind(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.unbindVerificationProvider(platformTenantId(), currentUser.getUserId(), factorCode);
    }

    public com.legendary.invention.api.system.VerificationChallengeDTO verificationChallenge(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.verificationChallenge(platformTenantId(), currentUser.getUserId(), factorCode);
    }

    public com.legendary.invention.api.system.VerificationVerificationDTO verificationVerify(String factorCode, SecondFactorCompleteRequest request) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!factorCode.equalsIgnoreCase(request.factorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不匹配");
        }
        return systemInternalApi.verificationVerify(platformTenantId(), currentUser.getUserId(), factorCode, request.challengeId(), request.verificationCode());
    }

    public LoginResponseDTO loginVerifiedUser(Long userId, Long tenantId, HttpServletRequest request) {
        return loginVerifiedUser(userId, tenantId, request, "PASSKEY");
    }

    private LoginResponseDTO loginVerifiedUser(Long userId, Long tenantId, HttpServletRequest request, String loginType) {
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "登录失败，账号不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "登录失败，账号已禁用: " + user.username(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }
        Long platformTenantId = platformTenantId();
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(platformTenantId, userId);
        String loginIp = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");
        AuthSession session = buildSession(user, platformTenantId, loginIp, userAgent, snapshot);
        saveSessionWithMultiDevicePolicy(session);
        recordLoginAudit(user.userId(), platformTenantId, user.username(), loginType, "SUCCESS", null, loginIp, userAgent);
        return toLoginResponse(session, user, snapshot);
    }

    private void recordLoginAudit(
            Long userId,
            Long tenantId,
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
                    tenantId,
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

    private List<LoginResponseDTO.SecondFactorOptionDTO> collectSecondFactorOptions(Long tenantId, Long userId) {
        List<VerificationProviderDTO> providers = systemInternalApi.listVerificationProviders(tenantId, userId);
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        List<LoginResponseDTO.SecondFactorOptionDTO> options = new ArrayList<>();
        for (VerificationProviderDTO provider : providers) {
            if (provider == null || !provider.isEnabled() || !provider.isBound()) {
                continue;
            }
            VerificationChallengeDTO challenge = systemInternalApi.verificationChallenge(tenantId, userId, provider.getFactorCode());
            options.add(toSecondFactorOption(provider, challenge));
        }
        return options;
    }

    private LoginResponseDTO.SecondFactorOptionDTO toSecondFactorOption(VerificationProviderDTO provider, VerificationChallengeDTO challenge) {
        LoginResponseDTO.SecondFactorOptionDTO option = new LoginResponseDTO.SecondFactorOptionDTO();
        option.setFactorCode(challenge == null ? provider.getFactorCode() : challenge.getFactorCode());
        option.setFactorName(challenge == null ? provider.getFactorName() : challenge.getFactorName());
        option.setChallengeId(challenge == null ? provider.getChallengeId() : challenge.getChallengeId());
        option.setMaskedContact(challenge == null ? provider.getMaskedContact() : challenge.getMaskedContact());
        option.setPromptMessage(challenge == null ? provider.getPromptMessage() : challenge.getPromptMessage());
        return option;
    }

    private AuthSession buildSession(SystemUserSnapshotDTO user, Long currentTenantId, String loginIp, String userAgent, PermissionSnapshotDTO snapshot) {
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.userId());
        session.setUsername(user.username());
        session.setCurrentTenantId(currentTenantId);
        session.setLoginTime(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setExpireTime(Instant.now().plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
        session.setSessionVersion(1);
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        session.setRefreshTokenId(UUID.randomUUID().toString());
        return session;
    }

    private void saveSessionWithMultiDevicePolicy(AuthSession session) {
        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(session.getUserId(), true);
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
                snapshot.permissions()
        );
    }

    private Long platformTenantId() {
        return PlatformConstants.PLATFORM_TENANT_ID;
    }

    private LoginResponseDTO.SecondFactorOptionDTO toOption(com.legendary.invention.api.system.VerificationProviderDTO provider) {
        LoginResponseDTO.SecondFactorOptionDTO option = new LoginResponseDTO.SecondFactorOptionDTO();
        option.setFactorCode(provider.getFactorCode());
        option.setFactorName(provider.getFactorName());
        option.setChallengeId(provider.getChallengeId());
        option.setMaskedContact(provider.getMaskedContact());
        option.setPromptMessage(provider.getPromptMessage());
        return option;
    }

    private CurrentUserDTO currentUserBySession(AuthSession session, SystemUserSnapshotDTO user) {
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(platformTenantId(), session.getUserId());
        return new CurrentUserDTO(
                user.userId(),
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
                session.getSessionId(),
                snapshot.version(),
                session.getSessionVersion(),
                snapshot.permissions(),
                snapshot.roleIds(),
                snapshot.primaryDeptId(),
                snapshot.deptIds(),
                snapshot.descendantDeptIds(),
                snapshot.dataScopes()
        );
    }

}
