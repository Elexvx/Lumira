package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;

class AiToolPolicyServiceTest {

    @Test
    void listPoliciesShouldSkipCountForFirstShortPage() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiToolPolicyService service = new DefaultAiToolPolicyService(queryOperations);

        var response = service.listPolicies(currentUser(), 1, 10);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(queryOperations.countQueryCalled).isFalse();
    }

    private static CurrentUser currentUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("ai:tool-policy:view"));
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean countQueryCalled;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            return requiredType.cast(5L);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_tool_policy")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(Map.ofEntries(
                            entry("id", 1L),
                            entry("tenantId", 1001L),
                            entry("policyName", "默认拦截"),
                            entry("toolCode", "*"),
                            entry("actionType", "*"),
                            entry("riskLevel", "HIGH"),
                            entry("matchType", "KEYWORD"),
                            entry("matchValue", "delete"),
                            entry("verdict", "DENY"),
                            entry("message", "需要确认"),
                            entry("enabled", true),
                            entry("createTime", LocalDateTime.now()),
                            entry("updateTime", LocalDateTime.now())
                    )), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return List.of();
        }
    }
}
