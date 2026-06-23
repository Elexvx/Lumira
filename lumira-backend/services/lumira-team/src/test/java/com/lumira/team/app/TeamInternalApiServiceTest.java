package com.lumira.team.app;

import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import com.lumira.team.infrastructure.persistence.RowMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamInternalApiServiceTest {
    @Test
    void getTeamIgnoresCallerTenantAndUsesPlatformScope() {
        RecordingQueries queries = new RecordingQueries();
        TeamInternalApiService service = new TeamInternalApiService(queries, mock(TeamPermissionService.class));

        service.getTeam(2002L, 2001L);
        service.listActiveMembers(null, 2001L);

        assertThat(queries.usedTenantIds).contains(1001L);
        assertThat(queries.usedTenantIds).doesNotContain(2002L);
    }

    @Test
    void roleChecksIgnoreCallerTenantAndUsePlatformScope() {
        RecordingQueries queries = new RecordingQueries();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeRole(1001L, 2001L, 3001L)).thenReturn(TeamPermissionService.ADMIN);
        TeamInternalApiService service = new TeamInternalApiService(queries, permission);

        assertThat(service.isTeamAdmin(2002L, 2001L, 3001L)).isTrue();

        verify(permission).activeRole(1001L, 2001L, 3001L);
    }

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private final List<Long> usedTenantIds = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            recordTenantArgs(args);
            return List.of();
        }

        private void recordTenantArgs(Object... args) {
            if (args == null) {
                return;
            }
            for (Object arg : args) {
                if (arg instanceof Long tenantId && (tenantId == 1001L || tenantId == 2002L)) {
                    usedTenantIds.add(tenantId);
                }
            }
        }
    }
}
