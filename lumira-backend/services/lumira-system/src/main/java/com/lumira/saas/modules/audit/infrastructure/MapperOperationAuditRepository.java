package com.lumira.saas.modules.audit.infrastructure;

import com.lumira.saas.modules.audit.entity.AuditOperationLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditOperationLogMapper;
import com.lumira.saas.modules.audit.repository.OperationAuditRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MapperOperationAuditRepository implements OperationAuditRepository {
    private final AuditOperationLogMapper mapper;

    public MapperOperationAuditRepository(AuditOperationLogMapper mapper) { this.mapper = mapper; }

    @Override
    public int insert(AuditOperationLogEntity entity) { return mapper.insert(entity); }
}
