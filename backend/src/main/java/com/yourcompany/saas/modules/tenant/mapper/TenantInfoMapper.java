package com.yourcompany.saas.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yourcompany.saas.modules.tenant.entity.TenantInfoEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantInfoMapper extends BaseMapper<TenantInfoEntity> {
}
