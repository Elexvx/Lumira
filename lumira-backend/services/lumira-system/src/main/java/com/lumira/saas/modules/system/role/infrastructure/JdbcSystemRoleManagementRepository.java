package com.lumira.saas.modules.system.role.infrastructure;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.role.repository.SystemRoleManagementRepository;
import com.lumira.saas.modules.system.role.vo.RoleDataScopeVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis/JDBC adapter for the system-role management persistence boundary. */
@Repository
public class JdbcSystemRoleManagementRepository implements SystemRoleManagementRepository {
    private static final long MAX_PAGE_SIZE = 100L;
    private final MyBatisQueryOperations database;

    public JdbcSystemRoleManagementRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public PageResponse<SystemVO.RoleVO> findRoles(RoleSearch search) {
        String baseSql = """
                from sys_role r
                where r.deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.roleCode())) {
            baseSql += " and r.role_code like ?";
            params.add(like(search.roleCode()));
        }
        if (StringUtils.hasText(search.roleName())) {
            baseSql += " and r.role_name like ?";
            params.add(like(search.roleName()));
        }
        if (StringUtils.hasText(search.roleType())) {
            baseSql += " and r.role_type = ?";
            params.add(search.roleType());
        }
        String selectSql = """
                select r.id, r.role_code as roleCode, r.role_name as roleName,
                       r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                """ + baseSql + " order by r.id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, SystemVO.RoleVO.class, search.pageNo(), search.pageSize(), params);
    }

    @Override
    public List<SystemVO.RoleVO> findActiveRoles() {
        return database.queryForList(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath,
                               r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.deleted = 0
                        order by case when r.role_code = 'ADMIN' then 0 else 1 end,
                                 r.role_name asc, r.id asc
                        """,
                SystemVO.RoleVO.class
        );
    }

    @Override
    public SystemVO.RoleVO findActiveRoleById(Long roleId) {
        return queryOne(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.id = ? and r.deleted = 0
                        """,
                SystemVO.RoleVO.class,
                roleId
        );
    }

    @Override
    public SystemVO.RoleVO findLatestActiveRoleByCode(String roleCode) {
        return queryOne(
                """
                        select r.id, r.role_code as roleCode, r.role_name as roleName,
                               r.role_type as roleType, r.default_home_path as defaultHomePath, r.created_at as createdAt, r.updated_at as updatedAt
                        from sys_role r
                        where r.role_code = ? and r.deleted = 0
                        order by r.id desc
                        limit 1
                        """,
                SystemVO.RoleVO.class,
                roleCode
        );
    }

    @Override
    public int softDeleteRole(RoleVersion role, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_role
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and role_code = ?
                          and role_type = ?
                          and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt, role.roleId(), role.roleCode(), role.roleType()
        );
    }

    @Override
    public void retireDeletedRoleRelations(Long roleId, Actor actor, LocalDateTime updatedAt) {
        database.update(
                """
                        update sys_role_permission
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_permission.role_id
                                and r.deleted = 1
                                and r.updated_by = ?
                                and r.updated_by_uuid = ?
                          )
                        """,
                actor.userId(), actor.userUuid(), updatedAt, roleId, actor.userId(), actor.userUuid()
        );
        database.update(
                """
                        update sys_role_data_scope
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_data_scope.role_id
                                and r.deleted = 1
                                and r.updated_by = ?
                                and r.updated_by_uuid = ?
                          )
                        """,
                actor.userId(), actor.userUuid(), updatedAt, roleId, actor.userId(), actor.userUuid()
        );
    }

    @Override
    public List<String> findActivePermissionKeys(Long roleId) {
        return database.queryForList(
                """
                        select permission_key
                        from sys_role_permission
                        where role_id = ? and deleted = 0
                        order by permission_key asc
                        """,
                String.class,
                roleId
        );
    }

    @Override
    public RoleSaveResult saveRole(RoleSave command) {
        if (command.existingRole() == null) {
            int inserted = database.update(
                    """
                            insert into sys_role (role_code, role_name, role_type, default_home_path, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                            values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    command.roleCode(), command.roleName(), command.roleType(), command.defaultHomePath(),
                    command.actor().userId(), command.actor().userUuid(), command.actor().userId(), command.actor().userUuid()
            );
            Long roleId = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
            return new RoleSaveResult(inserted, roleId);
        }
        RoleVersion existing = command.existingRole();
        int updated = database.update(
                """
                        update sys_role
                        set role_code = ?, role_name = ?, role_type = ?, default_home_path = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and role_code = ? and role_type = ? and deleted = 0
                        """,
                command.roleCode(), command.roleName(), command.roleType(), command.defaultHomePath(),
                command.actor().userId(), command.actor().userUuid(), command.updatedAt(), existing.roleId(),
                existing.roleCode(), existing.roleType()
        );
        return new RoleSaveResult(updated, existing.roleId());
    }

    @Override
    public int retireRolePermissions(Long roleId, RoleVersion existingRole, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_role_permission
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_permission.role_id
                                and r.role_code = ?
                                and r.role_type = ?
                                and r.deleted = 0
                          )
                        """,
                actor.userId(), actor.userUuid(), updatedAt, roleId,
                existingRole == null ? null : existingRole.roleCode(),
                existingRole == null ? null : existingRole.roleType()
        );
    }

    @Override
    public int upsertRolePermissions(Long roleId, RoleVersion existingRole, List<String> permissionKeys, Actor actor) {
        if (permissionKeys == null || permissionKeys.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("""
                insert into sys_role_permission (role_id, permission_key, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                """);
        List<Object> params = new ArrayList<>(permissionKeys.size() * 8);
        if (existingRole == null) {
            sql.append("values ");
            for (int index = 0; index < permissionKeys.size(); index += 1) {
                if (index > 0) {
                    sql.append(", ");
                }
                sql.append("(?, ?, ?, ?, ?, ?, 0)");
                params.add(roleId);
                params.add(permissionKeys.get(index));
                params.add(actor.userId());
                params.add(actor.userUuid());
                params.add(actor.userId());
                params.add(actor.userUuid());
            }
        } else {
            for (int index = 0; index < permissionKeys.size(); index += 1) {
                if (index > 0) {
                    sql.append(" union all ");
                }
                sql.append("""
                        select r.id, ?, ?, ?, ?, ?, 0
                        from sys_role r
                        where r.id = ?
                          and r.role_code = ?
                          and r.role_type = ?
                          and r.deleted = 0
                        """);
                params.add(permissionKeys.get(index));
                params.add(actor.userId());
                params.add(actor.userUuid());
                params.add(actor.userId());
                params.add(actor.userUuid());
                params.add(roleId);
                params.add(existingRole.roleCode());
                params.add(existingRole.roleType());
            }
        }
        sql.append("""
                 on duplicate key update
                    updated_by = values(updated_by),
                    updated_by_uuid = values(updated_by_uuid),
                    updated_at = current_timestamp,
                    deleted = 0
                """);
        return database.update(sql.toString(), params.toArray());
    }

    @Override
    public List<RoleDataScopeVO> findActiveDataScopes(Long roleId) {
        return database.query(
                """
                        select resource_code as resourceCode, scope_type as scopeType,
                               custom_dept_ids as customDeptIds, custom_user_ids as customUserIds
                        from sys_role_data_scope
                        where role_id = ? and deleted = 0
                        order by case when resource_code = '*' then 0 else 1 end, resource_code asc
                        """,
                (rs, rowNum) -> {
                    RoleDataScopeVO scope = new RoleDataScopeVO();
                    scope.setResourceCode(rs.getString("resourceCode"));
                    scope.setScopeType(rs.getString("scopeType"));
                    scope.setCustomDeptIds(parseIdList(rs.getString("customDeptIds")));
                    scope.setCustomUserIds(parseIdList(rs.getString("customUserIds")));
                    return scope;
                },
                roleId
        );
    }

    @Override
    public int retireRoleDataScopes(Long roleId, RoleVersion existingRole, Actor actor, LocalDateTime updatedAt) {
        if (existingRole == null) {
            return database.update(
                    """
                            update sys_role_data_scope
                            set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where role_id = ? and deleted = 0
                            """,
                    actor.userId(), actor.userUuid(), updatedAt, roleId
            );
        }
        return database.update(
                """
                        update sys_role_data_scope
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where role_id = ?
                          and deleted = 0
                          and exists (
                              select 1 from sys_role r
                              where r.id = sys_role_data_scope.role_id
                                and r.role_code = ?
                                and r.role_type = ?
                                and r.deleted = 0
                          )
                        """,
                actor.userId(), actor.userUuid(), updatedAt, roleId, existingRole.roleCode(), existingRole.roleType()
        );
    }

    @Override
    public int upsertRoleDataScopes(Long roleId, List<RoleDataScopeValue> scopes, Actor actor) {
        if (scopes == null || scopes.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("""
                insert into sys_role_data_scope (
                    role_id, resource_code, scope_type, custom_dept_ids, custom_user_ids,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                ) values
                """);
        List<Object> params = new ArrayList<>(scopes.size() * 9);
        for (int index = 0; index < scopes.size(); index += 1) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?, 0)");
            RoleDataScopeValue scope = scopes.get(index);
            params.add(roleId);
            params.add(scope.resourceCode());
            params.add(scope.scopeType());
            params.add(scope.customDeptIds());
            params.add(scope.customUserIds());
            params.add(actor.userId());
            params.add(actor.userUuid());
            params.add(actor.userId());
            params.add(actor.userUuid());
        }
        sql.append("""
                 on duplicate key update
                    scope_type = values(scope_type),
                    custom_dept_ids = values(custom_dept_ids),
                    custom_user_ids = values(custom_user_ids),
                    updated_by = values(updated_by),
                    updated_by_uuid = values(updated_by_uuid),
                    updated_at = current_timestamp,
                    deleted = 0
                """);
        return database.update(sql.toString(), params.toArray());
    }

    @Override
    public int countActiveRolePermissions(Long roleId) {
        Long count = database.queryForObject(
                """
                        select count(1)
                        from sys_role_permission
                        where role_id = ? and deleted = 0
                        """,
                Long.class,
                roleId
        );
        return count == null ? 0 : count.intValue();
    }

    @Override
    public int countActiveRoleUsers(Long roleId) {
        Long count = database.queryForObject(
                """
                        select count(1)
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                        where ur.role_id = ?
                          and ur.user_uuid is not null
                          and trim(ur.user_uuid) <> ''
                          and ur.deleted = 0
                        """,
                Long.class,
                roleId
        );
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Map<Long, Integer> countActiveRolePermissions(List<Long> roleIds) {
        List<Long> distinctRoleIds = distinctIds(roleIds);
        if (distinctRoleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(distinctRoleIds.size());
        return database.query(
                """
                        select role_id as roleId, count(1) as total
                        from sys_role_permission
                        where role_id in (%s) and deleted = 0
                        group by role_id
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, Integer> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("roleId"), rs.getInt("total"));
                    }
                    return result;
                },
                distinctRoleIds.toArray()
        );
    }

    @Override
    public Map<Long, Integer> countActiveRoleUsers(List<Long> roleIds) {
        List<Long> distinctRoleIds = distinctIds(roleIds);
        if (distinctRoleIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = placeholders(distinctRoleIds.size());
        return database.query(
                """
                        select ur.role_id as roleId, count(1) as total
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                        where ur.role_id in (%s)
                          and ur.user_uuid is not null
                          and trim(ur.user_uuid) <> ''
                          and ur.deleted = 0
                        group by ur.role_id
                        """.formatted(placeholders),
                rs -> {
                    Map<Long, Integer> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("roleId"), rs.getInt("total"));
                    }
                    return result;
                },
                distinctRoleIds.toArray()
        );
    }

    @Override
    public Map<String, String> findPlatformConfigValues(List<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) {
            return Map.of();
        }
        String placeholders = configKeys.stream().map(item -> "?").collect(Collectors.joining(", "));
        List<Map<String, Object>> rows = database.queryForList(
                """
                        select config_key as configKey, config_value as configValue
                        from sys_config
                        where deleted = 0
                          and config_scope = 'PLATFORM'
                          and config_key in (%s)
                        order by id desc
                        """.formatted(placeholders),
                configKeys.toArray()
        );
        Map<String, String> valueByKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String configKey = String.valueOf(row.get("configKey"));
            if (!valueByKey.containsKey(configKey)) {
                Object value = row.get("configValue");
                valueByKey.put(configKey, value == null ? "" : String.valueOf(value).trim());
            }
        }
        return valueByKey;
    }

    @Override
    public ConfigSaveResult savePlatformConfig(ConfigSave command) {
        Long existingId = findPlatformConfigId(command.configKey());
        if (existingId == null) {
            int inserted = database.update(
                    """
                            insert into sys_config (
                                config_key, config_name, config_value, config_scope, is_system, remark,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, ?, 0)
                            """,
                    command.configKey(), command.configName(), command.configValue(), command.remark(),
                    command.actor().userId(), command.actor().userUuid(), command.actor().userId(), command.actor().userUuid()
            );
            return new ConfigSaveResult(inserted, true);
        }
        int updated = database.update(
                """
                        update sys_config
                        set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and config_key = ?
                          and config_scope = 'PLATFORM'
                          and is_system = 0
                          and deleted = 0
                        """,
                command.configName(), command.configValue(), command.remark(), command.actor().userId(), command.actor().userUuid(),
                command.updatedAt(), existingId, command.configKey()
        );
        return new ConfigSaveResult(updated, false);
    }

    private Long findPlatformConfigId(String configKey) {
        try {
            return database.queryForObject(
                    """
                            select id
                            from sys_config
                            where config_key = ?
                              and config_scope = 'PLATFORM'
                              and is_system = 0
                              and deleted = 0
                            order by id desc
                            limit 1
                            """,
                    Long.class,
                    configKey
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<T> records = database.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        Long total = safePageNo == 1 && records.size() < safePageSize
                ? (long) records.size()
                : database.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private <T> T queryOne(String sql, Class<T> voClass, Object... params) {
        try {
            return database.queryForObject(sql, new BeanPropertyRowMapper<>(voClass), params);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private List<Long> distinctIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(id -> id != null).distinct().toList();
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private List<Long> parseIdList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                long id = Long.parseLong(part.trim());
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy values.
            }
        }
        return ids;
    }
}
