package com.lumira.saas.modules.system.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemManagementAppServiceVerificationLogsTenantScopeTest {

    @Test
    void shouldRejectTenantScopedUserRequestingAnotherTenantsVerificationLogs() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser currentUser = currentUser(2001L, Set.of("audit:operation:view"));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.listVerificationLogs(currentUser, 3001L, null, null, null, null, null, 1, 10)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertFalse(queryOperations.queried);
    }

    @Test
    void shouldDefaultVerificationLogsToCurrentTenantWhenTenantIdIsOmitted() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser currentUser = currentUser(2001L, Set.of("audit:operation:view"));

        service.listVerificationLogs(currentUser, null, null, null, null, null, null, 1, 10);

        assertTrue(queryOperations.queried);
        assertTrue(queryOperations.querySql.contains("and l.tenant_id = ?"));
        assertArrayEquals(new Object[]{2001L, 10L, 0L}, queryOperations.queryArgs);
        assertEquals(null, queryOperations.countArgs);
    }

    @Test
    void shouldAllowPlatformSuperUserToFilterVerificationLogsByTenant() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser currentUser = currentUser(1001L, Set.of("*"));

        service.listVerificationLogs(currentUser, 3001L, null, null, null, null, null, 1, 10);

        assertTrue(queryOperations.queried);
        assertTrue(queryOperations.querySql.contains("and l.tenant_id = ?"));
        assertArrayEquals(new Object[]{3001L, 10L, 0L}, queryOperations.queryArgs);
        assertEquals(null, queryOperations.countArgs);
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
                null,
                null,
                null
        );
    }

    private static CurrentUser currentUser(Long tenantId, Set<String> permissions) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1L);
        currentUser.setUsername("auditor");
        currentUser.setCurrentTenantId(tenantId);
        currentUser.setPermissions(permissions);
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean queried;
        private String querySql;
        private Object[] queryArgs;
        private Object[] countArgs;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queried = true;
            querySql = sql;
            queryArgs = Arrays.copyOf(args, args.length);
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            countArgs = Arrays.copyOf(args, args.length);
            return requiredType.cast(0L);
        }
    }
}
