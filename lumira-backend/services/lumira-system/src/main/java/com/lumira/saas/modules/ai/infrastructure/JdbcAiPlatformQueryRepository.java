package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.repository.AiPlatformQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcAiPlatformQueryRepository implements AiPlatformQueryRepository {

    private final MyBatisQueryOperations database;

    public JdbcAiPlatformQueryRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<Map<String, Object>> findMenus(String status, int limit) {
        String normalizedStatus = StringUtils.hasText(status) ? status : null;
        return database.queryForList("""
                select id, parent_id as parentId, menu_code as menuCode, menu_name as menuName,
                       menu_type as menuType, path, component, permission_key as permissionKey,
                       status, sort_no as sortNo
                from sys_menu
                where deleted = 0
                  and (? is null or status = ?)
                order by sort_no asc, id asc
                limit ?
                """, normalizedStatus, normalizedStatus, limit);
    }

    @Override
    public Optional<Map<String, Object>> findConfig(String configKey) {
        return database.queryForList("""
                select config_key as configKey, config_name as configName, config_value as configValue,
                       is_system as system
                from sys_config
                where config_key = ? and deleted = 0
                limit 1
                """, configKey).stream().findFirst();
    }
}
