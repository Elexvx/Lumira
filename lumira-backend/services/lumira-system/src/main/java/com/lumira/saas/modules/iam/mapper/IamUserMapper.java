package com.lumira.saas.modules.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.iam.entity.IamUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IamUserMapper extends BaseMapper<IamUserEntity> {
}
