package com.lumira.saas.modules.system.workorder.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkOrderFeedbackPluginStateService {

    private static final String PLUGIN_CODE = "work-order-feedback";
    private static final String STATUS_ENABLED = "ENABLED";

    private final MyBatisQueryOperations jdbcTemplate;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private volatile Boolean workOrderTableExists;

    public WorkOrderFeedbackPluginStateService(MyBatisQueryOperations jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    @Autowired
    public WorkOrderFeedbackPluginStateService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            SystemInternalApi systemInternalApi
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
    }

    public WorkOrderFeedbackPluginStateService(
            MyBatisQueryOperations jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, permissionSnapshotService, null);
    }

    public boolean isEnabled(CurrentUser currentUser) {
        if (!isTrustedActiveUser(currentUser)) {
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
        return enabled && hasWorkOrderTable();
    }

    private boolean isTrustedActiveUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return false;
        }
        Long userId = currentUser.getUserId();
        String userUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            return false;
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(userSnapshot.userUuid())
                    || !userUuid.equals(userSnapshot.userUuid().trim())
                    || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                return false;
            }
            currentUser.setUserId(userSnapshot.userId());
            currentUser.setUserUuid(userSnapshot.userUuid().trim());
            currentUser.setUsername(userSnapshot.username());
            userId = userSnapshot.userId();
            userUuid = userSnapshot.userUuid().trim();
        }
        return permissionSnapshotService == null || permissionSnapshotService.isTrustedActiveUser(userId, userUuid);
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
