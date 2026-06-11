package com.lumira.message.app;

import com.lumira.common.web.TraceContext;
import com.lumira.message.entity.AuditOperationLogEntity;
import com.lumira.message.mapper.MessageAuditOperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service("messageOperationAuditService")
public class OperationAuditService {

    private final MessageAuditOperationLogMapper auditOperationLogMapper;

    public OperationAuditService(MessageAuditOperationLogMapper auditOperationLogMapper) {
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
