package com.lumira.saas.modules.system.internal.infrastructure;

import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.saas.modules.system.internal.repository.InternalSystemRepository;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JDBC/MyBatis implementation of the internal-system persistence boundary. */
@Repository
public class JdbcInternalSystemRepository implements InternalSystemRepository {

    private final MyBatisQueryOperations database;

    public JdbcInternalSystemRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<SystemUserSnapshotDTO> findEnabledUserIdentities(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(userIds.size());
        return database.query(
                """
                        select u.id,
                               u.uuid,
                               u.username,
                               u.status
                        from sys_user u
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                          and u.id in (
                        """ + placeholders + """
                          )
                        order by u.id asc
                        """,
                (row, rowNum) -> userIdentity(row),
                userIds.toArray()
        );
    }

    @Override
    public List<SystemRoleSnapshotDTO> findRoleNames(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return database.query(
                """
                        select id, role_code, role_name
                        from sys_role
                        where deleted = 0
                          and id in (
                        """ + placeholders(roleIds.size()) + """
                          )
                        order by id asc
                        """,
                (row, rowNum) -> new SystemRoleSnapshotDTO(
                        row.getLong("id"),
                        null,
                        row.getString("role_name")
                ),
                roleIds.toArray()
        );
    }

    @Override
    public List<SystemUserSnapshotDTO> findEnabledRoleUserIdentities(Long roleId) {
        return database.query(
                """
                        select distinct u.id,
                               u.uuid,
                               u.username,
                               u.status
                        from sys_user_role ur
                        join sys_user u on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        where ur.role_id = ?
                          and ur.deleted = 0
                        order by u.id asc
                        """,
                (row, rowNum) -> userIdentity(row),
                roleId
        );
    }

