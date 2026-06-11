package com.lumira.saas.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.ai.domain.AiEntities.AiMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessageEntity> {
}
