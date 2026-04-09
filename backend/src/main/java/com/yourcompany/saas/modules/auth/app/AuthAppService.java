package com.yourcompany.saas.modules.auth.app;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
import com.yourcompany.saas.infrastructure.security.model.TokenType;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.CaptchaService;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
import com.yourcompany.saas.infrastructure.security.service.LoginProtectionService;
import com.yourcompany.saas.infrastructure.security.service.SecuritySettingsService;
import com.yourcompany.saas.modules.audit.app.LoginAuditService;
import com.yourcompany.saas.modules.auth.dto.LoginRequest;
import com.yourcompany.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.yourcompany.saas.modules.auth.dto.RefreshTokenRequest;
import com.yourcompany.saas.modules.auth.vo.AuthUserVO;
import com.yourcompany.saas.modules.auth.vo.CurrentUserVO;
import com.yourcompany.saas.modules.auth.vo.LoginResponseVO;
import com.yourcompany.saas.modules.auth.vo.RefreshTokenResponseVO;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import com.yourcompany.saas.modules.plugin.app.PluginManagementAppService;
import com.yourcompany.saas.modules.plugin.registry.PluginRuntimeDescriptor;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorChallenge;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorProfile;
import com.yourcompany.saas.modules.plugin.runtime.runtime.PluginRuntimeModels.PluginSecondFactorVerification;
import com.yourcompany.saas.modules.plugin.runtime.spi.PluginSecondFactorProvider;
import com.yourcompany.saas.modules.tenant.domain.TenantDomainService;
import com.yourcompany.saas.modules.tenant.domain.UserTenantAccess;
import com.yourcompany.saas.modules.tenant.entity.TenantInfoEntity;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;
import com.yourcompany.saas.modules.user.domain.UserDomainService;
import com.yourcompany.saas.modules.user.entity.SysUserEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthAppService {

    private final UserDomainService userDomainService;
    private final TenantDomainService tenantDomainService;
    private final LoginAuditService loginAuditService;
    private final AuthSessionStore authSessionStore;
    private final CaptchaService captchaService;
    private final LoginProtectionService loginProtectionService;
    private final JwtTokenService jwtTokenService;
    private final SecuritySettingsService securitySettingsService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionSnapshotService permissionSnapshotService;
    private final PluginManagementAppService pluginManagementAppService;

    public AuthAppService(
            UserDomainService userDomainService,
            TenantDomainService tenantDomainService,
            LoginAuditService loginAuditService,
            AuthSessionStore authSessionStore,
            CaptchaService captchaService,
            LoginProtectionService loginProtectionService,
            JwtTokenService jwtTokenService,
            SecuritySettingsService securitySettingsService,
            PasswordEncoder passwordEncoder,
            PermissionSnapshotService permissionSnapshotService,
            PluginManagementAppService pluginManagementAppService
    ) {
        this.userDomainService = userDomainService;
        this.tenantDomainService = tenantDomainService;
        this.loginAuditService = loginAuditService;
        this.authSessionStore = authSessionStore;
        this.captchaService = captchaService;
        this.loginProtectionService = loginProtectionService;
        this.jwtTokenService = jwtTokenService;
        this.securitySettingsService = securitySettingsService;
        this.passwordEncoder = passwordEncoder;
        this.permissionSnapshotService = permissionSnapshotService;
        this.pluginManagementAppService = pluginManagementAppService;
    }

    public LoginResponseVO login(LoginRequest request, String loginIp, String userAgent) {
        String account = request.account();
        loginProtectionService.ensureCanAttempt(account, loginIp);
        loginProtectionService.recordAttempt(account, loginIp);
        validateCaptchaIfRequired(request, account, loginIp, userAgent);

        SysUserEntity user = userDomainService.findLoginUser(account).orElse(null);
        if (user == null) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(null, null, account, "PASSWORD", "FAIL", "用户不存在", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "登录失败，账号不存在: " + account,
                    ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
            );
        }

        if (isUserDisabled(user)) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "账号已禁用", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "登录失败，账号已禁用: " + user.getUsername(),
                    ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage()
            );
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "密码错误", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.PASSWORD_ERROR,
                    "登录失败，密码错误: " + user.getUsername(),
                    ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
            );
        }

        List<UserTenantAccess> enabledTenantAccessList = tenantDomainService.listUserTenantAccess(user.getId()).stream()
                .filter(access -> access.getTenant() != null && "ENABLED".equalsIgnoreCase(access.getTenant().getStatus()))
                .toList();

        if (enabledTenantAccessList.isEmpty()) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "用户未绑定可用租户", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.TENANT_NOT_BOUND,
                    "登录失败，用户未绑定可用租户: " + user.getUsername(),
                    ErrorCode.TENANT_NOT_BOUND.getDefaultUserMessage()
            );
        }

        TenantInfoEntity currentTenant = pickCurrentTenant(enabledTenantAccessList);
        Long tenantId = currentTenant == null ? null : currentTenant.getId();
        LoginResponseVO response = new LoginResponseVO();
        response.setUser(toAuthUser(user));
        response.setTenants(tenantDomainService.toMyTenantVO(enabledTenantAccessList));
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));
        List<LoginResponseVO.SecondFactorOptionVO> secondFactorOptions = collectSecondFactorOptions(user, tenantId);
        if (!secondFactorOptions.isEmpty()) {
            response.setRequiresSecondFactor(Boolean.TRUE);
            response.setSecondFactorOptions(secondFactorOptions);
            response.setSecondFactorPluginCode(secondFactorOptions.get(0).getPluginCode());
            response.setSecondFactorPluginName(secondFactorOptions.get(0).getPluginName());
            response.setSecondFactorChallengeId(secondFactorOptions.get(0).getChallengeId());
            loginAuditService.log(user.getId(), tenantId, user.getUsername(), "PASSWORD", "PENDING", "SECOND_FACTOR_REQUIRED", loginIp, userAgent);
            return response;
        }

        AuthSession session = buildNewSession(user, tenantId, loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(user.getId(), true);
        }
        authSessionStore.save(session, true);
        loginProtectionService.clearFailureState(account, loginIp);

        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setRequiresCaptcha(Boolean.FALSE);

        loginAuditService.log(user.getId(), tenantId, user.getUsername(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    private void validateCaptchaIfRequired(LoginRequest request, String account, String loginIp, String userAgent) {
        if (!securitySettingsService.isCaptchaEnabled()) {
            return;
        }

        try {
            captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode(), request.getCaptchaProof());
        } catch (BizException ex) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(null, null, account, "CAPTCHA", "FAIL", ex.getErrorMessage(), loginIp, userAgent);
            throw ex;
        }
    }

    public LoginResponseVO completeSecondFactorLogin(SecondFactorCompleteRequest request, String loginIp, String userAgent) {
        PluginRuntimeDescriptor runtimeDescriptor = pluginManagementAppService.findActiveRuntimeDescriptor(request.getPluginCode())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "插件运行时不存在"));
        PluginSecondFactorProvider provider = runtimeDescriptor.getSecondFactorProvider();
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "插件未提供二次验证能力");
        }
        PluginSecondFactorVerification verification = provider.verify(
                runtimeDescriptor.getRuntimeContext(),
                request.getChallengeId(),
                request.getVerificationCode()
        );
        if (!verification.verified()) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification.message() == null ? "二次验证失败" : verification.message());
        }
        SysUserEntity user = userDomainService.findById(verification.userId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        TenantInfoEntity currentTenant = tenantDomainService.findTenantById(verification.tenantId()).orElse(null);
        List<UserTenantAccess> enabledTenantAccessList = tenantDomainService.listUserTenantAccess(user.getId()).stream()
                .filter(access -> access.getTenant() != null && "ENABLED".equalsIgnoreCase(access.getTenant().getStatus()))
                .toList();
        AuthSession session = buildNewSession(user, currentTenant == null ? null : currentTenant.getId(), loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(user.getId(), true);
        }
        authSessionStore.save(session, true);

        LoginResponseVO response = new LoginResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user));
        response.setTenants(tenantDomainService.toMyTenantVO(enabledTenantAccessList));
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));
        response.setRequiresCaptcha(Boolean.FALSE);
        loginAuditService.log(user.getId(), currentTenant == null ? null : currentTenant.getId(), user.getUsername(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    public void logout(CurrentUser currentUser, String loginIp, String userAgent) {
        if (currentUser.getSessionId() == null) {
            return;
        }
        authSessionStore.findBySessionId(currentUser.getSessionId()).ifPresent(session -> authSessionStore.remove(session, true));
        loginAuditService.log(
                currentUser.getUserId(),
                currentUser.getCurrentTenantId(),
                currentUser.getUsername(),
                "LOGOUT",
                "SUCCESS",
                null,
                loginIp,
                userAgent
        );
    }

    public RefreshTokenResponseVO refreshToken(RefreshTokenRequest request) {
        TokenClaims tokenClaims = jwtTokenService.parseToken(request.getRefreshToken());
        if (tokenClaims.getTokenType() != TokenType.REFRESH) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "refreshToken非法",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }

        AuthSession session = authSessionStore.findBySessionId(tokenClaims.getSessionId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "刷新失败，会话已失效",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        validateSessionForRefresh(session, tokenClaims);

        String newRefreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(newRefreshTokenId);
        session.setExpireTime(jwtTokenService.createRefreshTokenExpireAt());
        session.setLastActivityAt(Instant.now());
        authSessionStore.save(session, false);

        RefreshTokenResponseVO response = new RefreshTokenResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, newRefreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setSessionVersion(session.getSessionVersion());
        response.setPermissionsVersion(permissionSnapshotService.loadSnapshot(session.getCurrentTenantId(), session.getUserId()).getVersion());
        return response;
    }

    public CurrentUserVO currentUser(CurrentUser currentUser) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话关联用户不存在: " + currentUser.getUserId(),
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        TenantSummaryVO currentTenant = tenantDomainService.findTenantById(currentUser.getCurrentTenantId())
                .map(tenantDomainService::toTenantSummary)
                .orElse(null);
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId()
        );

        CurrentUserVO response = new CurrentUserVO();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRealName(user.getRealName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setMobile(user.getMobile());
        response.setEmail(user.getEmail());
        response.setCurrentTenant(currentTenant);
        response.setSessionId(currentUser.getSessionId());
        response.setPermissionsVersion(snapshot.getVersion());
        response.setSessionVersion(currentUser.getSessionVersion());
        response.setPermissions(snapshot.getPermissionList());
        return response;
    }

    private void validateSessionForRefresh(AuthSession session, TokenClaims tokenClaims) {
        if (!session.getUserId().equals(tokenClaims.getUserId())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "refreshToken与会话不匹配",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(tokenClaims.getSessionVersion())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "会话版本已变更，请重新登录",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            String latestSessionId = authSessionStore.findLatestActiveUserSessionId(session.getUserId()).orElse(null);
            if (latestSessionId == null || !session.getSessionId().equals(latestSessionId)) {
                throw new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "当前账号已在其他设备登录，请重新登录",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                );
            }
        }
        if (session.getRefreshTokenId() == null || !session.getRefreshTokenId().equals(tokenClaims.getTokenId())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "refreshToken已失效",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        if (jwtTokenService.isExpired(session.getExpireTime())) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "会话已过期，请重新登录",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }
        Instant lastActivityAt = session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getLoginTime();
        long idleTimeoutSeconds = jwtTokenService.getIdleTimeoutSeconds();
        if (lastActivityAt != null && idleTimeoutSeconds > 0) {
            Duration idleDuration = Duration.between(lastActivityAt, Instant.now());
            if (idleDuration.compareTo(Duration.ofSeconds(idleTimeoutSeconds)) >= 0) {
                throw new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话空闲超时，请重新登录",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                );
            }
        }
    }

    private boolean isUserDisabled(SysUserEntity user) {
        return user.getStatus() != null && !"ENABLED".equalsIgnoreCase(user.getStatus());
    }

    private void validateCaptchaIfRequired(LoginRequest request) {
        SecuritySettingsService.SecuritySettingsSnapshot settings = securitySettingsService.loadSettings();
        if (!settings.isCaptchaEnabled()) {
            return;
        }
        captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode(), request.getCaptchaProof());
    }

    private TenantInfoEntity pickCurrentTenant(List<UserTenantAccess> accessList) {
        return accessList.stream()
                .filter(UserTenantAccess::isDefault)
                .findFirst()
                .map(UserTenantAccess::getTenant)
                .orElseGet(() -> accessList.size() == 1 ? accessList.get(0).getTenant() : null);
    }

    private AuthSession buildNewSession(SysUserEntity user, Long currentTenantId, String loginIp, String userAgent) {
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setCurrentTenantId(currentTenantId);
        session.setLoginTime(now);
        session.setLastActivityAt(now);
        session.setExpireTime(now.plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
        session.setSessionVersion(1);
        session.setClientType("WEB");
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        return session;
    }

    private AuthUserVO toAuthUser(SysUserEntity user) {
        AuthUserVO authUserVO = new AuthUserVO();
        authUserVO.setUserId(user.getId());
        authUserVO.setUsername(user.getUsername());
        authUserVO.setNickname(user.getNickname());
        authUserVO.setRealName(user.getRealName());
        authUserVO.setAvatarUrl(user.getAvatarUrl());
        authUserVO.setMobile(user.getMobile());
        authUserVO.setEmail(user.getEmail());
        return authUserVO;
    }

    private List<LoginResponseVO.SecondFactorOptionVO> collectSecondFactorOptions(SysUserEntity user, Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<LoginResponseVO.SecondFactorOptionVO> result = new ArrayList<>();
        pluginManagementAppService.availablePlugins(tenantId).forEach(plugin -> {
            Optional<PluginRuntimeDescriptor> descriptor = pluginManagementAppService.findTenantRuntimeDescriptor(tenantId, plugin.getPluginCode());
            if (descriptor.isEmpty()) {
                return;
            }
            PluginSecondFactorProvider provider = descriptor.get().getSecondFactorProvider();
            if (provider == null) {
                return;
            }
            PluginSecondFactorProfile profile = provider.profile(descriptor.get().getRuntimeContext(), tenantId, user.getId());
            if (!profile.enabled() || !profile.bound()) {
                return;
            }
            PluginSecondFactorChallenge challenge = provider.prepareChallenge(descriptor.get().getRuntimeContext(), tenantId, user.getId());
            LoginResponseVO.SecondFactorOptionVO option = new LoginResponseVO.SecondFactorOptionVO();
            option.setPluginCode(plugin.getPluginCode());
            option.setPluginName(plugin.getPluginName());
            option.setFactorCode(provider.factorCode());
            option.setFactorName(provider.factorName());
            option.setChallengeId(challenge.challengeId());
            option.setMaskedContact(profile.maskedContact());
            option.setPromptMessage(profile.statusMessage());
            result.add(option);
        });
        return result;
    }
}
