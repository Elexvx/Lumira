package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordPluginStateService {

    private static final String PLUGIN_CODE = "sensitive-words";
    private static final int REQUIRED_SENSITIVE_WORD_COLUMNS = 12;

    private final MyBatisQueryOperations jdbcTemplate;
    private volatile Boolean sensitiveWordSchemaReady;

    public SensitiveWordPluginStateService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isEnabled(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return false;
        }
        boolean enabled = jdbcTemplate.exists(
                """
                        select 1
                        from sys_plugin_definition d
                        join sys_plugin_version v
                          on v.plugin_code = d.plugin_code
                         and v.is_active = 1
                         and v.deleted = 0
                        where d.plugin_code = ?
                          and d.status = 'ENABLED'
                          and d.deleted = 0
                        limit 1
                        """,
                PLUGIN_CODE
        );
        return enabled && hasSensitiveWordSchema();
    }

    public void ensureEnabled(CurrentUser currentUser) {
        if (!isEnabled(currentUser)) {
            throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "敏感词拦截插件未启用");
        }
    }

    public boolean hasSensitiveWordSchema() {
        Boolean cached = sensitiveWordSchemaReady;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            Boolean refreshed = sensitiveWordSchemaReady;
            if (refreshed == null) {
                boolean tableExists = jdbcTemplate.exists(
                        """
                                select 1
                                from information_schema.tables
                                where table_schema = database()
                                  and table_name = 'sys_sensitive_word'
                                limit 1
                                """
                );
                refreshed = tableExists && hasRequiredSensitiveWordColumns();
                sensitiveWordSchemaReady = refreshed;
            }
            return refreshed;
        }
    }

    private boolean hasRequiredSensitiveWordColumns() {
        Long columnCount = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = 'sys_sensitive_word'
                          and column_name in (
                              'id', 'word', 'normalized_word', 'category', 'severity', 'action',
                              'enabled', 'created_by', 'created_at', 'updated_by', 'updated_at', 'deleted'
                          )
                        """,
                Long.class
        );
        return columnCount != null && columnCount >= REQUIRED_SENSITIVE_WORD_COLUMNS;
    }
}
