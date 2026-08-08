package com.lumira.saas.modules.ai.repository;

import com.lumira.common.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiPlatformQueryRepository {

    List<Map<String, Object>> findMenus(CurrentUser actor, String status, int limit);

    Optional<Map<String, Object>> findConfig(CurrentUser actor, String configKey);
}
