package com.legendary.invention.saas.modules.localization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.saas.modules.localization.entity.LocalizationEntities.EntryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalizationEntryMapper extends BaseMapper<EntryEntity> {
}
