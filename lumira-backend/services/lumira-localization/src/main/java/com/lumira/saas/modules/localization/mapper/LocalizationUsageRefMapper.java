package com.lumira.saas.modules.localization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.UsageRefEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalizationUsageRefMapper extends BaseMapper<UsageRefEntity> {
}
