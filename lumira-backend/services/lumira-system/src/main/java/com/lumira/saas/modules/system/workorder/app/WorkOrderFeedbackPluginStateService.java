package com.lumira.saas.modules.system.workorder.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PlatformContext;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderFeedbackPluginStateService {

    private static final String PLUGIN_CODE = "work-order-feedback";

    private final MyBatisQueryOperations jdbcTemplate;
    private volatile Boolean workOrderTableExists;

    public WorkOrderFeedbackPluginStateService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isEnabled(CurrentUser currentUser) {
        if (currentUser == null || !currentUser.isAuthenticated()) {
            return false;
        }
        Long tenantId = PlatformContext.compatibilityTenantId();
        boolean enabled = jdbcTemplate.exists(
                """
                        select 1
                        from sys_plugin_tenant
                        where tenant_id = ?
                          and plugin_code = ?
                          and enabled = 1
                          and deleted = 0
                        limit 1
                        """,
                tenantId,
                PLUGIN_CODE
        );
        return enabled && hasWorkOrderTable();
    }

    public void ensureEnabled(CurrentUser currentUser) {
        if (!isEnabled(currentUser)) {
            throw new BizException(ErrorCode.PLUGIN_NOT_ENABLED, "工单反馈插件未启用");
        }
    }

    private boolean hasWorkOrderTable() {
        Boolean cached = workOrderTableExists;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            Boolean refreshed = workOrderTableExists;
            if (refreshed == null) {
                refreshed = jdbcTemplate.exists(
                        """
                                select 1
                                from information_schema.tables
                                where table_schema = database()
                                  and table_name = 'sys_work_order_feedback'
                                limit 1
                                """
                );
                workOrderTableExists = refreshed;
            }
            return refreshed;
        }
    }
}
