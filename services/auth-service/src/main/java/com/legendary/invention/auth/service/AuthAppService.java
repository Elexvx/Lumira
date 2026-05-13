package com.legendary.invention.auth.service;

import com.legendary.invention.api.auth.*;
import com.legendary.invention.api.client.SystemInternalApi;
import com.legendary.invention.api.client.TenantInternalApi;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.WechatLoginUserRequestDTO;
import com.legendary.invention.api.tenant.MyTenantDTO;
import com.legendary.invention.api.tenant.TenantSummaryDTO;
import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.auth.support.ClientIpResolver;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.security.JwtTokenClaims;
import com.legendary.invention.common.security.JwtTokenType;
import com.legendary.invention.common.security.SecurityContextFacade;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuthAppService {

    private final SystemInternalApi systemInternalApi;
    private final TenantInternalApi tenantInternalApi;
    private final LoginEncryptionService loginEncryptionService;
    private final LoginProtectionService loginProtectionService;
    private final AuthSessionStore authSessionStore;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextFacade securityContextFacade;
    private final ClientIpResolver clientIpResolver;
    private final WechatLoginService wechatLoginService;

    public AuthAppService(
            SystemInternalApi systemInternalApi,
            TenantInternalApi tenantInternalApi,
            LoginEncryptionService loginEncryptionService,
            LoginProtectionService loginProtectionService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            SecurityContextFacade securityContextFacade,
            ClientIpResolver clientIpResolver,
            WechatLoginService wechatLoginService
    ) {
        this.systemInternalApi = systemInternalApi;
        this.tenantInternalApi = tenantInternalApi;
        this.loginEncryptionService = loginEncryptionService;
        this.loginProtectionService = loginProtectionService;
        this.authSessionStore = authSessionStore;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.securityContextFacade = securityContextFacade;
        this.clientIpResolver = clientIpResolver;
        this.wechatLoginService = wechatLoginService;
    }

    public LoginEncryptionKeyDTO loginEncryptionKey() {
        return loginEncryptionService.getPublicKeyInfo();
    }

    @SentinelResource(value = "auth-login", blockHandler = "loginBlocked", blockHandlerClass = AuthSentinelBlockHandler.class)
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
                throw new BizException(ErrorCode.CAPTCHA_INVALID, "验证码错误，请重新输入");
            }
        }

        SystemUserSnapshotDTO user = systemInternalApi.findLoginUser(account);
        if (user == null) {
            loginProtectionService.recordFailure(account, loginIp);
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "登录失败，账号不存在: " + account, ErrorCode.LOGIN_FAILED.getDefaultUserMessage());
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            loginProtectionService.recordFailure(account, loginIp);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "登录失败，账号已禁用: " + user.username(), ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage());
        }

        String loginPassword = loginEncryptionService.decryptPassword(request.password());
        if (!passwordEncoder.matches(loginPassword, user.passwordHash())) {
            loginProtectionService.recordFailure(account, loginIp);
            throw new BizException(ErrorCode.PASSWORD_ERROR, "登录失败，密码错误: " + user.username(), ErrorCode.LOGIN_FAILED.getDefaultUserMessage());
        }

        List<MyTenantDTO> visibleTenants = tenantInternalApi.listVisibleTenants(user.userId());
        TenantSummaryDTO currentTenant = resolveCurrentTenant(visibleTenants);
        if (currentTenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_BOUND, "当前账号未绑定可用租户");
        }

        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(currentTenant.tenantId(), user.userId());
        LoginCapabilitiesDTO loginCapabilities = systemInternalApi.loginCapabilities(currentTenant.tenantId());
        List<LoginResponseDTO.SecondFactorOptionDTO> secondFactorOptions = new ArrayList<>();
        if (loginCapabilities != null && (loginCapabilities.smsLoginAvailable() || loginCapabilities.emailLoginAvailable())) {
            List<com.legendary.invention.api.system.VerificationProviderDTO> providers = systemInternalApi.listVerificationProviders(currentTenant.tenantId(), user.userId());
            if (providers == null) {
                providers = List.of();
            }
            for (var provider : providers) {
                if (provider != null && provider.isEnabled() && provider.isBound()) {
                    LoginResponseDTO.SecondFactorOptionDTO option = new LoginResponseDTO.SecondFactorOptionDTO();
                    option.setFactorCode(provider.getFactorCode());
                    option.setFactorName(provider.getFactorName());
                    option.setChallengeId(provider.getChallengeId());
                    option.setMaskedContact(provider.getMaskedContact());
                    option.setPromptMessage(provider.getPromptMessage());
                    secondFactorOptions.add(option);
                }
            }
        }

        if (!secondFactorOptions.isEmpty()) {
            LoginResponseDTO pending = new LoginResponseDTO();
            pending.setUser(toAuthUser(user, currentTenant, snapshot, null));
            pending.setTenants(visibleTenants == null ? List.of() : visibleTenants);
            pending.setCurrentTenant(currentTenant);
            pending.setRequiresSecondFactor(Boolean.TRUE);
            pending.setSecondFactorOptions(secondFactorOptions);
            pending.setRequiresCaptcha(Boolean.FALSE);
            return pending;
        }

        AuthSession session = buildSession(user, currentTenant, loginIp, userAgent, snapshot);
        authSessionStore.save(session, true);
        loginProtectionService.clearFailureState(account, loginIp);
        return toLoginResponse(session, user, currentTenant, snapshot);
    }

    @SentinelResource(value = "auth-login-code-challenge", blockHandler = "loginCodeChallengeBlocked", blockHandlerClass = AuthSentinelBlockHandler.class)
    public LoginCodeChallengeDTO loginCodeChallenge(LoginCodeChallengeRequest request, HttpServletRequest httpServletRequest) {
        String loginIp = clientIpResolver.resolve(httpServletRequest);
        String userAgent = httpServletRequest.getHeader("User-Agent");
        SystemUserSnapshotDTO user = systemInternalApi.findLoginUser(request.account());
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "登录失败，账号不存在: " + request.account(), ErrorCode.LOGIN_FAILED.getDefaultUserMessage());
        }
        TenantSummaryDTO tenant = resolveCurrentTenant(tenantInternalApi.listVisibleTenants(user.userId()));
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_BOUND, "当前账号未绑定可用租户");
        }
        LoginCodeChallengeDTO challenge = systemInternalApi.loginCodeChallenge(tenant.tenantId(), user.userId(), request.account(), request.loginType());
        return challenge;
    }

    @SentinelResource(value = "auth-login-code-complete", blockHandler = "completeLoginCodeLoginBlocked", blockHandlerClass = AuthSentinelBlockHandler.class)
    public LoginResponseDTO completeLoginCodeLogin(LoginCodeCompleteRequest request, HttpServletRequest httpServletRequest) {
        var verification = systemInternalApi.completeLoginCodeLogin(request);
        if (verification == null || !Boolean.TRUE.equals(verification.verified())) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification == null ? "验证码校验失败" : verification.message());
        }
        return completeVerifiedLogin(verification.userId(), verification.tenantId(), httpServletRequest);
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
        List<MyTenantDTO> visibleTenants = tenantInternalApi.listVisibleTenants(user.userId());
        TenantSummaryDTO currentTenant = resolveCurrentTenant(visibleTenants);
        if (currentTenant == null) {
            throw new BizException(ErrorCode.TENANT_NOT_BOUND, "当前账号未绑定可用租户");
        }
        PermissionSnapshotDTO snapshot = systemInternalApi.permissionSnapshot(currentTenant.tenantId(), user.userId());
        AuthSession session = buildSession(user, currentTenant, clientIpResolver.resolve(httpServletRequest), httpServletRequest.getHeader("User-Agent"), snapshot);
        authSessionStore.save(session, true);
        return toLoginResponse(session, user, currentTenant, snapshot);
    }

    @SentinelResource(value = "auth-second-factor-complete", blockHandler = "completeSecondFactorLoginBlocked", blockHandlerClass = AuthSentinelBlockHandler.class)
    public LoginResponseDTO completeSecondFactorLogin(SecondFactorCompleteRequest request, HttpServletRequest httpServletRequest) {
        var verification = systemInternalApi.completeSecondFactorLogin(request);
        if (verification == null || !Boolean.TRUE.equals(verification.verified())) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification == null ? "二次验证失败" : verification.message());
        }
        return completeVerifiedLogin(verification.userId(), verification.tenantId(), httpServletRequest);
    }

    public void logout(HttpServletRequest httpServletRequest) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        authSessionStore.findBySessionId(currentUser.getSessionId()).ifPresent(session -> authSessionStore.remove(session, true));
    }

    @SentinelResource(value = "auth-refresh-token", blockHandler = "refreshTokenBlocked", blockHandlerClass = AuthSentinelBlockHandler.class)
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

    @SentinelResource(value = "auth-current-user", blockHandler = "currentUserBlocked", blockHandlerClass = AuthSentinelBlockHandler.class)
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
        TenantSummaryDTO currentTenant = currentTenantOrThrow(currentUser);
        List<com.legendary.invention.api.system.VerificationProviderDTO> providers = systemInternalApi.listVerificationProviders(currentTenant.tenantId(), currentUser.getUserId());
        if (providers == null) {
            return List.of();
        }
        return providers.stream().map(this::toOption).toList();
    }

    public com.legendary.invention.api.system.VerificationProviderDTO verificationProvider(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        TenantSummaryDTO currentTenant = currentTenantOrThrow(currentUser);
        return systemInternalApi.verificationProvider(currentTenant.tenantId(), currentUser.getUserId(), factorCode);
    }

    public com.legendary.invention.api.system.VerificationChallengeDTO verificationBind(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        TenantSummaryDTO currentTenant = currentTenantOrThrow(currentUser);
        return systemInternalApi.bindVerificationProvider(currentTenant.tenantId(), currentUser.getUserId(), factorCode);
    }

    public Boolean verificationUnbind(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        TenantSummaryDTO currentTenant = currentTenantOrThrow(currentUser);
        return systemInternalApi.unbindVerificationProvider(currentTenant.tenantId(), currentUser.getUserId(), factorCode);
    }

    public com.legendary.invention.api.system.VerificationChallengeDTO verificationChallenge(String factorCode) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        TenantSummaryDTO currentTenant = currentTenantOrThrow(currentUser);
        return systemInternalApi.verificationChallenge(currentTenant.tenantId(), currentUser.getUserId(), factorCode);
    }

    public com.legendary.invention.api.system.VerificationVerificationDTO verificationVerify(String factorCode, SecondFactorCompleteRequest request) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        TenantSummaryDTO currentTenant = currentTenantOrThrow(currentUser);
        if (!factorCode.equalsIgnoreCase(request.factorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不匹配");
        }
        return systemInternalApi.verificationVerify(currentTenant.tenantId(), currentUser.getUserId(), factorCode, request.challengeId(), request.verificationCode());
    }

    private LoginResponseDTO completeVerifiedLogin(Long userId, Long tenantId, HttpServletRequest request) {
        SystemUserSnapshotDTO user = systemInternalApi.findUserById(userId);
        TenantSummaryDTO tenant = tenantId == null ? null : tenantInternalApi.findTenantSummary(tenantId);
        PermissionSnapshotDTO snapshot = tenant == null ? new PermissionSnapshotDTO("0", List.of()) : systemInternalApi.permissionSnapshot(tenant.tenantId(), userId);
        AuthSession session = buildSession(user, tenant, clientIpResolver.resolve(request), request.getHeader("User-Agent"), snapshot);
        authSessionStore.save(session, true);
        return toLoginResponse(session, user, tenant, snapshot);
    }

    private AuthSession buildSession(SystemUserSnapshotDTO user, TenantSummaryDTO currentTenant, String loginIp, String userAgent, PermissionSnapshotDTO snapshot) {
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.userId());
        session.setUsername(user.username());
        session.setCurrentTenantId(currentTenant == null ? null : currentTenant.tenantId());
        session.setLoginTime(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setExpireTime(Instant.now().plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
        session.setSessionVersion(1);
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        session.setRefreshTokenId(UUID.randomUUID().toString());
        return session;
    }

    private LoginResponseDTO toLoginResponse(AuthSession session, SystemUserSnapshotDTO user, TenantSummaryDTO tenant, PermissionSnapshotDTO snapshot) {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, session.getRefreshTokenId()));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user, tenant, snapshot, session.getSessionId()));
        response.setTenants(resolveTenants(user.userId()));
        response.setCurrentTenant(tenant);
        response.setRequiresSecondFactor(Boolean.FALSE);
        response.setRequiresCaptcha(Boolean.FALSE);
        return response;
    }

    private AuthUserDTO toAuthUser(SystemUserSnapshotDTO user, TenantSummaryDTO tenant, PermissionSnapshotDTO snapshot, String sessionId) {
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
                tenant,
                sessionId,
                snapshot.version(),
                1,
                snapshot.permissions()
        );
    }

    private List<MyTenantDTO> resolveTenants(Long userId) {
        List<MyTenantDTO> tenants = tenantInternalApi.listVisibleTenants(userId);
        return tenants == null ? List.of() : tenants;
    }

    private TenantSummaryDTO resolveCurrentTenant(List<MyTenantDTO> visibleTenants) {
        if (visibleTenants == null || visibleTenants.isEmpty()) {
            return null;
        }
        for (MyTenantDTO tenant : visibleTenants) {
            if (Boolean.TRUE.equals(tenant.getIsDefault())) {
                return toTenantSummaryDTO(tenant);
            }
        }
        return toTenantSummaryDTO(visibleTenants.get(0));
    }

    private TenantSummaryDTO currentTenantOrThrow(CurrentUser currentUser) {
        if (currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.TENANT_ERROR, "未找到当前租户");
        }
        TenantSummaryDTO tenant = tenantInternalApi.findTenantSummary(currentUser.getCurrentTenantId());
        if (tenant == null) {
            throw new BizException(ErrorCode.TENANT_ERROR, "租户不存在");
        }
        return tenant;
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
        TenantSummaryDTO currentTenant = session.getCurrentTenantId() == null ? null : tenantInternalApi.findTenantSummary(session.getCurrentTenantId());
        PermissionSnapshotDTO snapshot = currentTenant == null ? new PermissionSnapshotDTO("0", List.of()) : systemInternalApi.permissionSnapshot(currentTenant.tenantId(), session.getUserId());
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
                currentTenant,
                session.getSessionId(),
                snapshot.version(),
                session.getSessionVersion(),
                snapshot.permissions()
        );
    }

    private TenantSummaryDTO toTenantSummaryDTO(MyTenantDTO tenant) {
        if (tenant == null) {
            return null;
        }
        return new TenantSummaryDTO(
                tenant.getTenantId(),
                tenant.getTenantCode(),
                tenant.getTenantName(),
                tenant.getTenantShortName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
