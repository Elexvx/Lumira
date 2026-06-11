package com.lumira.saas.modules.system.sensitive.app;

import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;

@Service
public class SensitiveWordPluginStateService {

    private static final String PLUGIN_CODE = "sensitive-words";

    private final MyBatisQueryOperations jdbcTemplate;

    public SensitiveWordPluginStateService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isEnabled(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return false;
        }
        return isEnabled(resolveTenantId(currentUser));
    }

    public boolean isEnabled(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from sys_plugin_tenant
                        where tenant_id = ?
                          and plugin_code = ?
                          and enabled = 1
                          and deleted = 0
                        """,
                Long.class,
                tenantId,
                PLUGIN_CODE
        );
        return count != null && count > 0 && hasSensitiveWordTable();
    }

    public void ensureEnabled(CurrentUser currentUser) {
        if (!isEnabled(currentUser)) {
            throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "敏感词拦截插件未启用");
        }
    }

    public boolean hasSensitiveWordTable() {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from information_schema.tables
                        where table_schema = database()
                          and table_name = 'sys_sensitive_word'
                        """,
                Long.class
        );
        return count != null && count > 0;
    }

    private Long resolveTenantId(CurrentUser currentUser) {
        return currentUser.getCurrentTenantId() == null ? PlatformConstants.PLATFORM_TENANT_ID : currentUser.getCurrentTenantId();
    }
}
