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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class JdbcTeamRepository implements TeamRepository {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MyBatisQueryOperations jdbcTemplate;

    public JdbcTeamRepository(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String nextTeamCode(Long tenantId) {
        for (int i = 0; i < 10; i += 1) {
            byte[] bytes = new byte[6];
            SECURE_RANDOM.nextBytes(bytes);
            String code = "T" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
            if (!jdbcTemplate.exists("select 1 from team where tenant_id = ? and team_code = ? and deleted = 0 limit 1", tenantId, code)) {
                return code;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Unable to allocate team code", "Unable to allocate team code");
    }

    @Override
    public Long createTeam(Long tenantId, String teamCode, Long ownerUserId, TeamDTO.TeamCreateRequest request) {
        jdbcTemplate.update(
                """
                        insert into team (
                            tenant_id, team_code, team_name, team_type, avatar_url, description,
                            visibility, join_mode, owner_user_id, member_count, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 'ACTIVE', ?, ?, 0)
                        """,
                tenantId,
                teamCode,
                request.getTeamName(),
                request.getTeamType(),
                request.getAvatarUrl(),
                request.getDescription(),
                request.getVisibility(),
                request.getJoinMode(),
                ownerUserId,
                ownerUserId,
                ownerUserId
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public List<TeamVO.Team> listMyTeams(Long tenantId, Long userId) {
        return jdbcTemplate.query(
                teamSelect("""
                        join team_member m on m.team_id = t.id
                         and m.tenant_id = t.tenant_id
                         and m.user_id = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.tenant_id = ?
                          and t.deleted = 0
                          and t.status = 'ACTIVE'
                        order by t.updated_at desc, t.id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                userId,
                tenantId
        );
    }

    @Override
    public List<TeamVO.Team> listTeamsForAdmin(Long tenantId, Long userId) {
        return jdbcTemplate.query(
                teamSelect("""
                        left join team_member m on m.team_id = t.id
                         and m.tenant_id = t.tenant_id
                         and m.user_id = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.tenant_id = ?
                          and t.deleted = 0
                        order by t.updated_at desc, t.id desc
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                userId,
                tenantId
        );
    }

    @Override
    public TeamVO.Team findTeam(Long tenantId, Long teamId, Long currentUserId) {
        List<TeamVO.Team> teams = jdbcTemplate.query(
                teamSelect("""
                        left join team_member m on m.team_id = t.id
                         and m.tenant_id = t.tenant_id
                         and m.user_id = ?
                         and m.status = 'ACTIVE'
                         and m.deleted = 0
                        where t.tenant_id = ?
                          and t.id = ?
                          and t.deleted = 0
                        limit 1
                        """),
                new BeanPropertyRowMapper<>(TeamVO.Team.class),
                currentUserId,
                tenantId,
                teamId
        );
        return teams.isEmpty() ? null : teams.get(0);
    }

    @Override
    public int updateTeamProfile(Long tenantId, Long teamId, Long updatedBy, TeamDTO.TeamUpdateRequest request) {
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
                            updated_at = ?
                        where tenant_id = ?
                          and id = ?
                          and deleted = 0
                          and status = 'ACTIVE'
                        """,
                request.getTeamName(),
                request.getTeamType(),
                request.getAvatarUrl(),
                request.getDescription(),
                request.getVisibility(),
                request.getJoinMode(),
                updatedBy,
                LocalDateTime.now(),
                tenantId,
                teamId
        );
    }

    @Override
    public int softDeleteTeam(Long tenantId, Long teamId, Long updatedBy) {
        return jdbcTemplate.update(
                "update team set deleted = 1, status = 'DELETED', updated_by = ?, updated_at = ? where tenant_id = ? and id = ? and deleted = 0",
                updatedBy,
                LocalDateTime.now(),
                tenantId,
                teamId
        );
    }

    @Override
    public int transferOwner(Long tenantId, Long teamId, Long newOwnerUserId, Long updatedBy) {
        return jdbcTemplate.update(
                "update team set owner_user_id = ?, updated_by = ?, updated_at = ? where tenant_id = ? and id = ? and deleted = 0",
                newOwnerUserId,
                updatedBy,
                LocalDateTime.now(),
                tenantId,
                teamId
        );
    }

    @Override
    public Set<String> loadEnabledDictValues(Long tenantId, String dictCode) {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    """
                            select i.item_value
                            from sys_dict_type t
                            join sys_dict_item i
                              on i.tenant_id = t.tenant_id
                             and i.dict_type_id = t.id
                             and i.deleted = 0
                            where t.tenant_id = ?
                              and t.dict_code = ?
                              and t.deleted = 0
                              and t.status = 'ENABLED'
                              and i.status = 'ENABLED'
                            order by i.sort_no asc, i.id asc
                            """,
                    String.class,
                    tenantId,
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
                select t.id, t.tenant_id as tenantId, t.team_code as teamCode, t.team_name as teamName,
                       t.team_type as teamType, t.avatar_url as avatarUrl, t.description,
                       t.visibility, t.join_mode as joinMode, t.owner_user_id as ownerUserId,
                       t.member_count as memberCount, t.status, m.role as myRole,
                       t.created_at as createdAt, t.updated_at as updatedAt
                from team t
                """ + tail;
    }
}
