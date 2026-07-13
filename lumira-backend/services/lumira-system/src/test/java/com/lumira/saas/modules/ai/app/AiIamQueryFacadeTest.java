package com.lumira.saas.modules.ai.app;

import com.lumira.saas.modules.ai.repository.AiIamUserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiIamQueryFacadeTest {

    @Test
    void searchUsersShouldSkipCountForShortResultAndCapLimit() {
        RecordingUserRepository repository = new RecordingUserRepository();
        DefaultAiIamQueryFacade facade = new DefaultAiIamQueryFacade(repository);

        AiIamQueryFacade.UserSearchResult result = facade.searchUsers("admin", "ENABLED", 500);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items().getFirst().get("mobile")).isEqualTo("138****8000");
        assertThat(result.items().getFirst().get("email")).isEqualTo("a***@example.com");
        assertThat(repository.countCalled).isFalse();
        assertThat(repository.requestedLimit).isEqualTo(100);
    }

    private static final class RecordingUserRepository implements AiIamUserRepository {
        private boolean countCalled;
        private int requestedLimit;

        @Override
        public long count(String keyword, String status) {
            countCalled = true;
            return 10L;
        }

        @Override
        public List<Map<String, Object>> search(String keyword, String status, int limit) {
            requestedLimit = limit;
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
