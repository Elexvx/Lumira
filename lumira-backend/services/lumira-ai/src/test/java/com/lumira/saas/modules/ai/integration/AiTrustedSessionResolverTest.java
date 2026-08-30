package com.lumira.saas.modules.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiTrustedSessionResolverTest {

    @Test
    void resolvesATrustedTicketThroughTheDedicatedSessionTicketPort() {
        TrustedCurrentUserResolver trustedCurrentUserResolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser trusted = new CurrentUser(1001L, "operator", "session-1", 3, true, Set.of("ai:chat:send"));
        trusted.setUserUuid("user-uuid");
        trusted.setPermissionsVersion("permissions-v3");
        when(trustedCurrentUserResolver.resolveSessionTicket(
                "session-1", 1001L, "user-uuid", null, 3, "permissions-v3"
        )).thenReturn(trusted);

        AiTrustedSessionResolver.AuthenticatedAccess access = new AiTrustedSessionResolver(trustedCurrentUserResolver)
                .authenticateSessionTicket("session-1", 1001L, "user-uuid", null, 3, "permissions-v3");

        assertThat(access).isNotNull();
        assertThat(access.currentUser()).isSameAs(trusted);
        verify(trustedCurrentUserResolver).resolveSessionTicket(
                "session-1", 1001L, "user-uuid", null, 3, "permissions-v3"
        );
    }

    @Test
    void doesNotExposeAnUntrustedResolverResultAsAuthenticatedAccess() {
        TrustedCurrentUserResolver trustedCurrentUserResolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser untrusted = new CurrentUser(1001L, null, "session-1", 3, true, Set.of());
        untrusted.setUserUuid("user-uuid");
        untrusted.setPermissionsVersion("permissions-v3");
        when(trustedCurrentUserResolver.resolveSessionTicket(
                "session-1", 1001L, "user-uuid", null, 3, "permissions-v3"
        )).thenReturn(untrusted);

        AiTrustedSessionResolver.AuthenticatedAccess access = new AiTrustedSessionResolver(trustedCurrentUserResolver)
                .authenticateSessionTicket("session-1", 1001L, "user-uuid", null, 3, "permissions-v3");

        assertThat(access).isNull();
    }
}
