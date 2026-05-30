package com.legendary.invention.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.message.entity.AuditOperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageAuditOperationLogMapper extends BaseMapper<AuditOperationLogEntity> {
}
