package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityRegistrationAppServiceTest {

    private static final Long USER_ID = 2001L;
    private static final String USER_UUID = "user-uuid-2001";

    @Test
    void listShouldKeepFunctionalViewPermissionInsideSelfDataScope() {
        Fixture fixture = fixture(snapshot(
                Set.of("aiadc:activity:view"),
                List.of(new DataPermissionRule("activity:registration", DataScopeType.SELF, List.of(), List.of()))
        ));

        fixture.service().list(user(Set.of("aiadc:activity:view"), List.of()));

        verify(fixture.repository()).listVisible(USER_ID, USER_UUID, false);
    }

    @Test
    void listShouldAllowAllRecordsOnlyWhenActivityRegistrationScopeIsAll() {
        Fixture fixture = fixture(snapshot(
                Set.of("aiadc:activity:view"),
                List.of(new DataPermissionRule("activity:registration", DataScopeType.ALL, List.of(), List.of()))
        ));

        fixture.service().list(user(Set.of("aiadc:activity:view"), List.of()));

        verify(fixture.repository()).listVisible(USER_ID, USER_UUID, true);
    }

    @Test
    void listShouldHonorGlobalAllScopeForActivityRegistrations() {
        Fixture fixture = fixture(snapshot(
                Set.of("aiadc:activity:view"),
                List.of(new DataPermissionRule("*", DataScopeType.ALL, List.of(), List.of()))
        ));

        fixture.service().list(user(Set.of(), List.of()));

        verify(fixture.repository()).listVisible(USER_ID, USER_UUID, true);
    }

    @Test
    void listShouldUseLiveSnapshotInsteadOfStaleCurrentUserScope() {
        Fixture fixture = fixture(snapshot(
                Set.of("aiadc:activity:view"),
                List.of(new DataPermissionRule("activity:registration", DataScopeType.SELF, List.of(), List.of()))
        ));
        CurrentUser staleUser = user(
                Set.of("*"),
                List.of(new DataPermissionRule("activity:registration", DataScopeType.ALL, List.of(), List.of()))
        );

        fixture.service().list(staleUser);

        verify(fixture.repository()).listVisible(USER_ID, USER_UUID, false);
    }

    @Test
    void listShouldDefaultToSelfWhenNoDataScopeMatches() {
        Fixture fixture = fixture(snapshot(Set.of("aiadc:activity:view"), List.of()));

        fixture.service().list(user(Set.of("aiadc:activity:view"), List.of()));

        verify(fixture.repository()).listVisible(USER_ID, USER_UUID, false);
    }

    @Test
    void listShouldRejectInactiveTrustedUserBeforeRepositoryAccess() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(USER_ID, USER_UUID)).thenReturn(false);
        ActivityRegistrationAppService service =
                new ActivityRegistrationAppService(repository, permissionSnapshotService);

        assertThatThrownBy(() -> service.list(user(Set.of(), List.of())))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
        verify(repository, never()).listVisible(USER_ID, USER_UUID, false);
    }

    private Fixture fixture(PermissionSnapshotService.PermissionSnapshot snapshot) {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        PermissionSnapshotService permissionSnapshotService = mock(PermissionSnapshotService.class);
        when(permissionSnapshotService.isTrustedActiveUser(USER_ID, USER_UUID)).thenReturn(true);
        when(permissionSnapshotService.loadSnapshot(USER_ID, USER_UUID)).thenReturn(snapshot);
        return new Fixture(
                new ActivityRegistrationAppService(repository, permissionSnapshotService),
                repository
        );
    }

    private PermissionSnapshotService.PermissionSnapshot snapshot(
            Set<String> permissions,
            List<DataPermissionRule> dataScopes
    ) {
        return new PermissionSnapshotService.PermissionSnapshot(
                "permissions-live",
                permissions,
                Set.of(10L),
                null,
                Set.of(),
                Set.of(),
                dataScopes,
                "/"
        );
    }

    private CurrentUser user(Set<String> permissions, List<DataPermissionRule> dataScopes) {
        CurrentUser user = new CurrentUser(
                USER_ID,
                "alice",
                "session-1",
                1,
                true,
                permissions,
                Set.of(10L),
                null,
                Set.of(),
                Set.of(),
                dataScopes
        );
        user.setUserUuid(USER_UUID);
        user.setPermissionsVersion("permissions-stale");
        return user;
    }

    private record Fixture(
            ActivityRegistrationAppService service,
            ActivityRegistrationRepository repository
    ) {
    }
}
