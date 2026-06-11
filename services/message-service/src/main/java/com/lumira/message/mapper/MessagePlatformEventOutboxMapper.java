package com.lumira.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.message.app.PlatformEventOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessagePlatformEventOutboxMapper extends BaseMapper<PlatformEventOutboxEntity> {
}
