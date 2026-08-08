package com.lumira.saas.modules.system.config.repository;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;

/** Persistence boundary for editable PLATFORM-scoped system configuration rows. */
public interface SystemConfigurationManagementRepository {
    PageResponse<SystemVO.ConfigVO> findConfigs(ConfigSearch search);

    SystemVO.ConfigVO findActiveConfig(Long configId);

    String findEditablePlatformValue(Long configId, String configKey);

    int updateEditablePlatformConfig(ConfigWrite command);

    ConfigWriteResult createPlatformConfig(ConfigCreate command);

    SystemVO.ConfigVO findLatestActiveConfigByKey(String configKey);

    record Actor(Long userId, String userUuid) {}

    record ConfigSearch(String configKey, String configName, long pageNo, long pageSize) {}

    record ConfigVersion(Long id, String configKey) {}

    record ConfigWrite(
            ConfigVersion existing,
            String configKey,
            String configName,
            String configValue,
            String remark,
            Actor actor,
            LocalDateTime updatedAt
    ) {}

    record ConfigCreate(
            String configKey,
            String configName,
            String configValue,
            String remark,
            Actor actor
    ) {}

    record ConfigWriteResult(int writeCount, Long configId) {}
}
