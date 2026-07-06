package com.lumira.team.infrastructure.persistence;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.team.dto.TeamDTO;
import com.lumira.team.repository.TeamRepository;
import com.lumira.team.vo.TeamVO;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JdbcTeamRepository implements TeamRepository {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MyBatisQueryOperations jdbcTemplate;

    public JdbcTeamRepository(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String nextTeamCode() {
        int batchSize = 5;
        List<String> candidates = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i += 1) {
            byte[] bytes = new byte[6];
            SECURE_RANDOM.nextBytes(bytes);
            candidates.add("T" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT));
        }
        String placeholders = candidates.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        List<String> existing = jdbcTemplate.queryForList(
                "select team_code from team where team_code in (" + placeholders + ") and deleted = 0",
                String.class,
                candidates.toArray()
        );
        return candidates.stream()
                .filter(candidate -> !existing.contains(candidate))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR,
                        "Unable to allocate team code", "Unable to allocate team code"));
    }

    @Override
    public Long createTeam(String teamCode, Long ownerUserId, String ownerUserUuid, TeamDTO.TeamCreateRequest request) {
        int inserted = jdbcTemplate.update(
                """
                        insert into team (
                            team_code, team_name, team_type, avatar_url, description,
                            visibility, join_mode, owner_user_id, owner_user_uuid, member_count, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'ACTIVE', ?, ?, ?, ?, 0)
                        """,
                teamCode,
                request.getTeamName(),
                request.getTeamType(),
                request.getAvatarUrl(),
                request.getDescription(),
                request.getVisibility(),
                request.getJoinMode(),
                ownerUserId,
                requireUserUuid(ownerUserUuid),
                ownerUserId,
                requireUserUuid(ownerUserUuid),
                ownerUserId,
                requireUserUuid(ownerUserUuid)
        );
        if (inserted != 1) {
            throw new IllegalStateException("Team changed, please retry");
        }
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public List<TeamVO.Team> listMyTeams(Long userId, String userUuid) {
        return jdbcTemplate.query(
                teamSelect("""
                        join team_member m on m.team_id = t.id
                         and m.user_id = ?
                         and m.user_uuid = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.deleted = 0
                          and t.status = 'ACTIVE'
                        order by t.updated_at desc, t.id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                userId,
                userUuid
        );
    }

    @Override
    public List<TeamVO.Team> listTeamsForAdmin(Long userId, String userUuid) {
        return jdbcTemplate.query(
                teamSelect("""
                        left join team_member m on m.team_id = t.id
                         and m.user_id = ?
                         and m.user_uuid = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.deleted = 0
                        order by t.updated_at desc, t.id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                userId,
                userUuid
        );
    }

    @Override
    public TeamVO.Team findTeam(Long teamId, Long currentUserId, String currentUserUuid) {
        List<TeamVO.Team> teams = jdbcTemplate.query(
                teamSelect("""
                        left join team_member m on m.team_id = t.id
                         and m.user_id = ?
                         and m.user_uuid = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.id = ?
                          and t.deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                currentUserId,
                currentUserUuid,
                teamId
        );
        return teams.isEmpty() ? null : teams.get(0);
    }

    @Override
    public int updateTeamProfile(Long teamId, TeamVO.Team expectedTeam, Long updatedBy, String updatedByUuid, TeamDTO.TeamUpdateRequest request) {
        return jdbcTemplate.update(
                """
                        update team
                        set team_name = ?,
                            team_type = ?,
                            avatar_url = ?,
                            description = ?,
                            visibility = ?,
                            join_mode = ?,
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and deleted = 0
                          and status = ?
                        """,
                request.getTeamName(),
                request.getTeamType(),
                request.getAvatarUrl(),
                request.getDescription(),
                request.getVisibility(),
                request.getJoinMode(),
                updatedBy,
                requireUserUuid(updatedByUuid),
                LocalDateTime.now(),
                teamId,
                expectedTeam == null ? null : expectedTeam.getOwnerUserId(),
                expectedTeam == null ? null : requireUserUuid(expectedTeam.getOwnerUserUuid()),
                expectedTeam == null ? null : expectedTeam.getStatus()
        );
    }

    @Override
    public int softDeleteTeam(Long teamId, TeamVO.Team expectedTeam, Long updatedBy, String updatedByUuid) {
        return jdbcTemplate.update(
                """
                        update team
                        set deleted = 1, status = 'DELETED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and deleted = 0
                          and status = ?
                        """,
                updatedBy,
                requireUserUuid(updatedByUuid),
                LocalDateTime.now(),
                teamId,
                expectedTeam == null ? null : expectedTeam.getOwnerUserId(),
                expectedTeam == null ? null : requireUserUuid(expectedTeam.getOwnerUserUuid()),
                expectedTeam == null ? null : expectedTeam.getStatus()
        );
    }

    @Override
    public int transferOwner(Long teamId, Long currentOwnerUserId, String currentOwnerUserUuid, Long newOwnerUserId, String newOwnerUserUuid, Long updatedBy, String updatedByUuid) {
        return jdbcTemplate.update(
                """
                        update team
                        set owner_user_id = ?, owner_user_uuid = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and deleted = 0
                          and status = 'ACTIVE'
                        """,
                newOwnerUserId,
                requireUserUuid(newOwnerUserUuid),
                updatedBy,
                requireUserUuid(updatedByUuid),
                LocalDateTime.now(),
                teamId,
                currentOwnerUserId,
                requireUserUuid(currentOwnerUserUuid)
        );
    }

    @Override
    public Set<String> loadEnabledDictValues(String dictCode) {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    """
                            select i.item_value
                            from sys_dict_type t
                            join sys_dict_item i
                              on i.dict_type_id = t.id
                             and i.deleted = 0
                            where t.dict_code = ?
                              and t.deleted = 0
                              and t.status = 'ENABLED'
                              and i.status = 'ENABLED'
                            order by i.sort_no asc, i.id asc
                    """,
                    String.class,
                    dictCode
            );
            Set<String> normalizedValues = new LinkedHashSet<>();
            for (String itemValue : values) {
                if (StringUtils.hasText(itemValue)) {
                    normalizedValues.add(itemValue.trim().toUpperCase(Locale.ROOT));
                }
            }
            return normalizedValues;
        } catch (RuntimeException exception) {
            return Set.of();
        }
    }

    private String teamSelect(String tail) {
        return """
                select t.id, t.team_code as teamCode, t.team_name as teamName,
                       t.team_type as teamType, t.avatar_url as avatarUrl, t.description,
                       t.visibility, t.join_mode as joinMode, t.owner_user_id as ownerUserId, t.owner_user_uuid as ownerUserUuid,
                       t.member_count as memberCount, t.status, m.role as myRole,
                       t.created_at as createdAt, t.updated_at as updatedAt
                from team t
                """ + tail;
    }

    private String requireUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User uuid is required", "User uuid is required");
        }
        return userUuid.trim();
    }
}
