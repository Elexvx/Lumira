package com.lumira.saas.modules.system.workorder.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.workorder.repository.WorkOrderPluginStateRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkOrderPluginStateRepository implements WorkOrderPluginStateRepository {
    private final MyBatisQueryOperations database;

    public JdbcWorkOrderPluginStateRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public boolean isPluginEnabled(String pluginCode) {
        return database.exists("""
                select 1 from sys_plugin_definition d
                join sys_plugin_version v on v.plugin_code = d.plugin_code and v.is_active = 1 and v.deleted = 0
                where d.plugin_code = ? and d.status = 'ENABLED' and d.deleted = 0 limit 1
                """, pluginCode);
    }

    @Override
    public boolean hasFeedbackTable() {
        return database.exists("""
                select 1 from information_schema.tables
                where table_schema = database() and table_name = 'sys_work_order_feedback' limit 1
                """);
    }
}
