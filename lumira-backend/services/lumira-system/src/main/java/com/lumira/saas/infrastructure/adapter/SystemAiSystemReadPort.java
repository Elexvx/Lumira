package com.lumira.saas.infrastructure.adapter;

import com.lumira.api.ai.AiSystemReadPort;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/** System-owned read adapter for the explicit AI native-tool query surface. */
public class SystemAiSystemReadPort implements AiSystemReadPort {

    private final MyBatisQueryOperations database;
    private final PermissionGuard permissionGuard;

    public SystemAiSystemReadPort(MyBatisQueryOperations database, PermissionGuard permissionGuard) {
        this.database = database;
        this.permissionGuard = permissionGuard;
    }

    @Override
    public UserSearchPage searchUsers(CurrentUser actor, String keyword, String status, int limit) {
        permissionGuard.requirePermission(actor, "system:user:view");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select u.id, u.username, u.nickname, u.real_name as realName, u.mobile, u.email,
                       u.status, u.created_at as createdAt, u.updated_at as updatedAt
                from sys_user u
                where u.deleted = 0
                """);
        appendUserFilters(sql, args, keyword, status);
        sql.append(" order by u.id desc limit ?");
        args.add(safeLimit);
        List<UserItem> users = database.queryForList(sql.toString(), args.toArray()).stream()
                .map(this::toUserItem)
                .toList();

        List<Object> countArgs = new ArrayList<>();
        StringBuilder countSql = new StringBuilder("select count(1) from sys_user u where u.deleted = 0");
        appendUserFilters(countSql, countArgs, keyword, status);
        Long total = database.queryForObject(countSql.toString(), Long.class, countArgs.toArray());
        return new UserSearchPage(users, total == null ? 0L : total);
    }

    @Override
    public List<MenuItem> findMenus(CurrentUser actor, String status, int limit) {
        permissionGuard.requirePermission(actor, "system:menu:view");
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return database.queryForList("""
                select id, parent_id as parentId, menu_code as menuCode, menu_name as menuName,
                       menu_type as menuType, path, component, permission_key as permissionKey,
                       status, sort_no as sortNo
                from sys_menu
                where deleted = 0
                  and (? is null or status = ?)
                order by sort_no asc, id asc
                limit ?
                """, normalizedStatus, normalizedStatus, safeLimit).stream()
                .map(this::toMenuItem)
                .toList();
    }

    @Override
    public ConfigItem findConfig(CurrentUser actor, String configKey) {
        permissionGuard.requirePermission(actor, "system:config:view");
        if (!StringUtils.hasText(configKey)) {
            return null;
        }
        return database.queryForList("""
                select config_key as configKey, config_name as configName, config_value as configValue,
                       is_system as system
                from sys_config
                where config_key = ? and deleted = 0
                limit 1
                """, configKey.trim()).stream()
                .findFirst()
                .map(this::toConfigItem)
                .orElse(null);
    }

    private void appendUserFilters(StringBuilder sql, List<Object> args, String keyword, String status) {
        if (StringUtils.hasText(keyword)) {
            sql.append(" and (u.username like ? or u.nickname like ? or u.real_name like ? or u.mobile like ? or u.email like ?)");
            String pattern = "%" + keyword.trim() + "%";
            for (int index = 0; index < 5; index += 1) {
                args.add(pattern);
            }
        }
        if (StringUtils.hasText(status)) {
            sql.append(" and u.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
    }

    private UserItem toUserItem(Map<String, Object> row) {
        return new UserItem(
                longValue(row.get("id")),
                stringValue(row.get("username")),
                stringValue(row.get("nickname")),
                stringValue(row.get("realName")),
                stringValue(row.get("mobile")),
                stringValue(row.get("email")),
                stringValue(row.get("status")),
                timeValue(row.get("createdAt")),
                timeValue(row.get("updatedAt"))
        );
    }

    private MenuItem toMenuItem(Map<String, Object> row) {
        return new MenuItem(
                longValue(row.get("id")),
                longValue(row.get("parentId")),
                stringValue(row.get("menuCode")),
                stringValue(row.get("menuName")),
                stringValue(row.get("menuType")),
                stringValue(row.get("path")),
                stringValue(row.get("component")),
                stringValue(row.get("permissionKey")),
                stringValue(row.get("status")),
                intValue(row.get("sortNo"))
        );
    }

    private ConfigItem toConfigItem(Map<String, Object> row) {
        return new ConfigItem(
                stringValue(row.get("configKey")),
                stringValue(row.get("configName")),
                stringValue(row.get("configValue")),
                intValue(row.get("system"))
        );
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime timeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
