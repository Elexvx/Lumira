package com.legendary.invention.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.message.app.PlatformEventOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformEventOutboxMapper extends BaseMapper<PlatformEventOutboxEntity> {
}
