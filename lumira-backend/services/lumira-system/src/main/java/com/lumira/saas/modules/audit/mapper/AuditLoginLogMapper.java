package com.lumira.saas.modules.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.audit.entity.AuditLoginLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLoginLogMapper extends BaseMapper<AuditLoginLogEntity> {
}
