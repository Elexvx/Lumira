package com.legendary.invention.message.app;

import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.message.entity.AuditOperationLogEntity;
import com.legendary.invention.message.mapper.AuditOperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OperationAuditService {

    private final AuditOperationLogMapper auditOperationLogMapper;

    public OperationAuditService(AuditOperationLogMapper auditOperationLogMapper) {
        this.auditOperationLogMapper = auditOperationLogMapper;
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
        entity.setCreatedBy(userId == null ? 0L : userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        auditOperationLogMapper.insert(entity);
    }
}
