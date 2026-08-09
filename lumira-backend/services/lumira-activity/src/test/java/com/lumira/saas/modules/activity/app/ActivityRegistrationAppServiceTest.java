package com.lumira.saas.modules.activity.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivityRegistrationAppServiceTest {

    private static final Long USER_ID = 2001L;
    private static final String USER_UUID = "user-uuid-2001";

    @Test
    void listUsesResolverFreshSelfScopeInsteadOfStaleRequestScope() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser staleRequestUser = user(List.of(new DataPermissionRule("activity:registration", DataScopeType.ALL, List.of(), List.of())));
        CurrentUser resolvedUser = user(List.of(new DataPermissionRule("activity:registration", DataScopeType.SELF, List.of(), List.of())));
        when(resolver.resolve(staleRequestUser)).thenReturn(resolvedUser);

        new ActivityRegistrationAppService(repository, resolver).list(staleRequestUser);

        verify(repository).listVisible(USER_ID, USER_UUID, false);
    }

    @Test
    void listAllowsAllOnlyForCurrentActivityRegistrationScope() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser currentUser = user(List.of(new DataPermissionRule("activity:registration", DataScopeType.ALL, List.of(), List.of())));
        when(resolver.resolve(currentUser)).thenReturn(currentUser);

        new ActivityRegistrationAppService(repository, resolver).list(currentUser);

        verify(repository).listVisible(USER_ID, USER_UUID, true);
    }

    @Test
    void createUsesResolverFreshIdentityAndNeverUsesStaleRequestIdentity() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser requestUser = user(List.of());
        CurrentUser resolvedUser = user(List.of());
        resolvedUser.setUsername("live-alice");
        when(resolver.resolve(requestUser)).thenReturn(resolvedUser);
        ActivityRegistrationDTO.CreateRequest request = new ActivityRegistrationDTO.CreateRequest();
        request.setActivityId(9L);

        new ActivityRegistrationAppService(repository, resolver).create(requestUser, request);

        verify(repository).create(USER_ID, USER_UUID, "live-alice", request);
    }

    @Test
    void strictRuntimeRejectsTrustedUserWhenResolverIsUnavailableBeforeRepositoryAccess() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);

        assertThatThrownBy(() -> new ActivityRegistrationAppService(repository, null, true).list(user(List.of())))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(repository, never()).listVisible(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void resolverMustReturnTrustedCurrentUserBeforeRepositoryAccess() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser currentUser = user(List.of());
        when(resolver.resolve(currentUser)).thenReturn(new CurrentUser());

        assertThatThrownBy(() -> new ActivityRegistrationAppService(repository, resolver).list(currentUser))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(repository, never()).listVisible(anyLong(), anyString(), anyBoolean());
    }

    private CurrentUser user(List<DataPermissionRule> dataScopes) {
        CurrentUser user = new CurrentUser(
                USER_ID,
                "alice",
                "session-1",
                1,
                true,
                Set.of("aiadc:activity:view"),
                Set.of(10L),
                null,
                Set.of(),
                Set.of(),
                dataScopes
        );
        user.setUserUuid(USER_UUID);
        user.setPermissionsVersion("permissions-live");
        return user;
    }
}
