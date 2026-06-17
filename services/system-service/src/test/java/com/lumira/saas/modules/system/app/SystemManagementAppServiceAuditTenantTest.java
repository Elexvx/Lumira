package com.lumira.saas.modules.system.app;

import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemManagementAppServiceAuditTenantTest {

    private RecordingQueryOperations queryOperations;
    private SystemManagementAppService service;

    @BeforeEach
    void setUp() {
        queryOperations = new RecordingQueryOperations();
        service = new SystemManagementAppService(
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
                null,
                null,
                null
        );
    }

    @Test
    void loginLogsRejectsTenantIdDifferentFromCurrentTenant() {
        CurrentUser currentUser = currentUser(1001L, Set.of("audit:login:view"));

        assertThrows(BizException.class, () -> service.listLoginLogs(currentUser, null, 2002L, 1, 10));
        assertEquals(null, queryOperations.lastQuerySql);
    }

    @Test
    void operationLogsUseCurrentTenantWhenTenantIdOmitted() {
        CurrentUser currentUser = currentUser(1001L, Set.of("audit:operation:view"));

        service.listOperationLogs(currentUser, null, null, 1, 10);

        assertTrue(queryOperations.lastQuerySql.contains("and l.tenant_id = ?"));
        assertArrayEquals(new Object[]{1001L, 10L, 0L}, queryOperations.lastQueryArgs);
        assertEquals(null, queryOperations.lastCountArgs);
    }

    @Test
    void aiCallLogsAllowExplicitTenantForPlatformAdministrator() {
        CurrentUser currentUser = currentUser(1001L, Set.of("*"));

        service.listAiCallLogs(currentUser, 2002L, null, null, null, null, null, 1, 10);

        assertTrue(queryOperations.lastQuerySql.contains("and l.tenant_id = ?"));
        assertArrayEquals(new Object[]{2002L, 10L, 0L}, queryOperations.lastQueryArgs);
        assertEquals(null, queryOperations.lastCountArgs);
    }

    private static CurrentUser currentUser(Long tenantId, Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("auditor");
        currentUser.setCurrentTenantId(tenantId);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastQuerySql;
        private Object[] lastQueryArgs;
        private Object[] lastCountArgs;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.lastQuerySql = sql;
            this.lastQueryArgs = args;
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.lastCountArgs = args;
            if (requiredType == Long.class) {
                return requiredType.cast(0L);
            }
            return null;
        }
    }
}
