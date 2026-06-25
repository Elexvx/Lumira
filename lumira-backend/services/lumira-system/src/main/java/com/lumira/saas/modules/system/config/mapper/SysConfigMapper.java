package com.lumira.saas.modules.system.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfigEntity> {
    String findValue(
            @Param("configKey") String configKey,
            @Param("configScope") String configScope
    );

    List<SysConfigEntity> listEffectiveValues(
            @Param("configScope") String configScope,
            @Param("keys") List<String> keys
    );

    Long findIdByKey(@Param("configKey") String configKey);

    void upsertPlatformConfig(@Param("entity") SysConfigEntity entity);
}
