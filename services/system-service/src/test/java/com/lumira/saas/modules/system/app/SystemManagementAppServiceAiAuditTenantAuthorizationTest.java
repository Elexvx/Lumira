package com.lumira.saas.modules.system.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemManagementAppServiceAiAuditTenantAuthorizationTest {

    @Test
    void shouldRejectAiAuditLogsForOtherTenantWhenUserIsNotPlatformAdmin() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser tenantUser = currentUser(2002L, Set.of("audit:operation:view"));

        assertThrows(
                BizException.class,
                () -> service.listAiCallLogs(tenantUser, 3003L, null, null, null, null, null, 1, 10)
        );

        assertNull(queryOperations.lastSql);
    }

    @Test
    void shouldConstrainAiAuditLogsToCurrentTenantByDefault() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser tenantUser = currentUser(2002L, Set.of("audit:operation:view"));

        service.listAiCallLogs(tenantUser, null, null, null, null, null, null, 1, 10);

        assertTrue(queryOperations.lastSql.contains("and l.tenant_id = ?"));
        assertEquals(2002L, queryOperations.lastArgs[0]);
    }

    @Test
    void shouldRejectAiAuditLogsForOtherTenantWhenPlatformUserLacksWildcardPermission() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser platformAuditor = currentUser(1001L, Set.of("audit:operation:view"));

        assertThrows(
                BizException.class,
                () -> service.listAiCallLogs(platformAuditor, 3003L, null, null, null, null, null, 1, 10)
        );

        assertNull(queryOperations.lastSql);
    }

    @Test
    void shouldAllowPlatformAdminToFilterAiAuditLogsByTenant() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser platformAdmin = currentUser(1001L, Set.of("*"));

        service.listAiCallLogs(platformAdmin, 3003L, null, null, null, null, null, 1, 10);

        assertTrue(queryOperations.lastSql.contains("and l.tenant_id = ?"));
        assertEquals(3003L, queryOperations.lastArgs[0]);
    }

    private static SystemManagementAppService buildService(MyBatisQueryOperations queryOperations) {
        return new SystemManagementAppService(
                queryOperations,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static CurrentUser currentUser(Long tenantId, Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(9001L);
        currentUser.setUsername("audit-user");
        currentUser.setCurrentTenantId(tenantId);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastSql;
        private Object[] lastArgs;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastSql = sql;
            this.lastArgs = args;
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (Long.class.equals(requiredType)) {
                return requiredType.cast(0L);
            }
            return null;
        }
    }
}
