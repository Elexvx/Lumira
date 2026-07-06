package com.lumira.saas.modules.audit.app;

import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditOperationLogMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class OperationAuditService {

    private final AuditOperationLogMapper auditOperationLogMapper;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;

    public OperationAuditService(
            AuditOperationLogMapper auditOperationLogMapper,
            ObjectProvider<OwnerRuntimeMetrics> ownerRuntimeMetricsProvider
    ) {
        this.auditOperationLogMapper = auditOperationLogMapper;
        this.ownerRuntimeMetrics = ownerRuntimeMetricsProvider.getIfAvailable();
    }

    public void log(
            Long userId,
            String userUuid,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        AuditOperationLogEntity entity = new AuditOperationLogEntity();
        entity.setUserId(userId);
        entity.setUserUuid(requireAuditUserUuid(userId, userUuid));
        entity.setUsername(username);
        entity.setModuleName(moduleName);
        entity.setActionName(actionName);
        entity.setOperationType(operationType);
        entity.setResultStatus(resultStatus);
        entity.setDetailMessage(detailMessage);
        entity.setRequestId(TraceContext.getRequestId());
        entity.setTraceId(TraceContext.getTraceId());
        entity.setCreatedBy(userId == null ? 0 : userId);
        entity.setCreatedByUuid(entity.getUserUuid());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        try {
            int inserted = auditOperationLogMapper.insert(entity);
            if (inserted != 1) {
                throw new BizException(ErrorCode.BIZ_ERROR, "Operation audit changed, please retry");
            }
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

    private String requireAuditUserUuid(Long userId, String userUuid) {
        if (userId == null) {
            return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
        }
        if (userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted audit user is required");
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted audit user uuid is required");
        }
        return userUuid.trim();
    }
}
