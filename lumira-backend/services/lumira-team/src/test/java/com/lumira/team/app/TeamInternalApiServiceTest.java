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
    void getTeamUsesTeamIdOnly() {
        RecordingQueries queries = new RecordingQueries();
        TeamInternalApiService service = new TeamInternalApiService(queries, mock(TeamPermissionService.class));

        service.getTeam(2001L);
        service.listActiveMembers(2001L);

        assertThat(queries.forbiddenScopeIds).isEmpty();
    }

    @Test
    void roleChecksUseTeamAndUserOnly() {
        RecordingQueries queries = new RecordingQueries();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        when(permission.activeRole(2001L, 3001L)).thenReturn(TeamPermissionService.ADMIN);
        TeamInternalApiService service = new TeamInternalApiService(queries, permission);

        assertThat(service.isTeamAdmin(2001L, 3001L)).isTrue();

        verify(permission).activeRole(2001L, 3001L);
    }

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private final List<Long> forbiddenScopeIds = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            recordForbiddenScopeArgs(args);
            return List.of();
        }

        private void recordForbiddenScopeArgs(Object... args) {
            if (args == null) {
                return;
            }
            for (Object arg : args) {
                if (arg instanceof Long value && (value == 1001L || value == 2002L)) {
                    forbiddenScopeIds.add(value);
                }
            }
        }
    }
}
