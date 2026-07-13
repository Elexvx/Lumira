package com.lumira.saas.modules.system.settings.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.settings.repository.SystemPlatformSettingsRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSystemPlatformSettingsRepository implements SystemPlatformSettingsRepository {
    private final MyBatisQueryOperations database;
    public JdbcSystemPlatformSettingsRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public Map<String, String> findPlatformConfigValues(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Map.of();
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        List<Map<String, Object>> rows = database.queryForList("""
                select config_key as configKey, config_value as configValue from sys_config
                where deleted = 0 and config_scope = 'PLATFORM' and config_key in (%s) order by id desc
                """.formatted(placeholders), new ArrayList<>(keys).toArray());
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("configKey"));
            values.putIfAbsent(key, row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        }
        return values;
    }

    @Override
    public Map<String, String> findEffectiveSettingValues(String groupCode) {
        List<Map<String, Object>> rows = database.queryForList("""
                select d.config_key as configKey,
                       coalesce((select c.config_value from sys_config c
                                 where c.config_key = d.config_key and c.config_scope = 'PLATFORM' and c.deleted = 0
                                 order by c.id desc limit 1), d.default_value) as configValue
                from sys_platform_setting_definition d
                where d.group_code = ? and d.status = 'ENABLED' and d.deleted = 0
                order by d.sort_no, d.id
                """, groupCode);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            values.put(String.valueOf(row.get("configKey")),
                    row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        }
        return values;
    }

    @Override
    public Map<String, String> findSettingDefaults(String groupCode) {
        List<Map<String, Object>> rows = database.queryForList("""
                select config_key as configKey, default_value as configValue
                from sys_platform_setting_definition
                where group_code = ? and status = 'ENABLED' and deleted = 0 order by sort_no, id
                """, groupCode);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) values.put(String.valueOf(row.get("configKey")),
                row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        return values;
    }

    @Override
    public Map<String, String> findSettingResetValues(String groupCode) {
        List<Map<String, Object>> rows = database.queryForList("""
                select config_key as configKey, coalesce(reset_value, default_value) as configValue
                from sys_platform_setting_definition
                where group_code = ? and status = 'ENABLED' and deleted = 0 order by sort_no, id
                """, groupCode);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) values.put(String.valueOf(row.get("configKey")),
                row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        return values;
    }

    @Override
    public int upsertPlatformConfig(String key, String value, Long userId, String uuid) {
        SettingDefinition definition = findSettingDefinition(key);
        if (definition == null) return 0;
        Long id = findConfigId(key);
        if (id == null) return database.update("""
                insert into sys_config (config_key, config_name, config_value, config_scope, is_system, remark,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, ?, 0)
                """, key, definition.name(), value, definition.remark(), userId, uuid, userId, uuid);
        return database.update("""
                update sys_config set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                    updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and config_key = ? and config_scope = 'PLATFORM' and is_system = 0 and deleted = 0
                """, definition.name(), value, definition.remark(), userId, uuid, LocalDateTime.now(), id, key);
    }

    @Override
    public String findEnabledUserUuid(Long userId) {
        try { return database.queryForObject(
                "select uuid from sys_user where id = ? and status = 'ENABLED' and deleted = 0 limit 1",
                String.class, userId); }
        catch (EmptyResultDataAccessException exception) { return null; }
    }

    private Long findConfigId(String key) {
        try { return database.queryForObject("""
                select id from sys_config where config_key = ? and config_scope = 'PLATFORM'
                  and is_system = 0 and deleted = 0 order by id desc limit 1
                """, Long.class, key); }
        catch (EmptyResultDataAccessException exception) { return null; }
    }

    private SettingDefinition findSettingDefinition(String key) {
        List<Map<String, Object>> rows = database.queryForList("""
                select config_name as configName, remark
                from sys_platform_setting_definition
                where config_key = ? and status = 'ENABLED' and deleted = 0 limit 1
                """, key);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.getFirst();
        return new SettingDefinition(String.valueOf(row.get("configName")),
                row.get("remark") == null ? null : String.valueOf(row.get("remark")));
    }

    private record SettingDefinition(String name, String remark) {}
}
