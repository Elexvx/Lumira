package com.lumira.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.file.event.PlatformEventOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FilePlatformEventOutboxMapper extends BaseMapper<PlatformEventOutboxEntity> {
}
