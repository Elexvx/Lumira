package com.yourcompany.saas.modules.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yourcompany.saas.modules.tenant.entity.SysUserTenantEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserTenantMapper extends BaseMapper<SysUserTenantEntity> {
}
