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
import com.lumira.saas.modules.activity.model.ActivityRegistrationField;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(repository.findPublishedRegistrationForm(9L)).thenReturn(Optional.of(
                new ActivityRegistrationRepository.RegistrationForm(9L, "Roadshow", List.of())
        ));

        new ActivityRegistrationAppService(repository, resolver).create(requestUser, request);

        verify(repository).create(
                org.mockito.Mockito.eq(USER_ID),
                org.mockito.Mockito.eq(USER_UUID),
                org.mockito.Mockito.eq("live-alice"),
                any(ActivityRegistrationRepository.RegistrationSubmission.class)
        );
    }

    @Test
    void createRejectsMissingRequiredConfiguredFieldBeforeWrite() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        when(repository.findPublishedRegistrationForm(9L)).thenReturn(Optional.of(
                new ActivityRegistrationRepository.RegistrationForm(9L, "Roadshow", List.of(field("name", "姓名", "TEXT", true)))
        ));
        ActivityRegistrationDTO.CreateRequest request = new ActivityRegistrationDTO.CreateRequest();
        request.setActivityId(9L);

        assertThatThrownBy(() -> new ActivityRegistrationAppService(repository).create(user(List.of()), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(repository, never()).create(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void createPersistsSchemaSnapshotAnswersAndCompatibilityColumns() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        ActivityRegistrationField mobile = field("contactMobile", "联系电话", "MOBILE", true);
        ActivityRegistrationField audience = field("audience", "参会类型", "SELECT", true);
        audience.setOptions(List.of("嘉宾", "观众"));
        when(repository.findPublishedRegistrationForm(9L)).thenReturn(Optional.of(
                new ActivityRegistrationRepository.RegistrationForm(9L, "Roadshow", List.of(mobile, audience))
        ));
        ActivityRegistrationDTO.CreateRequest request = new ActivityRegistrationDTO.CreateRequest();
        request.setActivityId(9L);
        request.setAnswers(Map.of("contactMobile", "13800138000", "audience", "嘉宾"));

        new ActivityRegistrationAppService(repository).create(user(List.of()), request);

        ArgumentCaptor<ActivityRegistrationRepository.RegistrationSubmission> submission =
                ArgumentCaptor.forClass(ActivityRegistrationRepository.RegistrationSubmission.class);
        verify(repository).create(
                org.mockito.Mockito.eq(USER_ID),
                org.mockito.Mockito.eq(USER_UUID),
                org.mockito.Mockito.eq("alice"),
                submission.capture()
        );
        org.assertj.core.api.Assertions.assertThat(submission.getValue().mobile()).isEqualTo("13800138000");
        org.assertj.core.api.Assertions.assertThat(submission.getValue().answers())
                .extracting("fieldKey", "label", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("contactMobile", "联系电话", "13800138000"),
                        org.assertj.core.groups.Tuple.tuple("audience", "参会类型", "嘉宾")
                );
    }

    @Test
    void createRejectsChoiceValueOutsideConfiguredOptions() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        ActivityRegistrationField audience = field("audience", "参会类型", "SELECT", true);
        audience.setOptions(List.of("嘉宾", "观众"));
        when(repository.findPublishedRegistrationForm(9L)).thenReturn(Optional.of(
                new ActivityRegistrationRepository.RegistrationForm(9L, "Roadshow", List.of(audience))
        ));
        ActivityRegistrationDTO.CreateRequest request = new ActivityRegistrationDTO.CreateRequest();
        request.setActivityId(9L);
        request.setAnswers(Map.of("audience", "未配置选项"));

        assertThatThrownBy(() -> new ActivityRegistrationAppService(repository).create(user(List.of()), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(repository, never()).create(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void createRejectsNumberWhoseExpandedValueIsTooLarge() {
        ActivityRegistrationRepository repository = mock(ActivityRegistrationRepository.class);
        when(repository.findPublishedRegistrationForm(9L)).thenReturn(Optional.of(
                new ActivityRegistrationRepository.RegistrationForm(
                        9L,
                        "Roadshow",
                        List.of(field("budget", "预算", "NUMBER", true))
                )
        ));
        ActivityRegistrationDTO.CreateRequest request = new ActivityRegistrationDTO.CreateRequest();
        request.setActivityId(9L);
        request.setAnswers(Map.of("budget", "1e1001"));

        assertThatThrownBy(() -> new ActivityRegistrationAppService(repository).create(user(List.of()), request))
                .isInstanceOf(BizException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(repository, never()).create(anyLong(), anyString(), anyString(), any());
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

    private ActivityRegistrationField field(String key, String label, String type, boolean required) {
        ActivityRegistrationField field = new ActivityRegistrationField();
        field.setFieldKey(key);
        field.setLabel(label);
        field.setFieldType(type);
        field.setRequired(required);
        field.setOptions(List.of());
        return field;
    }
}
