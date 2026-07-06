package com.lumira.saas.modules.system.app;

import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.controller.InternalSystemController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemInternalApiServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void localServiceShouldInvokeDelegateWithInternalServiceAuthenticationAndRestoreContext() {
        InternalSystemController delegate = mock(InternalSystemController.class);
        SystemInternalApiService service = new SystemInternalApiService(delegate);
        Authentication previousAuthentication = userAuthentication();
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        SystemUserSnapshotDTO expected = new SystemUserSnapshotDTO(42L, "user-uuid-42", "alice", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null);
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        doAnswer(invocation -> {
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return expected;
        }).when(delegate).findUserIdentityById(42L);

        SystemUserSnapshotDTO actual = service.findUserIdentityById(42L);

        assertThat(actual).isSameAs(expected);
        assertThat(observedAuthentication.get()).isNotNull();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(observedAuthentication.get())).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        verify(delegate).findUserIdentityById(42L);
    }

    @Test
    void userHasEmailShouldInvokeDelegateWithInternalServiceAuthenticationAndRestoreContext() {
        InternalSystemController delegate = mock(InternalSystemController.class);
        SystemInternalApiService service = new SystemInternalApiService(delegate);
        Authentication previousAuthentication = userAuthentication();
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        when(delegate.userHasEmail(42L, "user-uuid-42")).thenAnswer(invocation -> {
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return Boolean.TRUE;
        });

        Boolean actual = service.userHasEmail(42L, "user-uuid-42");

        assertThat(actual).isTrue();
        assertThat(observedAuthentication.get()).isNotNull();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(observedAuthentication.get())).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        verify(delegate).userHasEmail(42L, "user-uuid-42");
    }

    @Test
    void findTargetUserUuidByIdShouldInvokeDelegateWithInternalServiceAuthenticationAndRestoreContext() {
        InternalSystemController delegate = mock(InternalSystemController.class);
        SystemInternalApiService service = new SystemInternalApiService(delegate);
        Authentication previousAuthentication = userAuthentication();
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        when(delegate.findTargetUserUuidById(42L)).thenAnswer(invocation -> {
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return "user-uuid-42";
        });

        String actual = service.findTargetUserUuidById(42L);

        assertThat(actual).isEqualTo("user-uuid-42");
        assertThat(observedAuthentication.get()).isNotNull();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(observedAuthentication.get())).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        verify(delegate).findTargetUserUuidById(42L);
    }

    @Test
    void permissionRoleSnapshotShouldInvokeDelegateWithInternalServiceAuthenticationAndRestoreContext() {
        InternalSystemController delegate = mock(InternalSystemController.class);
        SystemInternalApiService service = new SystemInternalApiService(delegate);
        Authentication previousAuthentication = userAuthentication();
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        PermissionSnapshotDTO expected = new PermissionSnapshotDTO("v11", List.of(), List.of(7L, 8L), null, List.of(), List.of(), List.of(), null);
        when(delegate.permissionRoleSnapshot(42L, "user-uuid-42")).thenAnswer(invocation -> {
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return expected;
        });

        PermissionSnapshotDTO actual = service.permissionRoleSnapshot(42L, "user-uuid-42");

        assertThat(actual).isSameAs(expected);
        assertThat(observedAuthentication.get()).isNotNull();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(observedAuthentication.get())).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        verify(delegate).permissionRoleSnapshot(42L, "user-uuid-42");
    }

    @Test
    void roleUserIdentitiesShouldInvokeDelegateWithInternalServiceAuthenticationAndRestoreContext() {
        InternalSystemController delegate = mock(InternalSystemController.class);
        SystemInternalApiService service = new SystemInternalApiService(delegate);
        Authentication previousAuthentication = userAuthentication();
        SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        List<SystemUserSnapshotDTO> expected = List.of(
                new SystemUserSnapshotDTO(42L, "user-uuid-42", "alice", null, "ENABLED", null, null, null, null, null, null, null, null, null, null, null)
        );
        when(delegate.roleUserIdentities(7L)).thenAnswer(invocation -> {
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return expected;
        });

        List<SystemUserSnapshotDTO> actual = service.roleUserIdentities(7L);

        assertThat(actual).isSameAs(expected);
        assertThat(observedAuthentication.get()).isNotNull();
        assertThat(AuthenticationTrustSupport.isInternalServiceAuthentication(observedAuthentication.get())).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previousAuthentication);
        verify(delegate).roleUserIdentities(7L);
    }

    private Authentication userAuthentication() {
        CurrentUser currentUser = new CurrentUser(1001L, "alice", null, "session-1", 1, true, Set.of("system:user:view"));
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setPermissionsVersion("v1");
        return new UsernamePasswordAuthenticationToken(currentUser, "jwt", Set.of());
    }
}
