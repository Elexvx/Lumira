package com.yourcompany.saas.modules.auth.app;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.model.TokenClaims;
import com.yourcompany.saas.infrastructure.security.model.TokenType;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
import com.yourcompany.saas.modules.audit.app.LoginAuditService;
import com.yourcompany.saas.modules.auth.dto.LoginRequest;
import com.yourcompany.saas.modules.auth.dto.RefreshTokenRequest;
import com.yourcompany.saas.modules.auth.vo.AuthUserVO;
import com.yourcompany.saas.modules.auth.vo.CurrentUserVO;
import com.yourcompany.saas.modules.auth.vo.LoginResponseVO;
import com.yourcompany.saas.modules.auth.vo.RefreshTokenResponseVO;
import com.yourcompany.saas.modules.tenant.domain.TenantDomainService;
import com.yourcompany.saas.modules.tenant.domain.UserTenantAccess;
import com.yourcompany.saas.modules.tenant.entity.TenantInfoEntity;
import com.yourcompany.saas.modules.tenant.vo.MyTenantVO;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;
import com.yourcompany.saas.modules.user.domain.UserDomainService;
import com.yourcompany.saas.modules.user.entity.SysUserEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthAppService {

    private final UserDomainService userDomainService;
    private final TenantDomainService tenantDomainService;
    private final LoginAuditService loginAuditService;
    private final AuthSessionStore authSessionStore;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthAppService(
            UserDomainService userDomainService,
            TenantDomainService tenantDomainService,
            LoginAuditService loginAuditService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder
    ) {
        this.userDomainService = userDomainService;
        this.tenantDomainService = tenantDomainService;
        this.loginAuditService = loginAuditService;
        this.authSessionStore = authSessionStore;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponseVO login(LoginRequest request, String loginIp, String userAgent) {
        String account = request.account();
        SysUserEntity user = userDomainService.findLoginUser(account)
                .orElseThrow(() -> {
                    loginAuditService.log(null, null, account, "PASSWORD", "FAIL", "用户不存在", loginIp, userAgent);
                    return new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "密码错误", loginIp, userAgent);
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        List<UserTenantAccess> enabledTenantAccessList = tenantDomainService.listUserTenantAccess(user.getId()).stream()
                .filter(access -> access.getTenant() != null && "ENABLED".equalsIgnoreCase(access.getTenant().getStatus()))
                .toList();

        if (enabledTenantAccessList.isEmpty()) {
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "用户未绑定可用租户", loginIp, userAgent);
            throw new BizException(ErrorCode.TENANT_ERROR, "当前用户未绑定可用租户");
        }

        TenantInfoEntity currentTenant = pickCurrentTenant(enabledTenantAccessList);
        AuthSession session = buildNewSession(user, currentTenant == null ? null : currentTenant.getId(), loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        authSessionStore.save(session, Duration.ofSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));

        LoginResponseVO response = new LoginResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user));
        response.setTenants(tenantDomainService.toMyTenantVO(enabledTenantAccessList));
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));

        loginAuditService.log(user.getId(), currentTenant == null ? null : currentTenant.getId(), user.getUsername(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    public void logout(CurrentUser currentUser, String loginIp, String userAgent) {
        if (currentUser.getSessionId() == null) {
            return;
        }
        authSessionStore.findBySessionId(currentUser.getSessionId()).ifPresent(authSessionStore::remove);
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
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken非法");
        }

        AuthSession session = authSessionStore.findBySessionId(tokenClaims.getSessionId())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "会话已失效"));

        validateSessionForRefresh(session, tokenClaims);

        String newRefreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(newRefreshTokenId);
        session.setExpireTime(LocalDateTime.now().plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
        authSessionStore.save(session, Duration.ofSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));

        RefreshTokenResponseVO response = new RefreshTokenResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, newRefreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setSessionVersion(session.getSessionVersion());
        response.setPermissionsVersion("0");
        return response;
    }

    public CurrentUserVO currentUser(CurrentUser currentUser) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在"));

        TenantSummaryVO currentTenant = tenantDomainService.findTenantById(currentUser.getCurrentTenantId())
                .map(tenantDomainService::toTenantSummary)
                .orElse(null);

        CurrentUserVO response = new CurrentUserVO();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRealName(user.getRealName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setCurrentTenant(currentTenant);
        response.setSessionId(currentUser.getSessionId());
        response.setPermissionsVersion("0");
        response.setSessionVersion(currentUser.getSessionVersion());
        return response;
    }

    private void validateSessionForRefresh(AuthSession session, TokenClaims tokenClaims) {
        if (!session.getUserId().equals(tokenClaims.getUserId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken与会话不匹配");
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(tokenClaims.getSessionVersion())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话版本已变更，请重新登录");
        }
        if (session.getRefreshTokenId() == null || !session.getRefreshTokenId().equals(tokenClaims.getTokenId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken已失效");
        }
        if (session.getExpireTime() == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已过期，请重新登录");
        }
    }

    private TenantInfoEntity pickCurrentTenant(List<UserTenantAccess> accessList) {
        return accessList.stream()
                .filter(UserTenantAccess::isDefault)
                .findFirst()
                .map(UserTenantAccess::getTenant)
                .orElseGet(() -> accessList.size() == 1 ? accessList.get(0).getTenant() : null);
    }

    private AuthSession buildNewSession(SysUserEntity user, Long currentTenantId, String loginIp, String userAgent) {
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setCurrentTenantId(currentTenantId);
        session.setLoginTime(LocalDateTime.now());
        session.setExpireTime(LocalDateTime.now().plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
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
        return authUserVO;
    }
}
