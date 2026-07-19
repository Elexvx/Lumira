package com.lumira.saas.modules.draft.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.draft.repository.UserDraftRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserDraftAppServiceTest {
    @Test
    void savesAndReadsPlainJsonCompatiblePayloads() {
        UserDraftRepository repository = mock(UserDraftRepository.class);
        when(repository.find(eq(1001L), eq("user-uuid-1001"), eq("registration.draft")))
                .thenReturn(Optional.of(new UserDraftRepository.UserDraft(
                        "{\"currentStep\":2,\"values\":{\"competitionId\":15}}",
                        LocalDateTime.now()
                )));
        UserDraftAppService service = new UserDraftAppService(repository, new ObjectMapper());

        UserDraftAppService.Draft draft = service.save(
                authenticatedUser(),
                "registration.draft",
                Map.of("currentStep", 2, "values", Map.of("competitionId", 15))
        );

        assertThat(draft.payload()).isEqualTo(Map.of(
                "currentStep", 2,
                "values", Map.of("competitionId", 15)
        ));
    }

    private static CurrentUser authenticatedUser() {
        CurrentUser user = new CurrentUser();
        user.setUserId(1001L);
        user.setUserUuid("user-uuid-1001");
        user.setAuthenticated(true);
        return user;
    }
}
