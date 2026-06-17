package com.lumira.saas.modules.ai.app;

import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiIamQueryFacadeTest {

    @Test
    void searchUsersShouldSkipCountForShortResultAndCapLimit() {
        RecordingQueryOperations queryOperations = new RecordingQueryOperations();
        DefaultAiIamQueryFacade facade = new DefaultAiIamQueryFacade(queryOperations);

        AiIamQueryFacade.UserSearchResult result = facade.searchUsers(1001L, "admin", "ENABLED", 500);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items().getFirst().get("mobile")).isEqualTo("138****8000");
        assertThat(result.items().getFirst().get("email")).isEqualTo("a***@example.com");
        assertThat(queryOperations.countQueryCalled).isFalse();
        assertThat(queryOperations.requestedLimit).isEqualTo(100);
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean countQueryCalled;
        private int requestedLimit;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            return requiredType.cast(10L);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            requestedLimit = ((Number) args[args.length - 1]).intValue();
            return List.of(Map.of(
                    "id", 100L,
                    "username", "admin",
                    "realName", "管理员",
                    "mobile", "13800008000",
                    "email", "admin@example.com",
                    "status", "ENABLED",
                    "createdAt", LocalDateTime.now()
            ));
        }
    }
}
