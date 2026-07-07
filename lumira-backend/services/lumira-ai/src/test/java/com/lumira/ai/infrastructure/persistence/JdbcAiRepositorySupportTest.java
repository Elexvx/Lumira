package com.lumira.ai.infrastructure.persistence;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JdbcAiRepositorySupportTest {

    @Test
    void requireTrustedUserIdShouldRejectMissingUserId() {
        TestRepositorySupport support = new TestRepositorySupport();
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setUserId(null);

        assertThatThrownBy(() -> support.exposeTrustedUserId(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void requireTrustedUserUuidShouldRejectBlankUserUuid() {
        TestRepositorySupport support = new TestRepositorySupport();
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setUserUuid(" ");

        assertThatThrownBy(() -> support.exposeTrustedUserUuid(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void hasAllPermissionShouldReturnFalseWhenPermissionsAreMissing() {
        TestRepositorySupport support = new TestRepositorySupport();
        CurrentUser currentUser = trustedCurrentUser();
        currentUser.setPermissions(null);

        assertThat(support.exposeHasAllPermission(currentUser)).isFalse();
    }

    private CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("alice");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(Set.of("*"));
        return currentUser;
    }

    private static final class TestRepositorySupport extends JdbcAiRepositorySupport {

        private TestRepositorySupport() {
            super(mock(JdbcTemplate.class));
        }

        private Long exposeTrustedUserId(CurrentUser currentUser) {
            return requireTrustedUserId(currentUser);
        }

        private String exposeTrustedUserUuid(CurrentUser currentUser) {
            return requireTrustedUserUuid(currentUser);
        }

        private boolean exposeHasAllPermission(CurrentUser currentUser) {
            return hasAllPermission(currentUser);
        }
    }
}
