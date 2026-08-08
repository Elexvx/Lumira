package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.repository.AiIamUserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiIamQueryFacadeTest {

    @Test
    void searchUsersShouldUseActorScopedRepositoryAndCapLimit() {
        RecordingUserRepository repository = new RecordingUserRepository();
        DefaultAiIamQueryFacade facade = new DefaultAiIamQueryFacade(repository);

        CurrentUser actor = new CurrentUser();
        actor.setUserId(100L);
        AiIamQueryFacade.UserSearchResult result = facade.searchUsers(actor, "admin", "ENABLED", 500);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.items().getFirst().get("mobile")).isEqualTo("138****8000");
        assertThat(result.items().getFirst().get("email")).isEqualTo("a***@example.com");
        assertThat(repository.requestedLimit).isEqualTo(100);
        assertThat(repository.actor).isSameAs(actor);
    }

    private static final class RecordingUserRepository implements AiIamUserRepository {
        private int requestedLimit;
        private CurrentUser actor;

        @Override
        public UserSearch search(CurrentUser actor, String keyword, String status, int limit) {
            this.actor = actor;
            requestedLimit = limit;
            return new UserSearch(List.of(Map.of(
                    "id", 100L,
                    "username", "admin",
                    "realName", "管理员",
                    "mobile", "13800008000",
                    "email", "admin@example.com",
                    "status", "ENABLED",
                    "createdAt", LocalDateTime.now()
            )), 1L);
        }
    }
}
