package com.lumira.saas.modules.system.user.infrastructure;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRowCursor;
import com.lumira.saas.modules.system.user.repository.SystemUserManagementRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis/JDBC adapter for system-user management persistence. */
@Repository
public class JdbcSystemUserManagementRepository implements SystemUserManagementRepository {
    private static final long MAX_PAGE_SIZE = 100L;
    private final MyBatisQueryOperations database;

    public JdbcSystemUserManagementRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public PageResponse<SystemVO.UserVO> findUsers(UserSearch search) {
        String baseSql = """
                from sys_user u
                left join iam_user iu
                  on iu.id = u.id
                 and iu.deleted = 0
                where u.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.uid())) {
            baseSql += " and u.uuid = ?";
            params.add(search.uid().trim());
        } else if (search.userId() != null) {
            baseSql += " and u.id = ?";
            params.add(search.userId());
        }
        if (StringUtils.hasText(search.normalizedUsername())) {
            baseSql += """
                     and exists (
                         select 1 from iam_user_identity ui
                         where ui.user_id = u.id
                           and ui.identity_type = 'USERNAME'
                           and ui.identifier_normalized = ?
                           and ui.deleted = 0
                     )
                    """;
            params.add(search.normalizedUsername());
        }
        if (StringUtils.hasText(search.normalizedMobile())) {
            baseSql += """
                     and exists (
                         select 1 from iam_user_identity ui
                         where ui.user_id = u.id
                           and ui.identity_type = 'MOBILE'
                           and ui.identifier_normalized = ?
                           and ui.deleted = 0
                     )
                    """;
            params.add(search.normalizedMobile());
        }
        if (StringUtils.hasText(search.normalizedEmail())) {
            baseSql += """
                     and exists (
                         select 1 from iam_user_identity ui
                         where ui.user_id = u.id
                           and ui.identity_type = 'EMAIL'
                           and ui.identifier_normalized = ?
                           and ui.deleted = 0
                     )
                    """;
            params.add(search.normalizedEmail());
        }
        if (search.departmentFilterIds() != null) {
            if (search.departmentFilterIds().isEmpty()) {
                baseSql += " and 1 = 0";
            } else {
                baseSql += """
                         and exists (
                             select 1
                             from sys_user_department ud_filter
                             where ud_filter.user_id = u.id
                               and ud_filter.dept_id in (%s)
                               and ud_filter.deleted = 0
                          )
                         """.formatted(placeholders(search.departmentFilterIds().size()));
                params.addAll(search.departmentFilterIds());
            }
        }
        if (StringUtils.hasText(search.status())) {
            baseSql += " and u.status = ?";
            params.add(search.status());
        }
        if (StringUtils.hasText(search.source())) {
            baseSql += " and iu.source = ?";
            params.add(search.source());
        }
        if (search.registeredStart() != null) {
            baseSql += " and coalesce(iu.registered_at, u.created_at) >= ?";
            params.add(search.registeredStart());
        }
        if (search.registeredEnd() != null) {
            baseSql += " and coalesce(iu.registered_at, u.created_at) <= ?";
            params.add(search.registeredEnd());
        }
        if (search.lastLoginStart() != null) {
            baseSql += " and iu.last_login_at >= ?";
            params.add(search.lastLoginStart());
        }
        if (search.lastLoginEnd() != null) {
            baseSql += " and iu.last_login_at <= ?";
            params.add(search.lastLoginEnd());
        }
        VisibilitySql visibility = visibilitySql(search.visibility(), "u");
        baseSql += visibility.sql();
        params.addAll(visibility.params());
        boolean cursorMode = search.cursorId() != null || search.cursorCreatedAt() != null;
        if (search.cursorCreatedAt() != null && search.cursorId() != null) {
            baseSql += " and (coalesce(iu.registered_at, u.created_at) < ? or (coalesce(iu.registered_at, u.created_at) = ? and u.id < ?))";
            params.add(search.cursorCreatedAt());
            params.add(search.cursorCreatedAt());
            params.add(search.cursorId());
        } else if (search.cursorId() != null) {
            baseSql += " and u.id < ?";
            params.add(search.cursorId());
        } else if (search.cursorCreatedAt() != null) {
            baseSql += " and coalesce(iu.registered_at, u.created_at) < ?";
            params.add(search.cursorCreatedAt());
        }
        String selectSql = """
                select u.id, u.uuid as uid, u.uuid as user_uuid, iu.user_no as userNo, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName,
                       u.avatar_url as avatarUrl, u.email, u.birth_month as birthMonth, u.gender, u.region,
                       u.available_time as availableTime, u.status, iu.source,
                       coalesce(iu.registered_at, u.created_at) as registeredAt,
                       iu.last_login_at as lastLoginAt,
                       u.created_at as createdAt, u.updated_at as updatedAt
                """ + baseSql + """
                order by coalesce(iu.registered_at, u.created_at) desc, u.id desc
                """;
        return cursorMode
                ? cursorQuery(selectSql, search.pageSize(), params)
                : pageQuery(selectSql, "select count(1) " + baseSql, search.pageNo(), search.pageSize(), params);
    }

    @Override
    public boolean canAccessActiveUser(Long userId, DataVisibility visibility) {
        if (userId == null) {
            return false;
        }
        VisibilitySql visibilitySql = visibilitySql(visibility, "u");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(visibilitySql.params());
        return database.exists(
                """
                        select 1
                        from sys_user u
                        where u.deleted = 0
                          and u.id = ?
                        """ + visibilitySql.sql() + " limit 1",
                params.toArray()
        );
    }

    @Override
    public SystemVO.UserVO findActiveUser(Long userId) {
        SystemVO.UserVO user = queryOne(
                """
                        select u.id, u.uuid as uid, u.uuid as user_uuid, u.username, u.mobile, u.id_card_number as idCardNumber, u.nickname, u.real_name as realName, u.avatar_url as avatarUrl,
                               u.email, u.birth_month as birthMonth, u.gender, u.region, u.available_time as availableTime,
                               u.status, iu.user_no as userNo, iu.source,
                               coalesce(iu.registered_at, u.created_at) as registeredAt,
                               iu.last_login_at as lastLoginAt,
                               u.created_at as createdAt, u.updated_at as updatedAt
                        from sys_user u
                        left join iam_user iu on iu.id = u.id and iu.deleted = 0
                        where u.id = ? and u.deleted = 0
                        """,
                SystemVO.UserVO.class,
                userId
        );
        return user;
    }

    @Override
    public int updateStatus(Long userId, String userUuid, String status, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                "update sys_user set status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                status, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public int softDeleteUser(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_user
                        set username = concat(left(username, 32), '#deleted#', id),
                            status = 'DISABLED',
                            deleted = 1,
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where id = ? and uuid = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public void retireUserRelations(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt) {
        String[] statements = {
                "update sys_user_role set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                "update sys_user_department set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                "update sys_user_passkey_credential set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                "update sys_verification_binding set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                "update sys_verification_challenge set consumed_flag = 1, deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0",
                "update sys_user_wechat_binding set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where user_id = ? and user_uuid = ? and deleted = 0"
        };
        for (String statement : statements) {
            database.update(statement, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid);
        }
    }

    @Override
    public List<SystemVO.RoleVO> findActiveUserRoles(Long userId, String userUuid) {
        return database.query(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by r.id desc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.RoleVO.class), userId, userUuid
        );
    }

    @Override
    public UserSaveResult saveUser(UserSave command) {
        if (command.userId() == null) {
            int inserted = database.update(
                    """
                            insert into sys_user (
                                uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                                available_time, id_card_number, status,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    command.generatedUuid(), command.username(), command.passwordHash(), command.mobile(), command.nickname(),
                    command.realName(), command.avatarUrl(), command.email(), command.birthMonth(), command.gender(), command.region(),
                    command.availableTime(), command.idCardNumber(), command.status(), command.actor().userId(), command.actor().userUuid(),
                    command.actor().userId(), command.actor().userUuid()
            );
            Long userId = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
            return new UserSaveResult(inserted, userId);
        }
        int updated = database.update(
                """
                        update sys_user
                        set username = ?, mobile = ?, nickname = ?, real_name = ?, avatar_url = ?, email = ?,
                            birth_month = ?, gender = ?, region = ?, available_time = ?, id_card_number = ?, status = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and deleted = 0
                        """,
                command.username(), command.mobile(), command.nickname(), command.realName(), command.avatarUrl(), command.email(),
                command.birthMonth(), command.gender(), command.region(), command.availableTime(), command.idCardNumber(), command.status(),
                command.actor().userId(), command.actor().userUuid(), command.updatedAt(), command.userId(), command.userUuid()
        );
        return new UserSaveResult(updated, command.userId());
    }

    @Override
    public int updatePasswordHash(Long userId, String userUuid, String passwordHash, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                "update sys_user set password_hash = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and uuid = ? and deleted = 0",
                passwordHash, actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public Long findActiveUserIdByUsername(String username) {
        return nullableLong("select id from sys_user where username = ? and deleted = 0 limit 1", username);
    }

    @Override
    public Long findActiveIdentityUserId(String normalizedUsername) {
        return nullableLong(
                """
                        select user_id
                        from iam_user_identity
                        where identity_type = 'USERNAME'
                          and identifier_normalized = ?
                          and deleted = 0
                        limit 1
                        """,
                normalizedUsername
        );
    }

    @Override
    public int countActiveRoles(List<Long> roleIds) {
        return count("select count(1) from sys_role where deleted = 0 and id in (" + placeholders(roleIds.size()) + ")", roleIds);
    }

    @Override
    public int countPrivilegedRoles(List<Long> roleIds) {
        return count(
                """
                        select count(1)
                        from sys_role_permission rp
                        join sys_permission p on p.id = rp.permission_id and p.deleted = 0
                        where rp.deleted = 0
                          and rp.role_id in (%s)
                          and p.perm_code = '*'
                        """.formatted(placeholders(roleIds.size())),
                roleIds
        );
    }

    @Override
    public int countEnabledDepartments(List<Long> departmentIds) {
        return count(
                "select count(1) from sys_department where deleted = 0 and status = 'ENABLED' and id in ("
                        + placeholders(departmentIds.size()) + ")",
                departmentIds
        );
    }

    @Override
    public void retireUserRoles(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt) {
        database.update(
                """
                        update sys_user_role
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where user_id = ? and user_uuid = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public int upsertUserRole(Long userId, String userUuid, Long roleId, Actor actor) {
        return database.update(
                """
                        insert into sys_user_role (user_id, user_uuid, role_id, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                        select ?, ?, r.id, ?, ?, ?, ?, 0
                        from sys_role r
                        where r.id = ? and r.deleted = 0
                        on duplicate key update
                            deleted = case when sys_user_role.user_id = values(user_id) and sys_user_role.user_uuid = values(user_uuid) and sys_user_role.role_id = values(role_id) then 0 else sys_user_role.deleted end,
                            updated_by = case when sys_user_role.user_id = values(user_id) and sys_user_role.user_uuid = values(user_uuid) and sys_user_role.role_id = values(role_id) then values(updated_by) else sys_user_role.updated_by end,
                            updated_by_uuid = case when sys_user_role.user_id = values(user_id) and sys_user_role.user_uuid = values(user_uuid) and sys_user_role.role_id = values(role_id) then values(updated_by_uuid) else sys_user_role.updated_by_uuid end,
                            updated_at = case when sys_user_role.user_id = values(user_id) and sys_user_role.user_uuid = values(user_uuid) and sys_user_role.role_id = values(role_id) then current_timestamp else sys_user_role.updated_at end
                        """,
                userId, userUuid, actor.userId(), actor.userUuid(), actor.userId(), actor.userUuid(), roleId
        );
    }

    @Override
    public void retireUserDepartments(Long userId, String userUuid, Actor actor, LocalDateTime updatedAt) {
        database.update(
                """
                        update sys_user_department
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where user_id = ? and user_uuid = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, userId, userUuid
        );
    }

    @Override
    public int upsertUserDepartment(Long userId, String userUuid, Long departmentId, boolean primary, Actor actor) {
        return database.update(
                """
                        insert into sys_user_department (user_id, user_uuid, dept_id, primary_flag, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                        select ?, ?, d.id, ?, ?, ?, ?, ?, 0
                        from sys_department d
                        where d.id = ? and d.status = 'ENABLED' and d.deleted = 0
                        on duplicate key update
                            primary_flag = case when sys_user_department.user_id = values(user_id) and sys_user_department.user_uuid = values(user_uuid) and sys_user_department.dept_id = values(dept_id) then values(primary_flag) else sys_user_department.primary_flag end,
                            deleted = case when sys_user_department.user_id = values(user_id) and sys_user_department.user_uuid = values(user_uuid) and sys_user_department.dept_id = values(dept_id) then 0 else sys_user_department.deleted end,
                            updated_by = case when sys_user_department.user_id = values(user_id) and sys_user_department.user_uuid = values(user_uuid) and sys_user_department.dept_id = values(dept_id) then values(updated_by) else sys_user_department.updated_by end,
                            updated_by_uuid = case when sys_user_department.user_id = values(user_id) and sys_user_department.user_uuid = values(user_uuid) and sys_user_department.dept_id = values(dept_id) then values(updated_by_uuid) else sys_user_department.updated_by_uuid end,
                            updated_at = case when sys_user_department.user_id = values(user_id) and sys_user_department.user_uuid = values(user_uuid) and sys_user_department.dept_id = values(dept_id) then current_timestamp else sys_user_department.updated_at end
                        """,
                userId, userUuid, primary ? 1 : 0, actor.userId(), actor.userUuid(), actor.userId(), actor.userUuid(), departmentId
        );
    }

    @Override
    public Map<Long, String> findActiveUserUuids(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return database.query(
                """
                        select id, uuid
                        from sys_user
                        where deleted = 0
                          and id in (%s)
                        """.formatted(placeholders(userIds.size())),
                rows -> {
                    Map<Long, String> result = new LinkedHashMap<>();
                    while (rows.next()) {
                        result.putIfAbsent(rows.getLong("id"), rows.getString("uuid"));
                    }
                    return result;
                },
                userIds.toArray()
        );
    }

    @Override
    public String findActiveUserUuid(Long userId) {
        return nullableString("select uuid from sys_user where id = ? and deleted = 0 limit 1", userId);
    }

    @Override
    public List<Long> findActiveUserRoleIds(Long userId, String userUuid) {
        return database.queryForList(
                """
                        select ur.role_id
                        from sys_user_role ur
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by ur.role_id asc
                        """,
                Long.class,
                userId, userUuid
        );
    }

    @Override
    public List<Long> findActiveUserDepartmentIds(Long userId, String userUuid) {
        return database.queryForList(
                """
                        select ud.dept_id
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.deleted = 0
                        where ud.user_id = ?
                          and ud.user_uuid = ?
                          and ud.deleted = 0
                        order by ud.primary_flag desc, ud.dept_id asc
                        """,
                Long.class,
                userId, userUuid
        );
    }

    @Override
    public List<String> findActiveUserRoleNames(Long userId, String userUuid) {
        return database.queryForList(
                """
                        select r.role_name
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        order by r.id asc
                        """,
                String.class,
                userId, userUuid
        );
    }

    @Override
    public List<String> findActiveUserDepartmentNames(Long userId, String userUuid) {
        return database.queryForList(
                """
                        select d.dept_name
                        from sys_user_department ud
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.deleted = 0
                        where ud.user_id = ?
                          and ud.user_uuid = ?
                          and ud.deleted = 0
                        order by ud.primary_flag desc, d.sort_no asc, d.id asc
                        """,
                String.class,
                userId, userUuid
        );
    }

    @Override
    public Map<Long, List<String>> findActiveUserRoleNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return database.query(
                """
                        select ur.user_id as userId, r.role_name as roleName
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        where ur.user_id in (%s)
                          and ur.user_uuid is not null
                          and trim(ur.user_uuid) <> ''
                          and ur.deleted = 0
                        order by ur.user_id asc, r.id asc
                        """.formatted(placeholders(userIds.size())),
                rows -> groupedStrings(rows, "userId", "roleName"),
                userIds.toArray()
        );
    }

    @Override
    public Map<Long, List<String>> findActiveUserDepartmentNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return database.query(
                """
                        select ud.user_id as userId, d.dept_name as deptName
                        from sys_user_department ud
                        join sys_user u
                          on u.id = ud.user_id
                         and u.uuid = ud.user_uuid
                         and u.deleted = 0
                        join sys_department d
                          on d.id = ud.dept_id
                         and d.deleted = 0
                        where ud.user_id in (%s)
                          and ud.user_uuid is not null
                          and trim(ud.user_uuid) <> ''
                          and ud.deleted = 0
                        order by ud.user_id asc, ud.primary_flag desc, d.sort_no asc, d.id asc
                        """.formatted(placeholders(userIds.size())),
                rows -> groupedStrings(rows, "userId", "deptName"),
                userIds.toArray()
        );
    }

    @Override
    public Set<Long> findActiveDepartmentTree(Long departmentId) {
        if (departmentId == null || !database.exists(
                "select 1 from sys_department where id = ? and deleted = 0 limit 1", departmentId)) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        result.add(departmentId);
        Set<Long> frontier = new LinkedHashSet<>();
        frontier.add(departmentId);
        while (!frontier.isEmpty()) {
            List<Long> children = database.queryForList(
                    "select id from sys_department where deleted = 0 and parent_id in (" + placeholders(frontier.size()) + ")",
                    Long.class,
                    frontier.toArray()
            );
            frontier = new LinkedHashSet<>();
            for (Long childId : children) {
                if (childId != null && result.add(childId)) {
                    frontier.add(childId);
                }
            }
        }
        return result;
    }

    private VisibilitySql visibilitySql(DataVisibility visibility, String userAlias) {
        if (visibility == null || visibility.all()) {
            return VisibilitySql.empty();
        }
        Set<Long> departmentIds = visibility.departmentIds() == null ? Set.of() : visibility.departmentIds();
        Set<Long> userIds = visibility.userIds() == null ? Set.of() : visibility.userIds();
        if (departmentIds.isEmpty() && userIds.isEmpty()) {
            return new VisibilitySql(" and 1 = 0", List.of());
        }
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!departmentIds.isEmpty()) {
            conditions.add("""
                    exists (
                        select 1
                        from sys_user_department sud
                        where sud.user_id = %s.id
                          and sud.dept_id in (%s)
                          and sud.deleted = 0
                    )
                    """.formatted(userAlias, placeholders(departmentIds.size())));
            params.addAll(departmentIds);
        }
        if (!userIds.isEmpty()) {
            conditions.add("%s.id in (%s)".formatted(userAlias, placeholders(userIds.size())));
            params.addAll(userIds);
        }
        return new VisibilitySql(" and (" + String.join(" or ", conditions) + ")", params);
    }

    private PageResponse<SystemVO.UserVO> pageQuery(
            String selectSql,
            String countSql,
            long pageNo,
            long pageSize,
            List<Object> params
    ) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<SystemVO.UserVO> records = database.query(
                selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(SystemVO.UserVO.class), queryParams.toArray()
        );
        Long count = safePageNo == 1 && records.size() < safePageSize
                ? (long) records.size()
                : database.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<SystemVO.UserVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(count == null ? 0 : count);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private PageResponse<SystemVO.UserVO> cursorQuery(String selectSql, long pageSize, List<Object> params) {
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize + 1);
        List<SystemVO.UserVO> records = database.query(
                selectSql + " limit ?", new BeanPropertyRowMapper<>(SystemVO.UserVO.class), queryParams.toArray()
        );
        boolean hasMore = records.size() > safePageSize;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, (int) safePageSize));
        }
        PageResponse<SystemVO.UserVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(-1);
        response.setPageNo(1);
        response.setPageSize(safePageSize);
        response.setHasMore(hasMore);
        if (!records.isEmpty()) {
            SystemVO.UserVO user = records.get(records.size() - 1);
            response.setNextCursorId(user.getId());
            response.setNextCursorCreatedAt(user.getRegisteredAt() == null ? null : user.getRegisteredAt().toString());
        }
        return response;
    }

    private SystemVO.UserVO queryOne(String sql, Class<SystemVO.UserVO> voClass, Object... params) {
        try {
            return database.queryForObject(sql, new BeanPropertyRowMapper<>(voClass), params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Long nullableLong(String sql, Object... params) {
        try {
            return database.queryForObject(sql, Long.class, params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private String nullableString(String sql, Object... params) {
        try {
            return database.queryForObject(sql, String.class, params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private int count(String sql, List<Long> ids) {
        Long value = database.queryForObject(sql, Long.class, ids.toArray());
        return value == null ? 0 : value.intValue();
    }

    private Map<Long, List<String>> groupedStrings(SqlRowCursor rows, String idColumn, String valueColumn) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        while (rows.next()) {
            result.computeIfAbsent(rows.getLong(idColumn), ignored -> new ArrayList<>())
                    .add(rows.getString(valueColumn));
        }
        return result;
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private record VisibilitySql(String sql, List<Object> params) {
        static VisibilitySql empty() {
            return new VisibilitySql("", List.of());
        }
    }
}
