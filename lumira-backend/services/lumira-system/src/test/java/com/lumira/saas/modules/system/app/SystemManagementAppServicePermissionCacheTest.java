package com.lumira.saas.modules.system.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemManagementAppServicePermissionCacheTest {

    @Test
    void shouldCachePermissionCatalogPerTenant() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        SystemManagementAppService service = new SystemManagementAppService(
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
                null,
                null
        );
        CurrentUser currentUser = new CurrentUser();
        currentUser.setCurrentTenantId(1001L);

        List<SystemVO.PermissionVO> first = service.listPermissions(currentUser);
        List<SystemVO.PermissionVO> second = service.listPermissions(currentUser);

        assertEquals(1, queryOperations.queryCount);
        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(permission -> "system:role:view".equals(permission.getPermissionKey())));
        assertTrue(first.stream().anyMatch(permission -> "payment:view".equals(permission.getPermissionKey())));
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private int queryCount;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount += 1;
            try {
                return List.of(rowMapper.mapRow(new SqlRow(Map.of(
                        "permission_key", "system:role:view",
                        "permission_name", "角色查看",
                        "permission_group", "system",
                        "source_type", "CORE",
                        "plugin_code", ""
                )), 0));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
