package com.lumira.team.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.infrastructure.persistence.MyBatisQueryOperations;
import com.lumira.team.infrastructure.persistence.RowMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamInternalApiServiceTest {
    @BeforeEach
    void authenticateInternalService() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", null, "internal", 0, false, Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(internalService, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teamReadsRequireRequesterMembership() {
        RecordingQueries queries = new RecordingQueries();
        TeamInternalApiService service = new TeamInternalApiService(queries, mock(TeamPermissionService.class), provider(userSnapshot(3001L)));

        service.getTeam(3001L, "user-uuid-3001", 2001L);
        service.listActiveMembers(3001L, "user-uuid-3001", 2001L);

        assertThat(queries.seenArgs).contains(3001L);
    }

    @Test
    void internalReadsShouldRejectInvalidIdsBeforeQuerying() {
        RecordingQueries queries = new RecordingQueries();
        TeamInternalApiService service = new TeamInternalApiService(queries, mock(TeamPermissionService.class), provider(userSnapshot(3001L)));

        assertThatThrownBy(() -> service.getTeam(0L, "user-uuid-3001", 2001L))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listActiveMembers(3001L, "user-uuid-3001", null))
                .isInstanceOf(BizException.class);

        assertThat(queries.seenArgs).isEmpty();
    }

    @Test
    void internalReadsShouldRejectMissingInternalServicePrincipalBeforeQuerying() {
        SecurityContextHolder.clearContext();
        RecordingQueries queries = new RecordingQueries();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = new TeamInternalApiService(queries, permission, provider(userSnapshot(3001L)));

        assertThatThrownBy(() -> service.getTeam(3001L, "user-uuid-3001", 2001L))
                .isInstanceOf(BizException.class);

        assertThat(queries.seenArgs).isEmpty();
        verifyNoInteractions(permission);
    }

    @Test
    void roleChecksShouldRejectMissingInternalServicePrincipalBeforePermissionLookup() {
        SecurityContextHolder.clearContext();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = new TeamInternalApiService(new RecordingQueries(), permission, provider(userSnapshot(3001L)));

        assertThatThrownBy(() -> service.isTeamAdmin(2001L, 3001L, "user-uuid-3001"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(permission);
    }

    @Test
    void roleChecksShouldRejectInvalidIdsBeforePermissionLookup() {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = new TeamInternalApiService(new RecordingQueries(), permission, provider(userSnapshot(3001L)));

        assertThatThrownBy(() -> service.isTeamAdmin(2001L, -1L, "user-uuid-3001"))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(permission);
    }

    @Test
    void roleChecksShouldRejectUserUuidMismatchBeforePermissionLookup() {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = new TeamInternalApiService(new RecordingQueries(), permission, provider(userSnapshot(3001L)));

        assertThatThrownBy(() -> service.isTeamAdmin(2001L, 3001L, "other-user-uuid"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("identity mismatch");

        verifyNoInteractions(permission);
    }

    @Test
    void roleChecksShouldRejectUserSnapshotMissingStatusBeforePermissionLookup() {
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = new TeamInternalApiService(new RecordingQueries(), permission, provider(userSnapshot(3001L, " ")));

        assertThatThrownBy(() -> service.isTeamAdmin(2001L, 3001L, "user-uuid-3001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("disabled");

        verifyNoInteractions(permission);
    }

    @Test
    void roleChecksUseTeamUserAndUuidMembership() {
        RecordingQueries queries = new RecordingQueries();
        TeamPermissionService permission = mock(TeamPermissionService.class);
        TeamInternalApiService service = new TeamInternalApiService(queries, permission, provider(userSnapshot(3001L)));

        assertThat(service.isTeamAdmin(2001L, 3001L, "user-uuid-3001")).isTrue();

        assertThat(queries.seenArgs).contains(2001L, 3001L);
        assertThat(queries.lastSql).contains("u.uuid = ?");
        assertThat(queries.lastArgs).contains("user-uuid-3001");
        verifyNoInteractions(permission);
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId) {
        return userSnapshot(userId, "ENABLED");
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, "user" + userId, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ObjectProvider<SystemInternalApi> provider(SystemUserSnapshotDTO snapshot) {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(snapshot.userId())).thenReturn(snapshot);
        return new ObjectProvider<>() {
            @Override
            public SystemInternalApi getObject(Object... args) {
                return systemInternalApi;
            }

            @Override
            public SystemInternalApi getIfAvailable() {
                return systemInternalApi;
            }

            @Override
            public SystemInternalApi getIfUnique() {
                return systemInternalApi;
            }

            @Override
            public SystemInternalApi getObject() {
                return systemInternalApi;
            }
        };
    }

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private final List<Long> seenArgs = new ArrayList<>();
        private String lastSql;
        private List<Object> lastArgs = List.of();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastSql = sql;
            lastArgs = args == null ? List.of() : List.of(args);
            recordArgs(args);
            if (sql.contains("from team_member") && sql.contains("user_id = ?")) {
                TeamMemberDTO member = new TeamMemberDTO();
                member.setRole(TeamPermissionService.ADMIN);
                return List.of((T) member);
            }
            return List.of();
        }

        private void recordArgs(Object... args) {
            if (args == null) {
                return;
            }
            for (Object arg : args) {
                if (arg instanceof Long value) {
                    seenArgs.add(value);
                }
            }
        }
    }
}
