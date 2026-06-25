package com.lumira.saas.modules.ai.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistantEmployeeResolverTest {

    @Test
    void createsAssistantEmployeeWhenMissing() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiAssistantEmployeeResolver resolver = new AiAssistantEmployeeResolver(jdbcTemplate);

        AiVO.EmployeeVO employee = resolver.getOrCreateAssistantEmployee();

        assertThat(employee.getId()).isEqualTo(500L);
        assertThat(employee.getUsername()).isEqualTo("ai-assistant");
        assertThat(jdbcTemplate.queryCount).isEqualTo(2);
        assertThat(jdbcTemplate.lastUpdateSql).contains("insert into ai_employee");
        assertThat(jdbcTemplate.lastUpdateArgs).contains(1001L, "ai-assistant", "AI Assistant");
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private int queryCount;
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;

        @Override
        public int update(String sql, Object... args) {
            this.lastUpdateSql = sql;
            this.lastUpdateArgs = args;
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount += 1;
            if (queryCount == 1) {
                return List.of();
            }
            AiVO.EmployeeVO employee = new AiVO.EmployeeVO();
            employee.setId(500L);
            employee.setUsername("ai-assistant");
            employee.setNickname("AI Assistant");
            employee.setEnabled(true);
            return List.of((T) employee);
        }
    }
}
