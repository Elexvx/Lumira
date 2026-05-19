package com.legendary.invention.saas.modules.localization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.saas.modules.localization.entity.LocalizationEntities.NamespaceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalizationNamespaceMapper extends BaseMapper<NamespaceEntity> {
}
