package com.lumira.saas.modules.system.role.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.common.security.CurrentUser;
import com.lumira.domain.event.DomainEvent;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemRoleManagementAppServiceTest {

    @Test
    void listRolesShouldReturnCountsAndDefaultRegistrationRole() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        PageResponse<SystemVO.RoleVO> page = service.listRoles(currentUser(), "common", "普通", "BUSINESS", 1, 10);

        assertEquals(2, page.getTotal());
        assertEquals(2, page.getRecords().size());
        assertEquals(3, page.getRecords().get(0).getPermissionCount());
        assertEquals(7, page.getRecords().get(0).getUserCount());
        assertTrue(page.getRecords().get(0).getDefaultRegistrationRole());
        assertEquals(0, jdbcTemplate.roleListCountQueries);
    }

    @Test
    void getRoleShouldReturnPermissionKeys() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        SystemVO.RoleDetailVO role = service.getRole(currentUser(), 2001L);

        assertEquals(List.of("system:user:view", "system:role:view"), role.getPermissionKeys());
        assertEquals(3, role.getPermissionCount());
        assertEquals(7, role.getUserCount());
        assertTrue(role.getDefaultRegistrationRole());
    }

    @Test
    void updateDefaultRegistrationRoleShouldWriteSysConfigAndReturnRole() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, mock(PermissionSnapshotService.class));

        SystemVO.DefaultRegistrationRoleVO role = service.updateDefaultRegistrationRole(currentUser(), 2001L);

        assertEquals("commonuser", jdbcTemplate.upsertedDefaultRoleCode);
        assertEquals("commonuser", role.getRoleCode());
        assertTrue(jdbcTemplate.auditLogged);
    }

    @Test
    void createRoleShouldWriteRolePermissionsAndInvalidateSnapshot() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        SystemDTO.RoleUpsertRequest request = roleRequest("auditor", "审计员", List.of("audit:view", "audit:view", "audit:export"));
        SystemVO.RoleDetailVO role = service.createRole(currentUser(), request);

        assertTrue(jdbcTemplate.insertedRole);
        assertEquals(1, jdbcTemplate.lastInsertIdQueries);
        assertTrue(jdbcTemplate.deletedRolePermissions);
        assertTrue(jdbcTemplate.insertedPermissionKeys.isEmpty());
        verify(permissionSnapshotService).invalidateTenant(1001L);
        assertEquals(2001L, role.getId());
    }

    @Test
    void updateRolePermissionsShouldReplacePermissionsAndInvalidateSnapshot() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        boolean updated = service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view", "system:user:view", "system:role:view"));

        assertTrue(updated);
        assertTrue(jdbcTemplate.deletedRolePermissions);
        assertEquals(List.of("system:user:view", "system:role:view"), jdbcTemplate.insertedPermissionKeys);
        verify(permissionSnapshotService).invalidateTenant(1001L);
    }

    @Test
    void updateRolePermissionsShouldRejectWildcardPermission() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService);

        boolean updated = service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view", "*", "system:role:view"));

        assertTrue(updated);
        assertTrue(jdbcTemplate.deletedRolePermissions);
        assertEquals(List.of("system:user:view", "system:role:view"), jdbcTemplate.insertedPermissionKeys);
        verify(permissionSnapshotService).invalidateTenant(1001L);
    }

    @Test
    void updateRolePermissionsShouldPublishDomainEventWhenPermissionSetChanges() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        RecordingDomainEventPublisher domainEventPublisher = new RecordingDomainEventPublisher();
        SystemRoleManagementAppService service = buildService(jdbcTemplate, permissionSnapshotService, domainEventPublisher);

        service.updateRolePermissions(currentUser(), 2001L, List.of("system:user:view"));

        assertEquals(1, domainEventPublisher.events.size());
        assertEquals("IAM_ROLE_PERMISSIONS_CHANGED", domainEventPublisher.events.getFirst().eventType());
    }

    private SystemRoleManagementAppService buildService(RecordingJdbcTemplate jdbcTemplate, PermissionSnapshotService permissionSnapshotService) {
        return buildService(jdbcTemplate, permissionSnapshotService, event -> {
        });
    }

    private SystemRoleManagementAppService buildService(
            RecordingJdbcTemplate jdbcTemplate,
            PermissionSnapshotService permissionSnapshotService,
            DomainEventPublisher domainEventPublisher
    ) {
        return new SystemRoleManagementAppService(
                new MyBatisQueryOperations(jdbcTemplate),
                permissionSnapshotService,
                new RecordingOperationAuditService(jdbcTemplate),
                domainEventPublisher
        );
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);
        return currentUser;
    }

    private SystemDTO.RoleUpsertRequest roleRequest(String roleCode, String roleName, List<String> permissionKeys) {
        SystemDTO.RoleUpsertRequest request = new SystemDTO.RoleUpsertRequest();
        request.setRoleCode(roleCode);
        request.setRoleName(roleName);
        request.setRoleType("BUSINESS");
        request.setPermissionKeys(permissionKeys);
        return request;
    }

    private static SystemVO.RoleVO role(Long id, String roleCode, String roleName) {
        SystemVO.RoleVO role = new SystemVO.RoleVO();
        role.setId(id);
        role.setTenantId(1001L);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleType("BUSINESS");
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        return role;
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private boolean insertedRole;
        private boolean deletedRolePermissions;
        private boolean auditLogged;
        private int lastInsertIdQueries;
        private int roleListCountQueries;
        private String upsertedDefaultRoleCode;
        private final List<String> insertedPermissionKeys = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("insert into sys_role ")) {
                insertedRole = true;
            }
            if (sql.contains("delete from sys_role_permission")) {
                deletedRolePermissions = true;
            }
            if (sql.contains("insert into sys_role_permission")) {
                insertedPermissionKeys.add(String.valueOf(args[2]));
            }
            if (sql.contains("insert into sys_config") || sql.contains("update sys_config")) {
                upsertedDefaultRoleCode = String.valueOf(args[3]);
            }
            return 1;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_role r")) {
                return castList(List.of(role(2001L, "commonuser", "普通用户"), role(2002L, "admin", "管理员")));
            }
            return List.of();
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> rse, Object... args) {
            Map<Long, Integer> result = new LinkedHashMap<>();
            if (sql.contains("from sys_role_permission")) {
                result.put(2001L, 3);
                result.put(2002L, 5);
            } else if (sql.contains("from sys_user_role")) {
                result.put(2001L, 7);
                result.put(2002L, 1);
            }
            return cast(result);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from sys_role r")) {
                return cast(role(2001L, "commonuser", "普通用户"));
            }
            return null;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("select id from sys_config")) {
                throw new EmptyResultDataAccessException(1);
            }
            if (sql.contains("select id from sys_role")) {
                return requiredType.cast(2001L);
            }
            if (sql.contains("select last_insert_id()")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(2001L);
            }
            if (sql.contains("select count(1) from sys_role")) {
                roleListCountQueries += 1;
                return requiredType.cast(2L);
            }
            if (sql.contains("from sys_role_permission")) {
                return requiredType.cast(3L);
            }
            if (sql.contains("from sys_user_role")) {
                return requiredType.cast(7L);
            }
            return null;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("from sys_config")) {
                return List.of(Map.of("configKey", "auth.default-registration-role-code", "configValue", "commonuser"));
            }
            return List.of();
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            if (sql.contains("from sys_role_permission") && String.class.equals(elementType)) {
                return castList(List.of("system:user:view", "system:role:view"));
            }
            return List.of();
        }

        @SuppressWarnings("unchecked")
        private <T> T cast(Object value) {
            return (T) value;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> castList(List<?> values) {
            return (List<T>) values;
        }
    }

    private static final class RecordingOperationAuditService extends OperationAuditService {
        private final RecordingJdbcTemplate jdbcTemplate;

        private RecordingOperationAuditService(RecordingJdbcTemplate jdbcTemplate) {
            super(null);
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void log(Long tenantId, Long userId, String username, String moduleName, String actionName, String operationType, String resultStatus, String detailMessage) {
            jdbcTemplate.auditLogged = true;
        }
    }

    private static final class RecordingDomainEventPublisher implements DomainEventPublisher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }
    }
}
