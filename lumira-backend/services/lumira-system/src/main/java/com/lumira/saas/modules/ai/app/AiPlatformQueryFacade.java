package com.lumira.saas.modules.ai.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

interface AiPlatformQueryFacade {

    List<Map<String, Object>> listMenus(Long tenantId, String status, int limit);

    Map<String, Object> readConfig(Long tenantId, String configKey);
}

@Service
class DefaultAiPlatformQueryFacade implements AiPlatformQueryFacade {

    private final MyBatisQueryOperations jdbcTemplate;

    DefaultAiPlatformQueryFacade(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> listMenus(Long tenantId, String status, int limit) {
        return jdbcTemplate.queryForList(
                """
                        select id, parent_id as parentId, menu_code as menuCode, menu_name as menuName,
                               menu_type as menuType, path, component, permission_key as permissionKey,
                               status, sort_no as sortNo
                        from sys_menu
                        where tenant_id = ?
                          and deleted = 0
                          and (? is null or status = ?)
                        order by sort_no asc, id asc
                        limit ?
                        """,
                tenantId,
                StringUtils.hasText(status) ? status : null,
                StringUtils.hasText(status) ? status : null,
                limit
        );
    }

    @Override
    public Map<String, Object> readConfig(Long tenantId, String configKey) {
        return jdbcTemplate.queryForList(
                """
                        select config_key as configKey, config_name as configName, config_value as configValue,
                               config_scope as configScope, is_system as system
                        from sys_config
                        where tenant_id = ?
                          and config_key = ?
                          and deleted = 0
                        limit 1
                        """,
                tenantId,
                configKey
        ).stream().findFirst().orElse(null);
    }
}
