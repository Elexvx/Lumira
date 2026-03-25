package com.yourcompany.saas.modules.audit.app;

import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.modules.audit.entity.AuditLoginLogEntity;
import com.yourcompany.saas.modules.audit.mapper.AuditLoginLogMapper;
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
        entity.setTenantId(tenantId);
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
