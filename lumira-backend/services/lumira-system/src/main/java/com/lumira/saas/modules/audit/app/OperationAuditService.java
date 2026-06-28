package com.lumira.saas.modules.audit.app;

import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditOperationLogMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.architecture.application.OwnerRuntimeMetrics;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class OperationAuditService {

    private final AuditOperationLogMapper auditOperationLogMapper;
    private final OwnerRuntimeMetrics ownerRuntimeMetrics;
    private final MyBatisQueryOperations queryOperations;

    public OperationAuditService(AuditOperationLogMapper auditOperationLogMapper) {
        this(auditOperationLogMapper, null, null);
    }

    public OperationAuditService(AuditOperationLogMapper auditOperationLogMapper, OwnerRuntimeMetrics ownerRuntimeMetrics) {
        this(auditOperationLogMapper, ownerRuntimeMetrics, null);
    }

    @Autowired
    public OperationAuditService(
            AuditOperationLogMapper auditOperationLogMapper,
            OwnerRuntimeMetrics ownerRuntimeMetrics,
            MyBatisQueryOperations queryOperations
    ) {
        this.auditOperationLogMapper = auditOperationLogMapper;
        this.ownerRuntimeMetrics = ownerRuntimeMetrics;
        this.queryOperations = queryOperations;
    }

    public void log(
            Long userId,
            String username,
            String moduleName,
            String actionName,
            String operationType,
            String resultStatus,
            String detailMessage
    ) {
        log(userId, resolveUserUuid(userId), username, moduleName, actionName, operationType, resultStatus, detailMessage);
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
        entity.setUserUuid(StringUtils.hasText(userUuid) ? userUuid : resolveUserUuid(userId));
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

    private String resolveUserUuid(Long userId) {
        if (userId == null || queryOperations == null) {
            return null;
        }
        try {
            return queryOperations.queryForObject(
                    "select uuid from sys_user where id = ? and deleted = 0 limit 1",
                    String.class,
                    userId
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