    @Override
    public List<SystemUserEmailRecipientDTO> findEmailRecipientsByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return database.query(
                """
                        select u.id as user_id, u.uuid as user_uuid, u.username, u.email
                        from sys_user u
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                          and u.id in (
                        """ + placeholders(userIds.size()) + """
                          )
                        order by u.id asc
                        """,
                this::emailRecipient,
                userIds.toArray()
        );
    }

    @Override
    public List<SystemUserWechatRecipientDTO> findWechatRecipientsByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return database.query(
                """
                        select u.id as user_id, u.uuid as user_uuid, u.username, wb.openid as wechat_openid
                        from sys_user u
                        left join sys_user_wechat_binding wb
                          on wb.user_id = u.id
                         and wb.user_uuid = u.uuid
                         and wb.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                          and u.id in (
                        """ + placeholders(userIds.size()) + """
                          )
                        order by u.id asc
                        """,
                this::wechatRecipient,
                userIds.toArray()
        );
    }

    @Override
    public List<SystemUserEmailRecipientDTO> findEmailRecipientsByRoleId(Long roleId) {
        return database.query(
                """
                        select distinct u.id as user_id, u.uuid as user_uuid, u.username, u.email
                        from sys_user u
                        join sys_user_role ur
                          on ur.user_id = u.id
                         and ur.user_uuid = u.uuid
                         and ur.role_id = ?
                         and ur.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                this::emailRecipient,
                roleId
        );
    }

    @Override
    public List<SystemUserWechatRecipientDTO> findWechatRecipientsByRoleId(Long roleId) {
        return database.query(
                """
                        select distinct u.id as user_id, u.uuid as user_uuid, u.username, wb.openid as wechat_openid
                        from sys_user u
                        join sys_user_role ur
                          on ur.user_id = u.id
                         and ur.user_uuid = u.uuid
                         and ur.role_id = ?
                         and ur.deleted = 0
                        left join sys_user_wechat_binding wb
                          on wb.user_id = u.id
                         and wb.user_uuid = u.uuid
                         and wb.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                this::wechatRecipient,
                roleId
        );
    }

    @Override
    public List<SystemUserEmailRecipientDTO> findPlatformEmailRecipients() {
        return database.query(
                """
                        select distinct u.id as user_id, u.uuid as user_uuid, u.username, u.email
                        from sys_user u
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                this::emailRecipient
        );
    }

    @Override
    public List<SystemUserWechatRecipientDTO> findPlatformWechatRecipients() {
        return database.query(
                """
                        select distinct u.id as user_id, u.uuid as user_uuid, u.username, wb.openid as wechat_openid
                        from sys_user u
                        left join sys_user_wechat_binding wb
                          on wb.user_id = u.id
                         and wb.user_uuid = u.uuid
                         and wb.deleted = 0
                        where u.deleted = 0
                          and u.status = 'ENABLED'
                        order by u.id asc
                        """,
                this::wechatRecipient
        );
    }

    @Override
    public int upsertPluginPermission(
            String permissionKey,
            String permissionName,
            String permissionGroup,
            String pluginCode,
            Long actorId,
            String actorUuid
    ) {
        return database.update(
                """
                        insert into sys_permission (
                            permission_key, permission_name, permission_group, source_type,
                            plugin_code, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, 'PLUGIN', ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            permission_name = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid) then values(permission_name) else permission_name end,
                            permission_group = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid) then values(permission_group) else permission_group end,
                            updated_by = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid) then values(updated_by) else updated_by end,
                            updated_by_uuid = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid) then values(updated_by_uuid) else updated_by_uuid end,
                            updated_at = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid) then current_timestamp else updated_at end,
                            deleted = case when source_type = 'PLUGIN' and plugin_code = values(plugin_code) and updated_by_uuid = values(updated_by_uuid) then 0 else deleted end
                        """,
                permissionKey,
                permissionName,
                permissionGroup,
                pluginCode,
                actorId,
                actorUuid,
                actorId,
                actorUuid
        );
    }

    @Override
    public List<Long> findActiveAdminRoleIds() {
        return database.queryForList(
                """
                        select id
                        from sys_role
                        where role_code = 'ADMIN'
                          and deleted = 0
                        """,
                Long.class
        );
    }

    @Override
    public int upsertRolePermission(Long roleId, String permissionKey, Long actorId, String actorUuid) {
        return database.update(
                """
                        insert into sys_role_permission (
                            role_id, permission_key, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            updated_by = case when role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid) then values(updated_by) else updated_by end,
                            updated_by_uuid = case when role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid) then values(updated_by_uuid) else updated_by_uuid end,
                            updated_at = case when role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid) then current_timestamp else updated_at end,
                            deleted = case when role_id = values(role_id) and permission_key = values(permission_key) and updated_by_uuid = values(updated_by_uuid) then 0 else deleted end
                        """,
                roleId,
                permissionKey,
                actorId,
                actorUuid,
                actorId,
                actorUuid
        );
    }

    @Override
    public boolean hasActivePluginPermission(String permissionKey, String pluginCode, String actorUuid) {
        return database.exists(
                """
                        select 1
                        from sys_permission
                        where permission_key = ?
                          and source_type = 'PLUGIN'
                          and plugin_code = ?
                          and updated_by_uuid = ?
                          and deleted = 0
                        limit 1
                        """,
                permissionKey,
                pluginCode,
                actorUuid
        );
    }

    @Override
    public boolean hasActivePluginRolePermission(Long roleId, String pluginCode, String permissionKey, String actorUuid) {
        return database.exists(
                """
                        select 1
                        from sys_role_permission rp
                        join sys_permission p
                          on p.permission_key = rp.permission_key
                         and p.source_type = 'PLUGIN'
                         and p.plugin_code = ?
                         and p.deleted = 0
                        where rp.role_id = ?
                          and rp.permission_key = ?
                          and rp.updated_by_uuid = ?
                          and rp.deleted = 0
                        limit 1
                        """,
                pluginCode,
                roleId,
                permissionKey,
                actorUuid
        );
    }

    @Override
    public List<PlatformConfigValue> findPlatformConfigValues(List<String> configKeys) {
        if (configKeys == null || configKeys.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = database.queryForList(
                """
                        select config_key, config_value
                        from sys_config
                        where config_scope = 'PLATFORM'
                          and deleted = 0
                          and config_key in (
                        """ + placeholders(configKeys.size()) + """
                          )
                        order by id desc
                        """,
                configKeys.toArray()
        );
        List<PlatformConfigValue> values = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object key = row.get("config_key");
            Object value = row.get("config_value");
            values.add(new PlatformConfigValue(
                    key == null ? null : String.valueOf(key),
                    value == null ? null : String.valueOf(value)
            ));
        }
        return values;
    }

    @Override
    public int updatePassword(String encodedPassword, Long actorId, String actorUuid, Long userId, String userUuid) {
        return database.update(
                """
                        update sys_user
                        set password_hash = ?,
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = current_timestamp
                        where id = ?
                          and uuid = ?
                          and deleted = 0
                          and status = 'ENABLED'
                        """,
                encodedPassword,
                actorId,
                actorUuid,
                userId,
                userUuid
        );
    }

    @Override
    public List<SystemVO.MenuVO> findEnabledSystemMenus() {
        return database.query(
                """
                        select id, parent_id as parentId, menu_code as menuCode,
                               menu_name as menuName, menu_type as menuType, path, component, icon, sort_no as sortNo,
                               permission_key as permissionKey, status
                        from sys_menu
                        where deleted = 0 and status = 'ENABLED'
                        order by sort_no asc, id asc
                        """,
                new BeanPropertyRowMapper<>(SystemVO.MenuVO.class)
        );
    }

    @Override
    public List<CurrentUserRoleOptionDTO> findRoleOptions(Long userId, String userUuid) {
        return database.query(
                """
                        select r.id as id,
                               r.role_code as roleCode,
                               r.role_name as roleName,
                               r.role_type as roleType,
                               r.default_home_path as defaultHomePath,
                               count(rp.permission_key) as permissionCount
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.deleted = 0
                        left join sys_role_permission rp on rp.role_id = r.id and rp.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.deleted = 0
                        group by r.id, r.role_code, r.role_name, r.role_type, r.default_home_path
                        order by r.id desc
                        """,
                (row, rowNum) -> new CurrentUserRoleOptionDTO(
                        row.getLong("id"),
                        row.getString("roleCode"),
                        row.getString("roleName"),
                        row.getString("roleType"),
                        row.getInt("permissionCount"),
                        row.getString("defaultHomePath")
                ),
                userId,
                userUuid
        );
    }

    @Override
    public Long findEnabledUserIdByWechatBinding(String unionid, String openid) {
        List<Long> userIds = database.query(
                """
                        select u.id
                        from sys_user_wechat_binding b
                        join sys_user u
                          on u.id = b.user_id
                         and u.uuid = b.user_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                         and u.uuid is not null
                         and u.uuid <> ''
                        where b.deleted = 0
                          and ((? <> '' and b.unionid = ?) or b.openid = ?)
                        order by case when ? <> '' and b.unionid = ? then 0 else 1 end, b.id desc
                        limit 1
                        """,
                (row, rowNum) -> row.getLong("id"),
                unionid,
                unionid,
                openid,
                unionid,
                unionid
        );
        return userIds.isEmpty() ? null : userIds.getFirst();
    }

    @Override
    public boolean hasActiveWechatBinding(String unionid, String openid) {
        return database.exists(
                """
                        select 1
                        from sys_user_wechat_binding b
                        where b.deleted = 0
                          and ((? <> '' and b.unionid = ?) or b.openid = ?)
                        limit 1
                        """,
                unionid,
                unionid,
                openid
        );
    }

    @Override
    public int insertWechatUser(String userUuid, String username, String passwordHash, Long actorId, String actorUuid) {
        return database.update(
                """
                        insert into sys_user (
                            uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                userUuid,
                username,
                passwordHash,
                null,
                "微信用户",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ENABLED",
                actorId,
                actorUuid,
                actorId,
                actorUuid
        );
    }

    @Override
    public int insertLoginCodeUser(
            String userUuid,
            String username,
            String passwordHash,
            String mobile,
            String nickname,
            String email,
            Long actorId,
            String actorUuid
    ) {
        return database.update(
                """
                        insert into sys_user (
                            uuid, username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?, ?, ?, 0)
                        """,
                userUuid,
                username,
                passwordHash,
                mobile,
                nickname,
                null,
                null,
                email,
                null,
                null,
                null,
                null,
                null,
                actorId,
                actorUuid,
                actorId,
                actorUuid
        );
    }

    @Override
    public Long findActiveUserIdByUsername(String username) {
        return database.queryForObject(
                "select id from sys_user where username = ? and deleted = 0 order by id desc limit 1",
                Long.class,
                username
        );
    }

    @Override
    public int upsertWechatBinding(Long userId, String userUuid, String openid, String unionid, String scope) {
        return database.update(
                """
                        insert into sys_user_wechat_binding (
                            user_id, user_uuid, openid, unionid, scope, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update unionid = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(unionid) else unionid end,
                                                scope = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(scope) else scope end,
                                                updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(updated_by) else updated_by end,
                                                updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) then values(updated_by_uuid) else updated_by_uuid end,
                                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) then current_timestamp else updated_at end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) then 0 else deleted end
                        """,
                userId,
                userUuid,
                openid,
                unionid,
                scope,
                userId,
                userUuid,
                userId,
                userUuid
        );
    }

    @Override
    public boolean hasActiveWechatBindingOwnedByUser(Long userId, String userUuid, String openid, String unionid) {
        return database.exists(
                """
                        select 1
                        from sys_user_wechat_binding b
                        join sys_user u
                          on u.id = b.user_id
                         and u.uuid = b.user_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        where b.user_id = ?
                          and b.user_uuid = ?
                          and b.openid = ?
                          and (? is null or b.unionid = ?)
                          and b.deleted = 0
                        limit 1
                        """,
                userId,
                userUuid,
                openid,
                unionid,
                unionid
        );
    }

    @Override
    public int updateWechatProfile(Long userId, String userUuid, String nickname, String avatarUrl) {
        return database.update(
                """
                        update sys_user
                        set nickname = case
                                when ? is not null then ?
                                else nickname
                            end,
                            avatar_url = case
                                when ? is not null then ?
                                else avatar_url
                            end,
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = current_timestamp
                        where id = ?
                          and uuid = ?
                          and deleted = 0
                        """,
                nickname,
                nickname,
                avatarUrl,
                avatarUrl,
                userId,
                userUuid,
                userId,
                userUuid
        );
    }

    @Override
    public int upsertUserRole(Long userId, String userUuid, Long roleId) {
        return database.update(
                """
                        insert into sys_user_role (user_id, user_uuid, role_id, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                        values (?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update updated_by = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then values(updated_by) else updated_by end,
                                                updated_by_uuid = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then values(updated_by_uuid) else updated_by_uuid end,
                                                updated_at = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then current_timestamp else updated_at end,
                                                deleted = case when user_id = values(user_id) and user_uuid = values(user_uuid) and role_id = values(role_id) then 0 else deleted end
                        """,
                userId,
                userUuid,
                roleId,
                userId,
                userUuid,
                userId,
                userUuid
        );
    }

    @Override
    public boolean hasActiveUserRole(Long userId, String userUuid, Long roleId) {
        return database.exists(
                """
                        select 1
                        from sys_user_role ur
                        join sys_user u
                          on u.id = ur.user_id
                         and u.uuid = ur.user_uuid
                         and u.deleted = 0
                         and u.status = 'ENABLED'
                        join sys_role r
                          on r.id = ur.role_id
                         and r.deleted = 0
                        where ur.user_id = ?
                          and ur.user_uuid = ?
                          and ur.role_id = ?
                          and ur.deleted = 0
                        limit 1
                        """,
                userId,
                userUuid,
                roleId
        );
    }

    @Override
    public RegistrationRole findActiveRoleByCode(String roleCode) {
        return database.query(
                """
                        select id, role_code, role_type
                        from sys_role
                        where role_code = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                rows -> rows.next()
                        ? new RegistrationRole(rows.getLong("id"), rows.getString("role_code"), rows.getString("role_type"))
                        : null,
                roleCode
        );
    }

    @Override
    public List<String> findActiveRolePermissionKeys(Long roleId) {
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
    public String findLatestPlatformConfigValue(String configKey) {
        return database.query(
                """
                        select config_value
                        from sys_config
                        where deleted = 0
                          and config_scope = 'PLATFORM'
                          and config_key = ?
                        order by id desc
                        limit 1
                        """,
                rows -> rows.next() ? rows.getString("config_value") : null,
                configKey
        );
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private SystemUserSnapshotDTO userIdentity(SqlRow row) {
        return new SystemUserSnapshotDTO(
                row.getLong("id"),
                row.getString("uuid"),
                row.getString("username"),
                null,
                row.getString("status"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private SystemUserEmailRecipientDTO emailRecipient(SqlRow row, int rowNum) {
        return new SystemUserEmailRecipientDTO(
                row.getLong("user_id"),
                row.getString("user_uuid"),
                row.getString("username"),
                row.getString("email")
        );
    }

    private SystemUserWechatRecipientDTO wechatRecipient(SqlRow row, int rowNum) {
        return new SystemUserWechatRecipientDTO(
                row.getLong("user_id"),
                row.getString("user_uuid"),
                row.getString("username"),
                row.getString("wechat_openid")
        );
    }
}
