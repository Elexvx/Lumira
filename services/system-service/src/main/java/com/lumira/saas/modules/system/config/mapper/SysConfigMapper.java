package com.lumira.saas.modules.system.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumira.saas.modules.system.config.entity.SysConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfigEntity> {
    String findValue(
            @Param("tenantId") Long tenantId,
            @Param("configKey") String configKey,
            @Param("configScope") String configScope
    );

    List<SysConfigEntity> listEffectiveValues(
            @Param("tenantId") Long tenantId,
            @Param("configScope") String configScope,
            @Param("keys") List<String> keys
    );

    Long findIdByTenantAndKey(@Param("tenantId") Long tenantId, @Param("configKey") String configKey);

    void upsertPlatformConfig(@Param("entity") SysConfigEntity entity);
}
