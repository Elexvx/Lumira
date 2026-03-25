package com.yourcompany.saas.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yourcompany.saas.modules.user.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
}
