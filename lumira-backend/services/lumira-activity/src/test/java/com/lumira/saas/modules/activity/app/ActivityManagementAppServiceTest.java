package com.lumira.saas.modules.activity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.lumira.api.event.EventCatalogEventTypes;
import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.repository.ActivityRepository;
import com.lumira.saas.modules.activity.vo.ActivityPageResponse;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActivityManagementAppServiceTest {

    @Test
    void createRequiresActivityCreatePermissionBeforeRepositoryAccess() {
        ActivityRepository repository = mock(ActivityRepository.class);
        ActivityManagementAppService service = new ActivityManagementAppService(repository);

        assertThatThrownBy(() -> service.createActivity(user(Set.of("aiadc:activity:view")), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void strictRuntimeRejectsTrustedUserWhenResolverIsUnavailableBeforeRepositoryAccess() {
        ActivityRepository repository = mock(ActivityRepository.class);
        ActivityManagementAppService service = new ActivityManagementAppService(repository, null, true);

        assertThatThrownBy(() -> service.createActivity(user(Set.of("aiadc:activity:create")), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void createUsesResolverFreshPermissionsAndIdentityForAuditWrite() {
        ActivityRepository repository = activityRepositoryWithDictionaries();
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser staleRequestUser = user(Set.of("aiadc:activity:view"));
        CurrentUser liveUser = user(Set.of("aiadc:activity:create", "aiadc:activity:view"));
        liveUser.setUserUuid("live-user-uuid");
        liveUser.setUsername("live-alice");
        when(resolver.resolve(staleRequestUser)).thenReturn(liveUser);
        when(resolver.resolve(liveUser)).thenReturn(liveUser);
        when(repository.create(any(), anyLong(), anyString())).thenReturn(11L);
        when(repository.findById(11L)).thenReturn(Optional.of(activity(11L)));

        ActivityVO.Activity created = new ActivityManagementAppService(repository, resolver)
                .createActivity(staleRequestUser, request());

        ArgumentCaptor<ActivityDTO.ActivityUpsertRequest> saved = ArgumentCaptor.forClass(ActivityDTO.ActivityUpsertRequest.class);
        verify(repository).create(saved.capture(), org.mockito.Mockito.eq(1001L), org.mockito.Mockito.eq("live-user-uuid"));
        assertThat(saved.getValue().getStatus()).isEqualTo("published");
        assertThat(saved.getValue().getLocale()).isEqualTo("zh");
        assertThat(created.getId()).isEqualTo(11L);
    }

    @Test
    void publishedCreateRecordsCatalogOutboxInsideOwnerApplicationTransaction() {
        ActivityRepository repository = activityRepositoryWithDictionaries();
        TransactionalEventOutboxPort outbox = mock(TransactionalEventOutboxPort.class);
        ActivityVO.Activity published = activity(11L);
        published.setCode("act-11");
        published.setStatus("published");
        when(repository.create(any(), anyLong(), anyString())).thenReturn(11L);
        when(repository.findById(11L)).thenReturn(Optional.of(published));

        new ActivityManagementAppService(repository, null, false, outbox)
                .createActivity(user(Set.of("aiadc:activity:create", "aiadc:activity:view")), request());

        ArgumentCaptor<java.util.Map<String, Object>> attributes = ArgumentCaptor.forClass(java.util.Map.class);
        verify(outbox).record(
                org.mockito.Mockito.eq(EventCatalogEventTypes.CATALOG_ITEM_UPSERTED),
                org.mockito.Mockito.eq(1001L),
                org.mockito.Mockito.eq("event-catalog.item"),
                org.mockito.Mockito.eq(11L),
                attributes.capture()
        );
        assertThat(attributes.getValue())
                .containsEntry("sourceType", "ACTIVITY")
                .containsEntry("sourceId", 11L)
                .containsEntry("userUuid", "user-uuid-1001");
    }

    @Test
    void failedOwnerWriteDoesNotRecordCatalogOutbox() {
        ActivityRepository repository = activityRepositoryWithDictionaries();
        TransactionalEventOutboxPort outbox = mock(TransactionalEventOutboxPort.class);
        when(repository.create(any(), anyLong(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> new ActivityManagementAppService(repository, null, false, outbox)
                .createActivity(user(Set.of("aiadc:activity:create")), request()))
                .isInstanceOf(BizException.class);

        verify(outbox, never()).record(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void activityWritesRejectUnsafeUrlBeforeRepositoryAccess() {
        ActivityRepository repository = activityRepositoryWithDictionaries();
        ActivityDTO.ActivityUpsertRequest unsafe = request();
        unsafe.setCtaHref("javascript:alert(1)");

        assertThatThrownBy(() -> new ActivityManagementAppService(repository)
                .createActivity(user(Set.of("aiadc:activity:create")), unsafe))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(repository, never()).create(any(), any(), any());
    }

    @Test
    void publicCardsRetainPaginationAndRedactManagementFields() {
        ActivityRepository repository = activityRepositoryWithDictionaries();
        ActivityVO.Activity row = activity(41L);
        row.setCode("internal-code");
        row.setStatus("published");
        row.setSort(7);
        when(repository.search(any(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(new ActivityRepository.PageData(List.of(row), 12L));

        ActivityPageResponse<ActivityVO.PublicActivity> page = new ActivityManagementAppService(repository)
                .listPublishedActivities(null, "zh", null, 2L, 10L);

        assertThat(page.getTotal()).isEqualTo(12L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getHasMore()).isFalse();
        assertThat(page.getRecords()).singleElement().satisfies(publicActivity -> {
            assertThat(publicActivity.getId()).isEqualTo(41L);
            assertThat(publicActivity.getTitle()).isEqualTo("Roadshow");
        });
    }

    private ActivityRepository activityRepositoryWithDictionaries() {
        ActivityRepository repository = mock(ActivityRepository.class);
        when(repository.findEnabledDictValues("aiadc_activity_locale")).thenReturn(List.of("zh", "en"));
        when(repository.findEnabledDictValues("aiadc_activity_status")).thenReturn(List.of("draft", "published"));
        when(repository.findEnabledDictValues("aiadc_activity_public_status")).thenReturn(List.of("published"));
        return repository;
    }

    private CurrentUser user(Set<String> permissions) {
        CurrentUser user = new CurrentUser(1001L, "alice", "session-1", 1, true, permissions);
        user.setUserUuid("user-uuid-1001");
        user.setPermissionsVersion("permissions-1");
        return user;
    }

    private ActivityDTO.ActivityUpsertRequest request() {
        ActivityDTO.ActivityUpsertRequest request = new ActivityDTO.ActivityUpsertRequest();
        request.setTitle("Roadshow");
        request.setActivityDate("2026-08-08");
        request.setActivityTime("10:00");
        request.setLocation("Shanghai");
        request.setStatus("published");
        request.setLocale("zh");
        return request;
    }

    private ActivityVO.Activity activity(Long id) {
        ActivityVO.Activity activity = new ActivityVO.Activity();
        activity.setId(id);
        activity.setLocale("zh");
        activity.setTitle("Roadshow");
        activity.setActivityDate("2026-08-08");
        activity.setActivityTime("10:00");
        activity.setLocation("Shanghai");
        return activity;
    }
}
