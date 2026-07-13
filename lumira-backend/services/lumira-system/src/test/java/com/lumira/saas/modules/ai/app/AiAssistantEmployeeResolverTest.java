package com.lumira.saas.modules.ai.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.modules.ai.infrastructure.JdbcAiAssistantEmployeeRepository;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAssistantEmployeeResolverTest {

    @Test
    void readsDatabaseSeededAssistantEmployee() {
        StubQueryOperations jdbcTemplate = new StubQueryOperations();
        AiAssistantEmployeeResolver resolver = new AiAssistantEmployeeResolver(new JdbcAiAssistantEmployeeRepository(jdbcTemplate));

        AiVO.EmployeeVO employee = resolver.getOrCreateAssistantEmployee();

        assertThat(employee.getId()).isEqualTo(500L);
        assertThat(employee.getUsername()).isEqualTo("ai-assistant");
        assertThat(jdbcTemplate.queryCount).isEqualTo(1);
    }

    private static class StubQueryOperations extends MyBatisQueryOperations {
        private int queryCount;
        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCount += 1;
            AiVO.EmployeeVO employee = new AiVO.EmployeeVO();
            employee.setId(500L);
            employee.setUsername("ai-assistant");
            employee.setNickname("AI Assistant");
            employee.setEnabled(true);
            return List.of((T) employee);
        }
    }
}
