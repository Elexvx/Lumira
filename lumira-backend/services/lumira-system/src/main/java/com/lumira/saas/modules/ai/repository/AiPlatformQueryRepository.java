package com.lumira.saas.modules.ai.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiPlatformQueryRepository {

    List<Map<String, Object>> findMenus(String status, int limit);

    Optional<Map<String, Object>> findConfig(String configKey);
}
