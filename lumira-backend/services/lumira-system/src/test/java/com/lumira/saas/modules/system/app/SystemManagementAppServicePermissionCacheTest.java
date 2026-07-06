package com.lumira.saas.modules.system.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemManagementAppServicePermissionCacheTest {

    @Test
    void shouldCachePermissionCatalogGlobally() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.currentPermissionSnapshotVersion()).thenReturn("v1");
        SystemManagementAppService service = new SystemManagementAppService(
                queryOperations,
                null,
                permissionSnapshotService,
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

        List<SystemVO.PermissionVO> first = service.listPermissions(currentUser);
        List<SystemVO.PermissionVO> second = service.listPermissions(currentUser);

        assertEquals(1, queryOperations.queryCount);
        assertEquals(first.size(), second.size());
        assertEquals(first.getFirst().getPermissionKey(), second.getFirst().getPermissionKey());
        assertTrue(first.stream().anyMatch(permission -> "system:role:view".equals(permission.getPermissionKey())));
        assertEquals(1, first.size());
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
