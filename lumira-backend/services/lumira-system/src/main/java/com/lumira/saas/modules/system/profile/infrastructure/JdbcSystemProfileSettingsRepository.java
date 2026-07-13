package com.lumira.saas.modules.system.profile.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.profile.repository.SystemProfileSettingsRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSystemProfileSettingsRepository implements SystemProfileSettingsRepository {
    private final MyBatisQueryOperations database;

    public JdbcSystemProfileSettingsRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public Map<String, String> findPlatformConfigValues(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Map.of();
        String placeholders = keys.stream().map(item -> "?").collect(Collectors.joining(", "));
        List<Map<String, Object>> rows = database.queryForList("""
                select config_key as configKey, config_value as configValue from sys_config
                where deleted = 0 and config_scope = 'PLATFORM' and config_key in (%s)
                order by id desc
                """.formatted(placeholders), new ArrayList<>(keys).toArray());
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("configKey"));
            values.putIfAbsent(key, row.get("configValue") == null ? "" : String.valueOf(row.get("configValue")));
        }
        return values;
    }

    @Override
    public List<FieldDefinition> findEnabledFieldDefinitions(String pageKey) {
        return database.query("""
                select field_key, field_label, field_description, group_key, group_label,
                       visible_config_key, weight_config_key, default_visible, default_weight,
                       field_type, required_flag, placeholder, sort_no
                from sys_profile_field_definition
                where page_key = ? and status = 'ENABLED' and deleted = 0
                order by sort_no, id
                """, (rs, rowNum) -> new FieldDefinition(
                rs.getString("field_key"), rs.getString("field_label"), rs.getString("field_description"),
                rs.getString("group_key"), rs.getString("group_label"), rs.getString("visible_config_key"),
                rs.getString("weight_config_key"), rs.getBoolean("default_visible"), rs.getInt("default_weight"),
                rs.getString("field_type"), rs.getBoolean("required_flag"), rs.getString("placeholder"),
                rs.getInt("sort_no")), pageKey);
    }

    @Override
    public List<String> findEnabledDictionaryValues(String dictionaryCode) {
        return database.query("""
                select i.item_value from sys_dict_type t
                join sys_dict_item i on i.dict_type_id = t.id
                where t.dict_code = ? and t.status = 'ENABLED' and t.deleted = 0
                  and i.status = 'ENABLED' and i.deleted = 0
                order by i.sort_no, i.id
                """, (rs, rowNum) -> rs.getString("item_value"), dictionaryCode);
    }

    @Override
    public int upsertPlatformConfig(String key, String name, String value, String remark, Long userId, String uuid) {
        Long existingId = findConfigId(key);
        if (existingId == null) {
            return database.update("""
                    insert into sys_config (config_key, config_name, config_value, config_scope, is_system, remark,
                        created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                    values (?, ?, ?, 'PLATFORM', 0, ?, ?, ?, ?, ?, 0)
                    """, key, name, value, remark, userId, uuid, userId, uuid);
        }
        return database.update("""
                update sys_config set config_name = ?, config_value = ?, config_scope = 'PLATFORM', remark = ?,
                    updated_by = ?, updated_by_uuid = ?, updated_at = ?
                where id = ? and config_key = ? and config_scope = 'PLATFORM' and is_system = 0 and deleted = 0
                """, name, value, remark, userId, uuid, LocalDateTime.now(), existingId, key);
    }

    private Long findConfigId(String key) {
        try {
            return database.queryForObject("""
                    select id from sys_config where config_key = ? and config_scope = 'PLATFORM'
                      and is_system = 0 and deleted = 0 order by id desc limit 1
                    """, Long.class, key);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }
}
