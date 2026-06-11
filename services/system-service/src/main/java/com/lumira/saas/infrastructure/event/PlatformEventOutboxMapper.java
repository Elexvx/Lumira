package com.lumira.saas.infrastructure.event;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformEventOutboxMapper extends BaseMapper<PlatformEventOutboxEntity> {
}
