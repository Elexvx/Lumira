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

        Long id = repository.createTeam("T001", 3001L, request);

        assertThat(id).isEqualTo(2001L);
        assertThat(queries.lastWriteSql).contains("insert into team");
        assertNoScopeColumn(queries.lastWriteSql);
        assertThat(queries.lastWriteArgs).contains("T001", 3001L);
    }

    @Test
    void jdbcTeamMemberRepositoryRefreshesMemberCount() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        repository.refreshMemberCount(2001L);

        assertThat(queries.lastWriteSql).contains("set member_count");
        assertNoScopeColumn(queries.lastWriteSql);
        assertThat(queries.lastWriteArgs).containsExactly(2001L, 2001L);
    }

    @Test
    void jdbcTeamMemberRepositoryAddsDraftMemberWithoutUserId() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);
        TeamDTO.DraftMemberRequest request = new TeamDTO.DraftMemberRequest();
        request.setMemberName("Alice");
        request.setEmployeeNo("E001");
        request.setDepartmentName("Product");
        request.setRole("MEMBER");

        Long id = repository.addDraftMember(2001L, request);

        assertThat(id).isEqualTo(2001L);
        assertThat(queries.lastWriteSql).contains("insert into team_member");
        assertNoScopeColumn(queries.lastWriteSql);
        assertThat(queries.lastWriteSql).contains("user_id");
        assertThat(queries.lastWriteSql).contains("null");
        assertThat(queries.lastWriteArgs).contains(2001L, "Alice", "E001", "Product");
    }

    @Test
    void jdbcTeamInviteRepositoryConsumesQuotaWithCurrentInviteGuards() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamInviteRepository repository = new JdbcTeamInviteRepository(queries);
        TeamVO.Invite invite = new TeamVO.Invite();
        invite.setId(5001L);

        assertThat(repository.consumeInviteQuota(invite)).isTrue();

        assertThat(queries.lastWriteSql).contains("used_count = used_count + 1");
        assertNoScopeColumn(queries.lastWriteSql);
        assertThat(queries.lastWriteArgs).contains(5001L);
    }

    private static void assertNoScopeColumn(String sql) {
        assertThat(sql).doesNotContain("ten" + "ant_" + "id");
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
