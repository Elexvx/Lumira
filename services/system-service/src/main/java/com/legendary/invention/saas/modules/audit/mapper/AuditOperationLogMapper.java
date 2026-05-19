package com.legendary.invention.saas.modules.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.saas.modules.audit.entity.AuditOperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditOperationLogMapper extends BaseMapper<AuditOperationLogEntity> {
}
