package com.lumira.saas.modules.system.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.api.ai.AiAuditReadPort;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.system.audit.infrastructure.JdbcSystemAuditQueryRepository;
import com.lumira.saas.modules.system.infrastructure.SystemManagementPersistenceDependencies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemManagementAppServiceAuditRoleOnlyTest {

    @Test
    void loginLogsDoNotExposeTenantPredicate() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        service.listLoginLogs(currentUser(), "alice", 1, 10);

        assertNoTenantSurface(queryOperations.lastSql);
        assertArrayEquals(new Object[]{"%alice%", 10L, 0L}, queryOperations.lastArgs);
    }

    @Test
    void operationLogsDoNotExposeTenantPredicate() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        service.listOperationLogs(currentUser(), "alice", 1, 10);

        assertNoTenantSurface(queryOperations.lastSql);
        assertArrayEquals(new Object[]{"%alice%", 10L, 0L}, queryOperations.lastArgs);
    }

    @Test
    void verificationLogsDoNotExposeTenantPredicate() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        service.listVerificationLogs(currentUser(), "sms", "login", "SUCCESS", null, null, 1, 10);

        assertNoTenantSurface(queryOperations.lastSql);
        assertArrayEquals(new Object[]{"SMS", "LOGIN", "SUCCESS", 10L, 0L}, queryOperations.lastArgs);
    }

    @Test
    void aiCallLogsDoNotExposeTenantPredicate() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        RecordingAiAuditReadPort aiAuditReadPort = new RecordingAiAuditReadPort();
        SystemManagementAppService service = buildService(queryOperations, aiAuditReadPort);

        service.listAiCallLogs(currentUser(), 42L, "skill", "SUCCESS", null, null, 1, 20);

        assertEquals(new AiAuditReadPort.AiToolAuditSearch(42L, "skill", "SUCCESS", null, null, 1, 20), aiAuditReadPort.lastSearch);
        assertEquals(0, queryOperations.queryCallCount);
    }

    @Test
    void loginLogsShouldRequireAuditPermissionBeforeQuery() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        BizException error = assertThrows(
                BizException.class,
                () -> service.listLoginLogs(currentUser("system:config:view"), "alice", 1, 10)
        );

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        assertEquals(0, queryOperations.queryCallCount);
    }

    @Test
    void loginLogsShouldAllowDedicatedLoginAuditPermission() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        service.listLoginLogs(currentUser("audit:login:view"), "alice", 1, 10);

        assertNoTenantSurface(queryOperations.lastSql);
        assertArrayEquals(new Object[]{"%alice%", 10L, 0L}, queryOperations.lastArgs);
    }

    @Test
    void operationLogsShouldAllowDedicatedOperationAuditPermission() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        service.listOperationLogs(currentUser("audit:operation:view"), "alice", 1, 10);

        assertNoTenantSurface(queryOperations.lastSql);
        assertArrayEquals(new Object[]{"%alice%", 10L, 0L}, queryOperations.lastArgs);
    }

    @Test
    void verificationLogsShouldAllowDedicatedOperationAuditPermission() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);

        service.listVerificationLogs(currentUser("audit:operation:view"), "sms", "login", "SUCCESS", null, null, 1, 10);

        assertNoTenantSurface(queryOperations.lastSql);
        assertArrayEquals(new Object[]{"SMS", "LOGIN", "SUCCESS", 10L, 0L}, queryOperations.lastArgs);
    }

    @Test
    void aiCallLogsShouldAllowDedicatedOperationAuditPermission() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        RecordingAiAuditReadPort aiAuditReadPort = new RecordingAiAuditReadPort();
        SystemManagementAppService service = buildService(queryOperations, aiAuditReadPort);

        service.listAiCallLogs(currentUser("audit:operation:view"), 42L, "skill", "SUCCESS", null, null, 1, 20);

        assertEquals(new AiAuditReadPort.AiToolAuditSearch(42L, "skill", "SUCCESS", null, null, 1, 20), aiAuditReadPort.lastSearch);
        assertEquals(0, queryOperations.queryCallCount);
    }

    @Test
    void loginLogsShouldRejectBlankUsernameBeforeQuery() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setUsername(" ");

        BizException error = assertThrows(
                BizException.class,
                () -> service.listLoginLogs(currentUser, "alice", 1, 10)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, queryOperations.queryCallCount);
    }

    @Test
    void loginLogsShouldRejectMissingSessionVersionBeforeQuery() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = buildService(queryOperations);
        CurrentUser currentUser = currentUser();
        currentUser.setSessionVersion(null);

        BizException error = assertThrows(
                BizException.class,
                () -> service.listLoginLogs(currentUser, "alice", 1, 10)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertEquals(0, queryOperations.queryCallCount);
    }

    private static void assertNoTenantSurface(String sql) {
        assertNotNull(sql);
        assertFalse(sql.contains("tenant_id"));
        assertFalse(sql.contains("tenantId"));
    }

    private static SystemManagementAppService buildService(MyBatisQueryOperations queryOperations) {
        return buildService(queryOperations, new RecordingAiAuditReadPort());
    }

    private static SystemManagementAppService buildService(
            MyBatisQueryOperations queryOperations,
            AiAuditReadPort aiAuditReadPort
    ) {
        SystemManagementPersistenceDependencies persistence = new SystemManagementPersistenceDependencies(
                null,
                null,
                null,
                null,
                new JdbcSystemAuditQueryRepository(queryOperations, aiAuditReadPort),
                null
        );
        return new SystemManagementAppService(
                persistence,
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

    private static CurrentUser currentUser() {
        return currentUser("audit:view");
    }

    private static CurrentUser currentUser(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setUsername("auditor");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private String lastSql;
        private Object[] lastArgs;
        private int queryCallCount;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCallCount += 1;
            this.lastSql = sql;
            this.lastArgs = args;
            return List.of();
        }
    }

    private static final class RecordingAiAuditReadPort implements AiAuditReadPort {
        private AiToolAuditSearch lastSearch;

        @Override
        public AiToolAuditPage findToolAudits(AiToolAuditSearch search) {
            this.lastSearch = search;
            return new AiToolAuditPage(List.of(), 0L, search.pageNo(), search.pageSize());
        }
    }
}
