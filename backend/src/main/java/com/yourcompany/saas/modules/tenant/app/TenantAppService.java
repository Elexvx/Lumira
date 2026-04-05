package com.yourcompany.saas.modules.tenant.app;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
import com.yourcompany.saas.modules.audit.app.LoginAuditService;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import com.yourcompany.saas.modules.tenant.domain.TenantDomainService;
import com.yourcompany.saas.modules.tenant.entity.TenantInfoEntity;
import com.yourcompany.saas.modules.tenant.vo.CurrentTenantVO;
import com.yourcompany.saas.modules.tenant.vo.MyTenantVO;
import com.yourcompany.saas.modules.tenant.vo.SwitchTenantVO;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class TenantAppService {

    private final TenantDomainService tenantDomainService;
    private final AuthSessionStore authSessionStore;
    private final JwtTokenService jwtTokenService;
    private final LoginAuditService loginAuditService;
    private final PermissionSnapshotService permissionSnapshotService;

    public TenantAppService(
            TenantDomainService tenantDomainService,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            LoginAuditService loginAuditService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this.tenantDomainService = tenantDomainService;
        this.authSessionStore = authSessionStore;
        this.jwtTokenService = jwtTokenService;
        this.loginAuditService = loginAuditService;
        this.permissionSnapshotService = permissionSnapshotService;
    }

    public CurrentTenantVO currentTenant(CurrentUser currentUser) {
        CurrentTenantVO response = new CurrentTenantVO();
        if (currentUser.getCurrentTenantId() == null) {
            response.setHasCurrentTenant(false);
            response.setCurrentTenant(null);
            return response;
        }

        TenantSummaryVO tenant = tenantDomainService.findTenantById(currentUser.getCurrentTenantId())
                .map(tenantDomainService::toTenantSummary)
                .orElse(null);

        response.setHasCurrentTenant(tenant != null);
        response.setCurrentTenant(tenant);
        return response;
    }

    public List<MyTenantVO> myTenants(CurrentUser currentUser) {
        return tenantDomainService.toMyTenantVO(tenantDomainService.listUserTenantAccess(currentUser.getUserId()));
    }

    public SwitchTenantVO switchTenant(CurrentUser currentUser, Long targetTenantId, String loginIp, String userAgent) {
        if (!tenantDomainService.isUserInTenant(currentUser.getUserId(), targetTenantId)) {
            loginAuditService.log(currentUser.getUserId(), targetTenantId, currentUser.getUsername(), "TENANT_SWITCH", "FAIL", "用户不属于目标租户", loginIp, userAgent);
            throw new BizException(ErrorCode.FORBIDDEN, "无权切换到该租户");
        }

        TenantInfoEntity tenantInfo = tenantDomainService.findTenantById(targetTenantId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.TENANT_ERROR,
                        "租户不存在: " + targetTenantId,
                        ErrorCode.TENANT_ERROR.getDefaultUserMessage()
                ));

        if (!"ENABLED".equalsIgnoreCase(tenantInfo.getStatus())) {
            throw new BizException(
                    ErrorCode.TENANT_ERROR,
                    "租户已停用: " + targetTenantId,
                    ErrorCode.TENANT_ERROR.getDefaultUserMessage()
            );
        }

        AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "租户切换失败，会话已失效",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        session.setCurrentTenantId(targetTenantId);
        session.setSessionVersion(session.getSessionVersion() == null ? 1 : session.getSessionVersion() + 1);

        Duration sessionTtl = jwtTokenService.calculateSessionTtl(session.getExpireTime());
        if (sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "租户切换失败，会话已过期",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }

        authSessionStore.save(session, sessionTtl, true);

        SwitchTenantVO response = new SwitchTenantVO();
        response.setCurrentTenant(tenantDomainService.toTenantSummary(tenantInfo));
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setSessionVersion(session.getSessionVersion());
        permissionSnapshotService.invalidateTenant(targetTenantId);
        response.setPermissionsVersion(permissionSnapshotService.loadSnapshot(targetTenantId, currentUser.getUserId()).getVersion());

        loginAuditService.log(currentUser.getUserId(), targetTenantId, currentUser.getUsername(), "TENANT_SWITCH", "SUCCESS", null, loginIp, userAgent);
        return response;
    }
}
