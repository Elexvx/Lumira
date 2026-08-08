package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.repository.AiPlatformQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

interface AiPlatformQueryFacade {

    List<Map<String, Object>> listMenus(CurrentUser actor, String status, int limit);

    Map<String, Object> readConfig(CurrentUser actor, String configKey);
}

@Service
class DefaultAiPlatformQueryFacade implements AiPlatformQueryFacade {

    private final AiPlatformQueryRepository platformQueryRepository;

    DefaultAiPlatformQueryFacade(AiPlatformQueryRepository platformQueryRepository) {
        this.platformQueryRepository = platformQueryRepository;
    }

    @Override
    public List<Map<String, Object>> listMenus(CurrentUser actor, String status, int limit) {
        return platformQueryRepository.findMenus(actor, status, limit);
    }

    @Override
    public Map<String, Object> readConfig(CurrentUser actor, String configKey) {
        return platformQueryRepository.findConfig(actor, configKey).orElse(null);
    }
}
