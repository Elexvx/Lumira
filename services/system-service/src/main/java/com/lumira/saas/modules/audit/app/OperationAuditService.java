package com.lumira.saas.modules.audit.app;

import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditOperationLogMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OperationAuditService {

    private final AuditOperationLogMapper auditOperationLogMapper;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;

    public OperationAuditService(AuditOperationLogMapper auditOperationLogMapper) {
        this(auditOperationLogMapper, null);
    }

    @Autowired
    public OperationAuditService(AuditOperationLogMapper auditOperationLogMapper, OwnerRuntimeMetrics ownerRuntimeMetrics) {
        this.auditOperationLogMapper = auditOperationLogMapper;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
    }

    public void log(
            Long tenantId,
            Long userId,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        AuditOperationLogEntity entity = new AuditOperationLogEntity();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setModuleName(moduleName);
        entity.setActionName(actionName);
        entity.setOperationType(operationType);
        entity.setResultStatus(resultStatus);
        entity.setDetailMessage(detailMessage);
        entity.setRequestId(TraceContext.getRequestId());
        entity.setTraceId(TraceContext.getTraceId());
        entity.setCreatedBy(userId == null ? 0 : userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        try {
            auditOperationLogMapper.insert(entity);
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordPlatformAuditWriteSuccess();
            }
        } catch (RuntimeException exception) {
            if (ownerRuntimeMetrics != null) {
                ownerRuntimeMetrics.recordPlatformAuditWriteFailure();
            }
            throw exception;
        }
    }
}
