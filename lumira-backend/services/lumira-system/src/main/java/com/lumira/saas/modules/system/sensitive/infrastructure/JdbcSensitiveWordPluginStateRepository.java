package com.lumira.saas.modules.system.sensitive.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.sensitive.repository.SensitiveWordPluginStateRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSensitiveWordPluginStateRepository implements SensitiveWordPluginStateRepository {
    private final MyBatisQueryOperations database;

    public JdbcSensitiveWordPluginStateRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public boolean isPluginEnabled(String pluginCode) {
        return database.exists("""
                select 1 from sys_plugin_definition d
                join sys_plugin_version v on v.plugin_code = d.plugin_code and v.is_active = 1 and v.deleted = 0
                where d.plugin_code = ? and d.status = 'ENABLED' and d.deleted = 0 limit 1
                """, pluginCode);
    }

    @Override
    public boolean hasRequiredSchema(int requiredColumnCount) {
        boolean tableExists = database.exists("""
                select 1 from information_schema.tables
                where table_schema = database() and table_name = 'sys_sensitive_word' limit 1
                """);
        if (!tableExists) return false;
        Long count = database.queryForObject("""
                select count(1) from information_schema.columns
                where table_schema = database() and table_name = 'sys_sensitive_word'
                  and column_name in ('id','word','normalized_word','category','severity','action','enabled',
                    'created_by','created_by_uuid','created_at','updated_by','updated_by_uuid','updated_at','deleted')
                """, Long.class);
        return count != null && count >= requiredColumnCount;
    }
}
