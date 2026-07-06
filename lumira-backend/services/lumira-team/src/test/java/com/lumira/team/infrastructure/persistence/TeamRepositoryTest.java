package com.lumira.team.infrastructure.persistence;

import com.lumira.team.dto.TeamDTO;
import com.lumira.team.vo.TeamVO;
import com.lumira.common.exception.BizException;
import com.lumira.team.infrastructure.persistence.RowMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        Long id = repository.createTeam("T001", 3001L, "user-uuid-3001", request);

        assertThat(id).isEqualTo(2001L);
        assertThat(queries.lastWriteSql).contains("insert into team");
        assertThat(queries.lastWriteSql).contains("created_by_uuid", "updated_by_uuid");
        assertNoScopeColumn(queries.lastWriteSql);
        assertThat(queries.lastWriteArgs).contains("T001", 3001L, "user-uuid-3001");
    }

    @Test
    void jdbcTeamRepositoryRejectsCreateTeamInsertMissBeforeGeneratedId() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);
        TeamDTO.TeamCreateRequest request = new TeamDTO.TeamCreateRequest();
        request.setTeamName("Core Team");
        request.setTeamType("GENERAL");
        request.setVisibility("PRIVATE");
        request.setJoinMode("INVITE_ONLY");

        assertThatThrownBy(() -> repository.createTeam("T001", 3001L, "user-uuid-3001", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team changed");

        assertThat(queries.lastWriteSql).contains("insert into team");
        assertThat(queries.lastInsertIdQueries).isZero();
    }

    @Test
    void jdbcTeamRepositoryUpdatesTeamWithTrustedUpdaterUuid() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);
        TeamDTO.TeamUpdateRequest request = new TeamDTO.TeamUpdateRequest();
        request.setTeamName("Core Team");
        request.setTeamType("GENERAL");
        request.setVisibility("PRIVATE");
        request.setJoinMode("INVITE_ONLY");

        repository.updateTeamProfile(2001L, teamSnapshot(), 3001L, "user-uuid-3001", request);

        assertThat(queries.lastWriteSql).contains("updated_by_uuid");
        assertThat(queries.lastWriteSql).contains("and owner_user_id = ?", "and owner_user_uuid = ?", "and status = ?");
        assertThat(queries.lastWriteArgs).contains(3001L, "user-uuid-3001", 2001L);
    }

    @Test
    void jdbcTeamRepositoryDeletesOnlyActiveTeamWithTrustedUpdaterUuid() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);

        assertThat(repository.softDeleteTeam(2001L, teamSnapshot(), 3001L, "user-uuid-3001")).isEqualTo(1);

        assertThat(queries.lastWriteSql)
                .contains("updated_by_uuid")
                .contains("and owner_user_id = ?")
                .contains("and owner_user_uuid = ?")
                .contains("and status = ?");
        assertThat(queries.lastWriteArgs).contains(3001L, "user-uuid-3001", 2001L);
    }

    private static TeamVO.Team teamSnapshot() {
        TeamVO.Team team = new TeamVO.Team();
        team.setId(2001L);
        team.setOwnerUserId(3001L);
        team.setOwnerUserUuid("user-uuid-3001");
        team.setStatus("ACTIVE");
        return team;
    }

    @Test
    void jdbcTeamRepositoryTransfersOwnerOnlyForActiveTeamWithBothUserUuids() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);

        assertThat(repository.transferOwner(2001L, 3001L, "user-uuid-3001", 3002L, "user-uuid-3002", 3001L, "user-uuid-3001")).isEqualTo(1);

        assertThat(queries.lastWriteSql)
                .contains("owner_user_uuid = ?")
                .contains("updated_by_uuid = ?")
                .contains("and owner_user_id = ?")
                .contains("and owner_user_uuid = ?")
                .contains("status = 'ACTIVE'");
        assertThat(queries.lastWriteArgs).contains(3002L, "user-uuid-3002", 3001L, "user-uuid-3001", 2001L);
    }

    @Test
    void jdbcTeamMemberRepositoryStopsOwnerTransferWhenPreviousOwnerBoundaryMisses() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        boolean transferred = repository.transferOwner(2001L, 3001L, "user-uuid-3001", "MEMBER", 2L, 3002L, "user-uuid-3002");

        assertThat(transferred).isFalse();
        assertThat(queries.updateCallCount).isEqualTo(1);
        assertThat(queries.lastWriteSql).contains("user_uuid = ?");
    }

    @Test
    void jdbcTeamMemberRepositoryRefreshesMemberCount() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        repository.refreshMemberCount(2001L, team());

        assertThat(queries.lastWriteSql).contains("set member_count");
        assertThat(queries.lastWriteSql).contains("owner_user_id = ?");
        assertThat(queries.lastWriteSql).contains("owner_user_uuid = ?");
        assertThat(queries.lastWriteSql).contains("status = ?");
        assertThat(queries.lastWriteSql).contains("deleted = 0");
        assertNoScopeColumn(queries.lastWriteSql);
        assertThat(queries.lastWriteArgs).containsExactly(2001L, 2001L, 3001L, "user-uuid-3001", "ACTIVE");
    }

    @Test
    void jdbcTeamMemberRepositoryRemovesMembersOnlyAfterExpectedTeamDeleted() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        repository.removeMembersByTeam(2001L, team());

        assertThat(queries.lastWriteSql).contains("update team_member");
        assertThat(queries.lastWriteSql).contains("exists (");
        assertThat(queries.lastWriteSql).contains("owner_user_id = ?");
        assertThat(queries.lastWriteSql).contains("owner_user_uuid = ?");
        assertThat(queries.lastWriteSql).contains("t.status = 'DELETED'");
        assertThat(queries.lastWriteSql).contains("t.deleted = 1");
        assertThat(queries.lastWriteArgs).contains(2001L, 3001L, "user-uuid-3001");
    }

    @Test
    void jdbcTeamInviteRepositoryDisablesTeamInvitesOnlyAfterExpectedTeamDeleted() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamInviteRepository repository = new JdbcTeamInviteRepository(queries);

        repository.disableInvitesByTeam(2001L, team(), 3001L, "user-uuid-3001");

        assertThat(queries.lastWriteSql).contains("update team_invite");
        assertThat(queries.lastWriteSql).contains("exists (");
        assertThat(queries.lastWriteSql).contains("owner_user_id = ?");
        assertThat(queries.lastWriteSql).contains("owner_user_uuid = ?");
        assertThat(queries.lastWriteSql).contains("t.status = 'DELETED'");
        assertThat(queries.lastWriteSql).contains("t.deleted = 1");
        assertThat(queries.lastWriteArgs).contains(3001L, "user-uuid-3001", 2001L, 3001L, "user-uuid-3001");
    }

    @Test
    void jdbcTeamJoinRequestRepositoryClosesRequestsOnlyAfterExpectedTeamDeleted() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamJoinRequestRepository repository = new JdbcTeamJoinRequestRepository(queries);

        repository.closeRequestsByTeam(2001L, team());

        assertThat(queries.lastWriteSql).contains("update team_join_request");
        assertThat(queries.lastWriteSql).contains("exists (");
        assertThat(queries.lastWriteSql).contains("owner_user_id = ?");
        assertThat(queries.lastWriteSql).contains("owner_user_uuid = ?");
        assertThat(queries.lastWriteSql).contains("t.status = 'DELETED'");
        assertThat(queries.lastWriteSql).contains("t.deleted = 1");
        assertThat(queries.lastWriteArgs).contains(2001L, 3001L, "user-uuid-3001");
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
    void jdbcTeamMemberRepositoryRejectsOwnerInsertMiss() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        assertThatThrownBy(() -> repository.addOwner(2001L, 3001L, "user-uuid-3001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team owner membership changed");
    }

    @Test
    void jdbcTeamMemberRepositoryRejectsDraftMemberInsertMissBeforeGeneratedId() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);
        TeamDTO.DraftMemberRequest request = new TeamDTO.DraftMemberRequest();
        request.setMemberName("Alice");
        request.setRole("MEMBER");

        assertThatThrownBy(() -> repository.addDraftMember(2001L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team draft member changed");
    }

    @Test
    void jdbcTeamMemberRepositoryUpdatesRoleWithExpectedMemberIdentity() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);
        TeamVO.Member member = member(9L, 3009L, "user-uuid-3009", "MEMBER");

        assertThat(repository.updateMemberRole(2001L, member, "MANAGER")).isTrue();

        assertThat(queries.lastWriteSql)
                .contains("status = ?")
                .contains("role = ?")
                .contains("user_id = ? and user_uuid = ?");
        assertThat(queries.lastWriteArgs).contains(2001L, 9L, "ACTIVE", "MEMBER", 3009L, "user-uuid-3009");
    }

    @Test
    void jdbcTeamMemberRepositoryRemovesMemberWithExpectedMemberIdentity() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);
        TeamVO.Member member = member(9L, 3009L, "user-uuid-3009", "MEMBER");

        assertThat(repository.removeMember(2001L, member)).isTrue();

        assertThat(queries.lastWriteSql)
                .contains("status = ?")
                .contains("role = ?")
                .contains("user_id = ? and user_uuid = ?");
        assertThat(queries.lastWriteArgs).contains(2001L, 9L, "ACTIVE", "MEMBER", 3009L, "user-uuid-3009");
    }

    @Test
    void jdbcTeamInviteRepositoryConsumesQuotaWithCurrentInviteGuards() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamInviteRepository repository = new JdbcTeamInviteRepository(queries);
        TeamVO.Invite invite = new TeamVO.Invite();
        invite.setId(5001L);
        invite.setTeamId(2001L);
        invite.setInviteCode("JOIN2026");
        invite.setInviteType("LINK");
        invite.setRoleOnJoin("MEMBER");
        invite.setNeedApproval(false);

        assertThat(repository.consumeInviteQuota(invite, 3001L, "user-uuid-3001")).isTrue();

        assertThat(queries.lastWriteSql).contains("used_count = used_count + 1");
        assertThat(queries.lastWriteSql).contains("updated_by_uuid");
        assertThat(queries.lastWriteSql).contains("team_id = ?");
        assertThat(queries.lastWriteSql).contains("invite_code = ?");
        assertThat(queries.lastWriteSql).contains("invite_type = ?");
        assertThat(queries.lastWriteSql).contains("role_on_join = ?");
        assertThat(queries.lastWriteSql).contains("need_approval = ?");
        assertThat(queries.lastWriteArgs).contains(3001L, "user-uuid-3001", 5001L, 2001L, "JOIN2026", "LINK", "MEMBER", 0);
    }

    @Test
    void jdbcTeamMemberRepositoryRestoresDirectMemberOnlyForActiveTeam() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        repository.ensureDirectMember(2001L, 3002L, "user-uuid-3002", null, null, "MEMBER");

        assertThat(queries.lastWriteSql)
                .contains("update team_member")
                .contains("exists (")
                .contains("from team t")
                .contains("t.status = 'ACTIVE'")
                .contains("t.deleted = 0");
        assertThat(queries.lastWriteArgs).contains(2001L, 3002L, "user-uuid-3002");
    }

    @Test
    void jdbcTeamMemberRepositoryInsertsDirectMemberOnlyForActiveTeam() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        repository.ensureDirectMember(2001L, 3002L, "user-uuid-3002", null, null, "MEMBER");

        assertThat(queries.lastWriteSql)
                .contains("insert into team_member")
                .contains("select ?, ?, ?, ?")
                .contains("from team t")
                .contains("t.status = 'ACTIVE'")
                .contains("t.deleted = 0");
        assertThat(queries.lastWriteArgs).contains(2001L, 3002L, "user-uuid-3002", "MEMBER");
    }

    @Test
    void jdbcTeamMemberRepositoryRejectsDirectMemberInsertMiss() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        queries.nextUpdateCounts.add(0);
        JdbcTeamMemberRepository repository = new JdbcTeamMemberRepository(queries);

        assertThatThrownBy(() -> repository.ensureDirectMember(2001L, 3002L, "user-uuid-3002", null, null, "MEMBER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team membership changed");
    }

    @Test
    void jdbcTeamInviteRepositoryRejectsCreateInviteInsertMissBeforeGeneratedId() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamInviteRepository repository = new JdbcTeamInviteRepository(queries);

        assertThatThrownBy(() -> repository.createInvite(
                2001L,
                "JOIN2026",
                "token-hash",
                "LINK",
                "MEMBER",
                null,
                null,
                false,
                3001L,
                "user-uuid-3001"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team invite changed");
    }

    @Test
    void jdbcTeamJoinRequestRepositoryRejectsCreatePendingInsertMissBeforeGeneratedId() {
        RecordingQueries queries = new RecordingQueries();
        queries.nextUpdateCounts.add(0);
        JdbcTeamJoinRequestRepository repository = new JdbcTeamJoinRequestRepository(queries);

        assertThatThrownBy(() -> repository.createPending(2001L, 3002L, "user-uuid-3002", 5001L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team join request changed");
    }

    @Test
    void jdbcTeamInviteRepositoryDisablesInviteWithTrustedUpdaterUuid() {
        RecordingQueries queries = new RecordingQueries();
        JdbcTeamInviteRepository repository = new JdbcTeamInviteRepository(queries);

        assertThat(repository.disableInvite(2001L, 5001L, 3001L, "user-uuid-3001")).isTrue();

        assertThat(queries.lastWriteSql).contains("updated_by_uuid");
        assertThat(queries.lastWriteSql).contains("invite_code = ?");
        assertThat(queries.lastWriteSql).contains("invite_type = ?");
        assertThat(queries.lastWriteSql).contains("role_on_join = ?");
        assertThat(queries.lastWriteSql).contains("status = ?");
        assertThat(queries.lastWriteArgs).contains(3001L, "user-uuid-3001", 2001L, 5001L, "JOIN2026", "LINK", "MEMBER", "ACTIVE");
    }

    @Test
    void jdbcTeamRepositorySelectsAvailableCodeFromBatch() {
        RecordingQueries queries = new RecordingQueries();
        queries.existingCodeStrategy = candidates -> candidates.subList(0, candidates.size() - 1);
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);

        String code = repository.nextTeamCode();

        assertThat(code).isEqualTo(queries.lastCodeCandidates.get(4));
        assertThat(queries.lastCodeLookupSql).contains("team_code in (?, ?, ?, ?, ?)");
    }

    @Test
    void jdbcTeamRepositoryThrowsWhenBatchCodesAllConflict() {
        RecordingQueries queries = new RecordingQueries();
        queries.existingCodeStrategy = List::copyOf;
        JdbcTeamRepository repository = new JdbcTeamRepository(queries);

        assertThatThrownBy(repository::nextTeamCode)
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Unable to allocate team code");
    }

    private static void assertNoScopeColumn(String sql) {
        assertThat(sql).doesNotContain("ten" + "ant_" + "id");
    }

    private static TeamVO.Member member(Long id, Long userId, String userUuid, String role) {
        TeamVO.Member member = new TeamVO.Member();
        member.setId(id);
        member.setTeamId(2001L);
        member.setUserId(userId);
        member.setUserUuid(userUuid);
        member.setRole(role);
        member.setStatus("ACTIVE");
        return member;
    }

    private static TeamVO.Team team() {
        TeamVO.Team team = new TeamVO.Team();
        team.setId(2001L);
        team.setOwnerUserId(3001L);
        team.setOwnerUserUuid("user-uuid-3001");
        team.setStatus("ACTIVE");
        return team;
    }

    private static final class RecordingQueries extends MyBatisQueryOperations {
        private String lastWriteSql;
        private List<Object> lastWriteArgs = List.of();
        private String lastCodeLookupSql;
        private List<String> lastCodeCandidates = List.of();
        private java.util.function.Function<List<String>, List<String>> existingCodeStrategy = ignored -> List.of();
        private final java.util.Queue<Integer> nextUpdateCounts = new java.util.ArrayDeque<>();
        private int updateCallCount;
        private int lastInsertIdQueries;

        @Override
        public int update(String sql, Object... args) {
            updateCallCount += 1;
            lastWriteSql = sql;
            lastWriteArgs = new ArrayList<>(Arrays.asList(args));
            if (!nextUpdateCounts.isEmpty()) {
                return nextUpdateCounts.remove();
            }
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(2001L);
            }
            return null;
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            lastCodeLookupSql = sql;
            lastCodeCandidates = Arrays.stream(args)
                    .map(String::valueOf)
                    .toList();
            return existingCodeStrategy.apply(lastCodeCandidates).stream()
                    .map(elementType::cast)
                    .toList();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from team_invite")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(java.util.Map.of(
                            "id", 5001L,
                            "teamId", 2001L,
                            "inviteCode", "JOIN2026",
                            "inviteType", "LINK",
                            "roleOnJoin", "MEMBER",
                            "needApproval", false,
                            "status", "ACTIVE"
                    )), 0));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
            return List.of();
        }
    }
}
