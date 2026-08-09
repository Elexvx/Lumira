package com.lumira.saas.modules.system.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SystemWorkflowAdaptersTest {

    @Test
    void userAccessAdapterRevalidatesTrustedWorkflowUserThroughTheSessionTicket() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser requestUser = trustedUser();
        CurrentUser refreshedUser = trustedUser();
        refreshedUser.setUsername("refreshed-user");
        when(sessionAuthenticationService.authenticateSessionTicket(
                "session-1", 1001L, "user-uuid", 7L, 3, "permissions-v3"))
                .thenReturn(new SessionAuthenticationService.AuthenticatedAccess(refreshedUser, null, false));

        CurrentUser actual = new SystemWorkflowUserAccessAdapter(sessionAuthenticationService, mock(SystemInternalApi.class))
                .refreshTrustedUser(requestUser);

        assertThat(actual).isSameAs(refreshedUser);
        verify(sessionAuthenticationService).authenticateSessionTicket(
                "session-1", 1001L, "user-uuid", 7L, 3, "permissions-v3");
    }

    @Test
    void userAccessAdapterDoesNotResolveAnUntrustedInput() {
        SessionAuthenticationService sessionAuthenticationService = mock(SessionAuthenticationService.class);
        CurrentUser anonymous = new CurrentUser(0L, "anonymous", null, 0, false, Set.of());

        CurrentUser actual = new SystemWorkflowUserAccessAdapter(sessionAuthenticationService, mock(SystemInternalApi.class))
                .refreshTrustedUser(anonymous);

        assertThat(actual).isSameAs(anonymous);
        verifyNoInteractions(sessionAuthenticationService);
    }

    @Test
    void userAccessAdapterResolvesOnlyEnabledUserUuidThroughSystemInternalApi() {
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findTargetUserUuidById(1002L)).thenReturn(" expert-uuid ");
        SystemWorkflowUserAccessAdapter adapter = new SystemWorkflowUserAccessAdapter(
                mock(SessionAuthenticationService.class), systemInternalApi);

        assertThat(adapter.findEnabledUserUuid(1002L)).isEqualTo("expert-uuid");
        assertThat(adapter.findEnabledUserUuid(0L)).isNull();
        verify(systemInternalApi).findTargetUserUuidById(1002L);
    }

    @Test
    void auditAdapterForwardsWorkflowAuditFieldsWithoutChangingTheirMeaning() {
        OperationAuditService auditService = mock(OperationAuditService.class);
        SystemWorkflowAuditAdapter adapter = new SystemWorkflowAuditAdapter(auditService);

        adapter.log(1001L, "user-uuid", "operator", "workflow", "publish", "UPDATE", "SUCCESS", "published");

        verify(auditService).log(
                1001L, "user-uuid", "operator", "workflow", "publish", "UPDATE", "SUCCESS", "published");
    }

    @Test
    void eventAdapterPublishesWorkflowEventThroughTheSystemTransactionalOutbox() {
        PlatformEventPublisher publisher = mock(PlatformEventPublisher.class);
        Map<String, Object> payload = Map.of("userUuid", "user-uuid", "workflowInstanceId", 99L);
        SystemWorkflowEventAdapter adapter = new SystemWorkflowEventAdapter(publisher);

        adapter.record("EXPERT_APPROVED", 1001L, "aiadc_expert", 501L, payload);

        verify(publisher).record(
                PlatformEventTypes.SOURCE_SYSTEM,
                "EXPERT_APPROVED",
                1001L,
                "aiadc_expert",
                501L,
                payload
        );
    }

    private static CurrentUser trustedUser() {
        CurrentUser user = new CurrentUser(1001L, "user", "session-1", 3, true, Set.of("workflow:view"));
        user.setUserUuid("user-uuid");
        user.setSimulatedRoleId(7L);
        user.setPermissionsVersion("permissions-v3");
        return user;
    }
}
