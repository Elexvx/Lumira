package com.lumira.team.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamMemberRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class JdbcTeamMemberRepository implements TeamMemberRepository {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MyBatisQueryOperations jdbcTemplate;

    public JdbcTeamMemberRepository(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void addOwner(Long teamId, Long userId, String userUuid) {
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                """
                        insert into team_member (team_id, user_id, user_uuid, role, member_name, member_source, status, joined_at, created_at, updated_at, deleted)
                        values (?, ?, ?, 'OWNER', 'Owner', 'REGISTERED', 'ACTIVE', ?, ?, ?, 0)
                        """,
                teamId,
                userId,
                userUuid,
                now,
                now,
                now
        );
        requireSingleWrite(inserted, "Team owner membership changed, please retry");
    }

    @Override
    public List<TeamVO.Member> listMembers(Long teamId) {
        return jdbcTemplate.query(
                memberSelect("""
                        where team_id = ?
                          and deleted = 0
                        order by field(role, 'OWNER', 'ADMIN', 'MANAGER', 'MEMBER'), id asc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                teamId
        );
    }

    @Override
    public TeamVO.Member findMemberById(Long teamId, Long memberId) {
        List<TeamVO.Member> members = jdbcTemplate.query(
                memberSelect("""
                        where team_id = ?
                          and id = ?
                          and deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Member.class),
                teamId,
                memberId
        );
        return members.isEmpty() ? null : members.get(0);
    }

    @Override
    public boolean updateMemberRole(Long teamId, TeamVO.Member expectedMember, String role) {
        requireExpectedMember(expectedMember);
        int updated = jdbcTemplate.update(
                """
                        update team_member
                        set role = ?, updated_at = ?
                        where team_id = ?
                          and id = ?
                          and status = ?
                          and role = ?
                          and deleted = 0
                          and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
                        """,
                role,
                LocalDateTime.now(),
                teamId,
                expectedMember.getId(),
                expectedMember.getStatus(),
                expectedMember.getRole(),
                expectedMember.getUserId(),
                expectedMember.getUserId(),
                expectedMember.getUserUuid()
        );
        return updated > 0;
    }

    @Override
    public boolean removeMember(Long teamId, TeamVO.Member expectedMember) {
        requireExpectedMember(expectedMember);
        int updated = jdbcTemplate.update(
                """
                        update team_member
                        set status = 'REMOVED', deleted = 1, updated_at = ?
                        where team_id = ?
                          and id = ?
                          and status = ?
                          and role = ?
                          and deleted = 0
                          and ((user_id is null and ? is null and user_uuid is null) or (user_id = ? and user_uuid = ?))
                        """,
                LocalDateTime.now(),
                teamId,
                expectedMember.getId(),
                expectedMember.getStatus(),
                expectedMember.getRole(),
                expectedMember.getUserId(),
                expectedMember.getUserId(),
                expectedMember.getUserUuid()
        );
        return updated > 0;
    }

    @Override
    public boolean transferOwner(
            Long teamId,
            Long previousOwnerUserId,
            String previousOwnerUserUuid,
            String previousOwnerRole,
            Long newOwnerMemberId,
            Long newOwnerUserId,
            String newOwnerUserUuid
    ) {
        LocalDateTime now = LocalDateTime.now();
        int demoted = jdbcTemplate.update(
                "update team_member set role = ?, updated_at = ? where team_id = ? and user_id = ? and user_uuid = ? and status = 'ACTIVE' and deleted = 0",
                previousOwnerRole,
                now,
                teamId,
                previousOwnerUserId,
                previousOwnerUserUuid
        );
        if (demoted != 1) {
            return false;
        }
        int promoted = jdbcTemplate.update(
                """
                        update team_member
                        set role = 'OWNER', updated_at = ?
                        where team_id = ?
                          and id = ?
                          and user_id = ?
                          and user_uuid = ?
                          and status = 'ACTIVE'
                          and deleted = 0
                        """,
                now,
                teamId,
                newOwnerMemberId,
                newOwnerUserId,
                newOwnerUserUuid
        );
        return promoted == 1;
    }

    @Override
    public void ensureDirectMember(Long teamId, Long userId, String userUuid, Long invitedBy, String invitedByUuid, String role) {
        try {
            int updated = jdbcTemplate.update(
                    """
                            update team_member
                            set user_uuid = ?, status = 'ACTIVE', role = ?, invited_by = ?, invited_by_uuid = ?, joined_at = ?, updated_at = ?, deleted = 0
                            where team_id = ?
                              and user_id = ?
                              and user_uuid = ?
                              and deleted = 1
                              and exists (
                                  select 1
                                  from team t
                                  where t.id = team_member.team_id
                                    and t.status = 'ACTIVE'
                                    and t.deleted = 0
                              )
                            """,
                    userUuid,
                    role,
                    invitedBy,
                    invitedByUuid,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    teamId,
                    userId,
                    userUuid
            );
            if (updated == 0) {
                int inserted = jdbcTemplate.update(
                        """
                                insert into team_member (team_id, user_id, user_uuid, role, member_source, status, invited_by, invited_by_uuid, joined_at, created_at, updated_at, deleted)
                                select ?, ?, ?, ?, 'REGISTERED', 'ACTIVE', ?, ?, ?, ?, ?, 0
                                from team t
                                where t.id = ?
                                  and t.status = 'ACTIVE'
                                  and t.deleted = 0
                                """,
                        teamId,
                        userId,
                        userUuid,
                        role,
                        invitedBy,
                        invitedByUuid,
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        teamId
                );
                requireSingleWrite(inserted, "Team membership changed, please retry");
            }
        } catch (DuplicateKeyException exception) {
            // Existing active member is an idempotent success path.
        }
    }

    @Override
    public Long addDraftMember(Long teamId, TeamDTO.DraftMemberRequest request) {
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                """
                        insert into team_member (
                            team_id, user_id, role, member_alias, member_name,
                            employee_no, department_name, remark, extra_values_json, member_source, status,
                            joined_at, created_at, updated_at, deleted
                        ) values (?, null, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', 'ACTIVE', ?, ?, ?, 0)
                        """,
                teamId,
                request.getRole(),
                request.getMemberName(),
                request.getMemberName(),
                request.getEmployeeNo(),
                request.getDepartmentName(),
                request.getRemark(),
                serializeExtraValues(request.getExtraValues()),
                now,
                now,
                now
        );
        requireSingleWrite(inserted, "Team draft member changed, please retry");
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public void refreshMemberCount(Long teamId, TeamVO.Team expectedTeam) {
        requireExpectedTeam(expectedTeam);
        jdbcTemplate.update(
                """
                        update team
                        set member_count = (
                            select count(1)
                            from team_member
                            where team_id = ?
                              and status = 'ACTIVE'
                              and deleted = 0
                        )
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and status = ?
                          and deleted = 0
                        """,
                teamId,
                teamId,
                expectedTeam.getOwnerUserId(),
                requireUserUuid(expectedTeam.getOwnerUserUuid()),
                expectedTeam.getStatus()
        );
    }

    @Override
    public void removeMembersByTeam(Long teamId, TeamVO.Team expectedTeam) {
        requireExpectedTeam(expectedTeam);
        jdbcTemplate.update(
                """
                        update team_member
                        set deleted = 1, status = 'REMOVED', updated_at = ?
                        where team_id = ?
                          and deleted = 0
                          and exists (
                              select 1
                              from team t
                              where t.id = team_member.team_id
                                and t.owner_user_id = ?
                                and t.owner_user_uuid = ?
                                and t.status = 'DELETED'
                                and t.deleted = 1
                          )
                        """,
                LocalDateTime.now(),
                teamId,
                expectedTeam.getOwnerUserId(),
                requireUserUuid(expectedTeam.getOwnerUserUuid())
        );
    }

    private String memberSelect(String tail) {
        return """
                select id, team_id as teamId, user_id as userId, user_uuid as userUuid, role,
                       member_alias as memberAlias, member_name as memberName,
                       employee_no as employeeNo, department_name as departmentName,
                       extra_values_json as extraValuesJson,
                       remark, member_source as memberSource, status, invited_by as invitedBy, invited_by_uuid as invitedByUuid,
                       joined_at as joinedAt, created_at as createdAt
                from team_member
                """ + tail;
    }

    private String serializeExtraValues(java.util.Map<String, String> extraValues) {
        if (extraValues == null || extraValues.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(extraValues);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private void requireExpectedMember(TeamVO.Member member) {
        if (member == null || member.getId() == null || member.getId() <= 0) {
            throw new IllegalArgumentException("Team member id is required");
        }
        if (member.getStatus() == null || member.getStatus().isBlank()) {
            throw new IllegalArgumentException("Team member status is required");
        }
        if (member.getRole() == null || member.getRole().isBlank()) {
            throw new IllegalArgumentException("Team member role is required");
        }
        if (member.getUserId() != null && (member.getUserUuid() == null || member.getUserUuid().isBlank())) {
            throw new IllegalArgumentException("Team member user uuid is required");
        }
    }

    private void requireExpectedTeam(TeamVO.Team team) {
        if (team == null || team.getId() == null || team.getId() <= 0) {
            throw new IllegalArgumentException("Team id is required");
        }
        if (team.getOwnerUserId() == null || team.getOwnerUserId() <= 0 || team.getOwnerUserUuid() == null || team.getOwnerUserUuid().isBlank()) {
            throw new IllegalArgumentException("Team owner identity is required");
        }
        if (team.getStatus() == null || team.getStatus().isBlank()) {
            throw new IllegalArgumentException("Team status is required");
        }
    }

    private String requireUserUuid(String userUuid) {
        if (userUuid == null || userUuid.isBlank()) {
            throw new IllegalArgumentException("User uuid is required");
        }
        return userUuid.trim();
    }

    private void requireSingleWrite(int updated, String message) {
        if (updated != 1) {
            throw new IllegalStateException(message);
        }
    }
}
