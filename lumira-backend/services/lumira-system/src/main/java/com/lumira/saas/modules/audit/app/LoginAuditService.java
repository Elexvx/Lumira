package com.lumira.saas.modules.audit.app;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.audit.entity.AuditLoginLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditLoginLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoginAuditService {

    private final AuditLoginLogMapper auditLoginLogMapper;

    public LoginAuditService(AuditLoginLogMapper auditLoginLogMapper) {
        this.auditLoginLogMapper = auditLoginLogMapper;
    }

    public void log(
            Long userId,
            Long tenantId,
            String username,
            String loginType,
            String loginResult,
            String failReason,
            String loginIp,
            String userAgent
    ) {
        AuditLoginLogEntity entity = new AuditLoginLogEntity();
        entity.setUserId(userId);
        entity.setTenantId(tenantId == null ? PlatformConstants.PLATFORM_TENANT_ID : tenantId);
        entity.setUsername(username);
        entity.setLoginType(loginType);
        entity.setLoginResult(loginResult);
        entity.setFailReason(failReason);
        entity.setLoginIp(loginIp);
        entity.setUserAgent(userAgent);
        entity.setRequestId(TraceContext.getRequestId());
        entity.setTraceId(TraceContext.getTraceId());
        entity.setCreatedAt(LocalDateTime.now());
        auditLoginLogMapper.insert(entity);
    }
}
