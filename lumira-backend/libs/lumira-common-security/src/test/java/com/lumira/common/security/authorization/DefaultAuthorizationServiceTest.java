package com.lumira.common.security.authorization;

import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthorizationServiceTest {

    private final DefaultAuthorizationService authorizationService = new DefaultAuthorizationService();

    @Test
    void allowsLegacyPermissionMatch() {
        AuthorizationDecision decision = authorizationService.evaluate(
                AuthorizationRequest.permission(currentUser(1001L, "system:user:view"), "system:user:view")
        );

        assertThat(decision.verdict()).isEqualTo(AuthorizationVerdict.ALLOW);
    }

    @Test
    void deniesBlankPermissionKey() {
        assertThatThrownBy(() -> authorizationService.require(AuthorizationRequest.permission(currentUser(1001L, "*"), "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Permission key is required");
    }

    @Test
    void deniesTenantMismatch() {
        AuthorizationRequest request = new AuthorizationRequest(
                2002L,
                SubjectRef.humanUser(2002L, 100L),
                null,
                100L,
                null,
                null,
                null,
                "system:user:view",
                null,
                "LOW",
                null,
                java.util.Map.of(),
                false,
                false,
                "WEB",
                null,
                null,
                currentUser(1001L, "system:user:view")
        );

        assertThatThrownBy(() -> authorizationService.require(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Tenant context does not match");
    }

    @Test
    void requireAllowsAuthorizedRequest() {
        assertThatCode(() -> authorizationService.require(
                AuthorizationRequest.permission(currentUser(1001L, "*"), "system:user:view")
        )).doesNotThrowAnyException();
    }

    private CurrentUser currentUser(Long tenantId, String permission) {
        return new CurrentUser(100L, "admin", tenantId, "session-1", 1, true, Set.of(permission));
    }
}
