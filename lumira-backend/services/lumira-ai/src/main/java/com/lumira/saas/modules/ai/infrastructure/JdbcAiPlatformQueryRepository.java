package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.api.ai.AiSystemReadPort;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.repository.AiPlatformQueryRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiPlatformQueryRepository implements AiPlatformQueryRepository {

    private final AiSystemReadPort systemReadPort;

    public JdbcAiPlatformQueryRepository(AiSystemReadPort systemReadPort) {
        this.systemReadPort = systemReadPort;
    }

    @Override
    public List<Map<String, Object>> findMenus(CurrentUser actor, String status, int limit) {
        return systemReadPort.findMenus(actor, status, limit).stream()
                .map(menu -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", menu.id());
                    item.put("parentId", menu.parentId());
                    item.put("menuCode", menu.menuCode());
                    item.put("menuName", menu.menuName());
                    item.put("menuType", menu.menuType());
                    item.put("path", menu.path());
                    item.put("component", menu.component());
                    item.put("permissionKey", menu.permissionKey());
                    item.put("status", menu.status());
                    item.put("sortNo", menu.sortNo());
                    return item;
                })
                .toList();
    }

    @Override
    public Optional<Map<String, Object>> findConfig(CurrentUser actor, String configKey) {
        AiSystemReadPort.ConfigItem config = systemReadPort.findConfig(actor, configKey);
        if (config == null) {
            return Optional.empty();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("configKey", config.configKey());
        item.put("configName", config.configName());
        item.put("configValue", config.configValue());
        item.put("system", config.system());
        return Optional.of(item);
    }
}
