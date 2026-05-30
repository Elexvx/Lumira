package com.legendary.invention.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legendary.invention.file.event.PlatformEventOutboxEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FilePlatformEventOutboxMapper extends BaseMapper<PlatformEventOutboxEntity> {
}
