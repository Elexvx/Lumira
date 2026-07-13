package com.lumira.saas.modules.audit.repository;

import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;

public interface OperationAuditRepository {
    int insert(AuditOperationLogEntity entity);
}
