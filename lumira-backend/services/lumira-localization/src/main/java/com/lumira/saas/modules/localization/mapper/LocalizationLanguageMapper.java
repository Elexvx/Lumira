package com.lumira.saas.modules.localization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.LanguageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalizationLanguageMapper extends BaseMapper<LanguageEntity> {
}
