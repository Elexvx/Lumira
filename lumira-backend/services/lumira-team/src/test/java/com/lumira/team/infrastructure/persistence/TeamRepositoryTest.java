package com.lumira.team.infrastructure.persistence;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRepositoryTest {
    @Test
    void jdbcTeamRepositoryCreatesTeamAndReturnsGeneratedId() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        request.setTeamType("GENERAL");
        request.setVisibility("PRIVATE");
        request.setJoinMode("INVITE_ONLY");

        Long id = repository.createTeam(1001L, "T001", 3001L, request);

        assertThat(id).isEqualTo(2001L);
        assertThat(queries.lastWriteSql).contains("insert into team");
        assertThat(queries.lastWriteArgs).contains(1001L, "T001", 3001L);
    }

    @Test
    void jdbcTeamMemberRepositoryRefreshesMemberCount() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        repository.refreshMemberCount(1001L, 2001L);

        assertThat(queries.lastWriteSql).contains("set member_count");
        assertThat(queries.lastWriteArgs).containsExactly(1001L, 2001L, 1001L, 2001L);
    }

    @Test
    void jdbcTeamInviteRepositoryConsumesQuotaWithCurrentInviteGuards() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamInviteRepository repository = new JdbcTeamInviteRepository(queries);
        TeamVO.Invite invite = new TeamVO.Invite();
        invite.setTenantId(1001L);
        invite.setId(5001L);

        assertThat(repository.consumeInviteQuota(invite)).isTrue();

        assertThat(queries.lastWriteSql).contains("used_count = used_count + 1");
        assertThat(queries.lastWriteArgs).contains(1001L, 5001L);
    }

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private String lastWriteSql;
        private List<Object> lastWriteArgs = List.of();

        @Override
        public int update(String sql, Object... args) {
            lastWriteSql = sql;
            lastWriteArgs = new ArrayList<>(Arrays.asList(args));
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                return requiredType.cast(2001L);
            }
            return null;
        }
    }
}
