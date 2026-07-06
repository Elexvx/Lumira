package com.lumira.localization.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.localization.dto.LocalizationDTO;
import com.lumira.localization.dto.LocalizationQueryModels.EntryQuery;
import com.lumira.localization.mapper.LocalizationEntryMapper;
import com.lumira.localization.mapper.LocalizationLanguageMapper;
import com.lumira.localization.mapper.LocalizationManagementMapper;
import com.lumira.localization.mapper.LocalizationNamespaceMapper;
import com.lumira.localization.mapper.LocalizationReleaseMapper;
import com.lumira.localization.mapper.LocalizationTranslationMapper;
import com.lumira.localization.mapper.LocalizationUsageRefMapper;
import com.lumira.localization.vo.LocalizationVO;
import com.lumira.localization.entity.LocalizationEntities.EntryEntity;
import com.lumira.localization.entity.LocalizationEntities.ReleaseEntity;
import com.lumira.localization.entity.LocalizationEntities.LanguageEntity;
import com.lumira.localization.entity.LocalizationEntities.NamespaceEntity;
import com.lumira.localization.entity.LocalizationEntities.TranslationEntity;
import com.lumira.localization.entity.LocalizationEntities.UsageRefEntity;
import com.lumira.localization.dto.LocalizationQueryModels.LanguageStatRow;
import com.lumira.localization.dto.LocalizationQueryModels.NamespaceStatRow;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

class LocalizationManagementAppServiceTest {

    private final LocalizationLanguageMapper languageMapper = mock(LocalizationLanguageMapper.class);
    private final LocalizationNamespaceMapper namespaceMapper = mock(LocalizationNamespaceMapper.class);
    private final LocalizationEntryMapper entryMapper = mock(LocalizationEntryMapper.class);
    private final LocalizationTranslationMapper translationMapper = mock(LocalizationTranslationMapper.class);
    private final LocalizationUsageRefMapper usageRefMapper = mock(LocalizationUsageRefMapper.class);
    private final LocalizationReleaseMapper releaseMapper = mock(LocalizationReleaseMapper.class);
    private final LocalizationManagementMapper localizationManagementMapper = mock(LocalizationManagementMapper.class);
    private final SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
    private final LocalizationManagementAppService service = new LocalizationManagementAppService(
            languageMapper,
            namespaceMapper,
            entryMapper,
            translationMapper,
            usageRefMapper,
            releaseMapper,
            localizationManagementMapper,
            new ObjectMapper(),
            systemInternalApi
    );

    @Test
    void listEntries_shouldCapTotalCountAndReportHasMore() {
        LocalizationVO.EntryVO entryVO = new LocalizationVO.EntryVO();
        entryVO.setId(1L);
        when(localizationManagementMapper.listEntries(any())).thenReturn(List.of(entryVO));
        when(localizationManagementMapper.countEntries(any())).thenReturn(250L);
        when(translationMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        LocalizationVO.EntryPageResponse page = service.listEntries("zh-CN", null, null, null, null, 2L, 20L, "updatedAt", "desc");

        ArgumentCaptor<EntryQuery> queryCaptor = ArgumentCaptor.forClass(EntryQuery.class);
        verify(localizationManagementMapper).countEntries(queryCaptor.capture());
        EntryQuery query = queryCaptor.getValue();

        assertThat(query.getOffset()).isEqualTo(20L);
        assertThat(query.getLimit()).isEqualTo(20L);
        assertThat(query.getCountLimit()).isEqualTo(21L + 20L);
        assertThat(page.getHasMore()).isTrue();
        assertThat(page.getTotal()).isEqualTo(41L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getPageSize()).isEqualTo(20L);
        assertThat(page.getTotalCapped()).isTrue();
    }

    @Test
    void listEntries_shouldKeepExactTotalWhenWithinCountCap() {
        LocalizationVO.EntryVO entryVO = new LocalizationVO.EntryVO();
        entryVO.setId(2L);
        when(localizationManagementMapper.listEntries(any())).thenReturn(List.of(entryVO));
        when(localizationManagementMapper.countEntries(any())).thenReturn(10L);
        when(translationMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        LocalizationVO.EntryPageResponse page = service.listEntries("en-US", "common", "ok", "ENABLED", "PENDING", 1L, 20L, "updatedAt", "ascend");

        assertThat(page.getHasMore()).isFalse();
        assertThat(page.getTotal()).isEqualTo(10L);
        assertThat(page.getTotalCapped()).isFalse();
    }

    @Test
    void listLanguages_shouldUseBatchStatsForCoverageAndPublishMetadata() {
        LanguageEntity zh = new LanguageEntity();
        zh.id = 1L;
        zh.localeCode = "zh-CN";
        zh.languageName = "Chinese";
        zh.sortNo = 1;
        zh.isDefault = 1;
        zh.deleted = 0;

        LanguageEntity en = new LanguageEntity();
        en.id = 2L;
        en.localeCode = "en-US";
        en.languageName = "English";
        en.sortNo = 2;
        en.deleted = 0;

        LanguageStatRow zhStat = new LanguageStatRow();
        zhStat.setLocaleCode("zh-CN");
        zhStat.setTranslatedCount(8L);
        zhStat.setPublishedVersion(3L);

        LanguageStatRow enStat = new LanguageStatRow();
        enStat.setLocaleCode("en-US");
        enStat.setTranslatedCount(4L);
        enStat.setPublishedVersion(2L);

        when(languageMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(zh, en));
        when(localizationManagementMapper.listLanguageStats()).thenReturn(List.of(zhStat, enStat));
        when(entryMapper.selectCount(any(QueryWrapper.class))).thenReturn(10L);

        List<LocalizationVO.LanguageVO> languages = service.listLanguages();

        assertThat(languages).hasSize(2);
        assertThat(languages.get(0).getLocaleCode()).isEqualTo("zh-CN");
        assertThat(languages.get(0).getTranslatedCount()).isEqualTo(8L);
        assertThat(languages.get(0).getPublishedVersion()).isEqualTo(3L);
        assertThat(languages.get(1).getLocaleCode()).isEqualTo("en-US");
        assertThat(languages.get(1).getTranslatedCount()).isEqualTo(4L);
        assertThat(languages.get(1).getPublishedVersion()).isEqualTo(2L);

        verify(localizationManagementMapper).listLanguageStats();
        verify(localizationManagementMapper, never()).countTranslatedEntries(any());
    }

    @Test
    void listNamespaces_shouldUseBatchStatsForCoverage() {
        NamespaceEntity common = new NamespaceEntity();
        common.id = 10L;
        common.namespaceCode = "common";
        common.namespaceName = "Common";
        common.sortNo = 1;
        common.deleted = 0;

        NamespaceEntity nav = new NamespaceEntity();
        nav.id = 11L;
        nav.namespaceCode = "nav";
        nav.namespaceName = "Nav";
        nav.sortNo = 2;
        nav.deleted = 0;

        NamespaceStatRow commonStat = new NamespaceStatRow();
        commonStat.setNamespaceCode("common");
        commonStat.setEntryCount(12L);
        commonStat.setTranslatedCount(9L);

        NamespaceStatRow navStat = new NamespaceStatRow();
        navStat.setNamespaceCode("nav");
        navStat.setEntryCount(5L);
        navStat.setTranslatedCount(2L);

        when(namespaceMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(common, nav));
        when(localizationManagementMapper.listNamespaceStats("zh-CN")).thenReturn(List.of(commonStat, navStat));

        List<LocalizationVO.NamespaceVO> namespaces = service.listNamespaces("zh-CN");

        assertThat(namespaces).hasSize(2);
        assertThat(namespaces.get(0).getNamespaceCode()).isEqualTo("common");
        assertThat(namespaces.get(0).getEntryCount()).isEqualTo(12L);
        assertThat(namespaces.get(0).getTranslatedCount()).isEqualTo(9L);
        assertThat(namespaces.get(1).getNamespaceCode()).isEqualTo("nav");
        assertThat(namespaces.get(1).getEntryCount()).isEqualTo(5L);
        assertThat(namespaces.get(1).getTranslatedCount()).isEqualTo(2L);

        verify(localizationManagementMapper).listNamespaceStats("zh-CN");
        verify(localizationManagementMapper, never()).countEntriesByNamespace(any());
        verify(localizationManagementMapper, never()).countTranslatedEntriesByNamespace(any(), any());
    }

    @Test
    void snapshotMetrics_shouldExposeCacheCounters() {
        LocalizationManagementAppService.MetricsSnapshot snapshot = service.snapshotMetrics();

        assertThat(snapshot.runtimeBundleCacheSize()).isZero();
        assertThat(snapshot.runtimeBundleCacheHits()).isZero();
        assertThat(snapshot.runtimeBundleCacheMisses()).isZero();
        assertThat(snapshot.runtimeBundleCacheHitRatio()).isZero();
        assertThat(snapshot.readModelVersionCacheHits()).isZero();
        assertThat(snapshot.readModelVersionCacheMisses()).isZero();
        assertThat(snapshot.readModelVersionCacheFallbacks()).isZero();
        assertThat(snapshot.readModelVersionCacheHitRatio()).isZero();
    }

    @Test
    void publish_shouldRejectUnauthenticatedUserBeforeReleaseWrite() {
        LocalizationDTO.PublishRequest request = new LocalizationDTO.PublishRequest();
        request.setLocaleCode("zh-CN");
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, false, Set.of("localization:publish"));

        assertThatThrownBy(() -> service.publish(request, currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(releaseMapper, never()).update(any(), any(UpdateWrapper.class));
        verify(releaseMapper, never()).insert(any(ReleaseEntity.class));
    }

    @Test
    void publish_shouldRejectBlankUsernameBeforeReleaseWrite() {
        LocalizationDTO.PublishRequest request = new LocalizationDTO.PublishRequest();
        request.setLocaleCode("zh-CN");
        CurrentUser currentUser = new CurrentUser(100L, " ", null, "session-1", 1, true, Set.of("localization:publish"));

        assertThatThrownBy(() -> service.publish(request, currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(releaseMapper, never()).update(any(), any(UpdateWrapper.class));
        verify(releaseMapper, never()).insert(any(ReleaseEntity.class));
    }

    @Test
    void publish_shouldRejectMissingSessionVersionBeforeReleaseWrite() {
        LocalizationDTO.PublishRequest request = new LocalizationDTO.PublishRequest();
        request.setLocaleCode("zh-CN");
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", null, true, Set.of("localization:publish"));

        assertThatThrownBy(() -> service.publish(request, currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(releaseMapper, never()).update(any(), any(UpdateWrapper.class));
        verify(releaseMapper, never()).insert(any(ReleaseEntity.class));
    }

    @Test
    void publish_shouldRejectMissingUserUuidBeforeReleaseWrite() {
        LocalizationDTO.PublishRequest request = new LocalizationDTO.PublishRequest();
        request.setLocaleCode("zh-CN");
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("localization:publish"));
        currentUser.setPermissionsVersion("permissions-1");

        assertThatThrownBy(() -> service.publish(request, currentUser))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(releaseMapper, never()).update(any(), any(UpdateWrapper.class));
        verify(releaseMapper, never()).insert(any(ReleaseEntity.class));
    }

    @Test
    void saveLanguage_shouldRejectUnauthenticatedUserBeforeAuditWrite() {
        LocalizationDTO.LanguageUpsertRequest request = new LocalizationDTO.LanguageUpsertRequest();
        request.setLocaleCode("en-US");
        request.setLanguageName("English");
        request.setDefaultLanguage(false);
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, false, Set.of("localization:update"));

        assertThatThrownBy(() -> service.saveLanguage(currentUser, null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(languageMapper, never()).insert(any(LanguageEntity.class));
        verify(languageMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void saveLanguage_shouldRejectMissingPermissionsVersionBeforeAuditWrite() {
        LocalizationDTO.LanguageUpsertRequest request = new LocalizationDTO.LanguageUpsertRequest();
        request.setLocaleCode("en-US");
        request.setLanguageName("English");
        request.setDefaultLanguage(false);
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of("localization:update"));
        currentUser.setUserUuid("user-uuid-100");

        assertThatThrownBy(() -> service.saveLanguage(currentUser, null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(languageMapper, never()).insert(any(LanguageEntity.class));
        verify(languageMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void saveLanguage_shouldAuditWithAuthenticatedOperator() {
        LocalizationDTO.LanguageUpsertRequest request = new LocalizationDTO.LanguageUpsertRequest();
        request.setLocaleCode("en-US");
        request.setLanguageName("English");
        request.setNativeName("English");
        request.setDefaultLanguage(false);
        request.setStatus("ENABLED");
        CurrentUser currentUser = trustedUser("localization:update");
        AtomicReference<LanguageEntity> inserted = new AtomicReference<>();
        when(languageMapper.selectOne(any(QueryWrapper.class))).thenAnswer(invocation -> inserted.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            LanguageEntity entity = invocation.getArgument(0);
            entity.id = 10L;
            inserted.set(entity);
            return 1;
        }).when(languageMapper).insert(any(LanguageEntity.class));

        LocalizationVO.LanguageVO saved = service.saveLanguage(currentUser, null, request);

        assertThat(saved.getLocaleCode()).isEqualTo("en-US");
        assertThat(inserted.get().createdBy).isEqualTo(100L);
        assertThat(inserted.get().createdByUuid).isEqualTo("user-uuid-100");
        assertThat(inserted.get().updatedBy).isEqualTo(100L);
        assertThat(inserted.get().updatedByUuid).isEqualTo("user-uuid-100");
    }

    @Test
    void saveLanguage_shouldRejectDisabledTrustedUserBeforeAuditWrite() {
        LocalizationDTO.LanguageUpsertRequest request = new LocalizationDTO.LanguageUpsertRequest();
        request.setLocaleCode("en-US");
        request.setLanguageName("English");
        request.setDefaultLanguage(false);
        CurrentUser currentUser = trustedUser("localization:update");
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "alice", "DISABLED"));

        assertThatThrownBy(() -> service.saveLanguage(currentUser, null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(languageMapper, never()).insert(any(LanguageEntity.class));
        verify(languageMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void saveLanguage_shouldRejectRevokedTrustedPermissionBeforeAuditWrite() {
        LocalizationDTO.LanguageUpsertRequest request = new LocalizationDTO.LanguageUpsertRequest();
        request.setLocaleCode("en-US");
        request.setLanguageName("English");
        request.setDefaultLanguage(false);
        CurrentUser currentUser = trustedUser("localization:update");
        when(systemInternalApi.permissionSnapshot(100L, "user-uuid-100"))
                .thenReturn(permissionSnapshot(List.of("localization:view")));

        assertThatThrownBy(() -> service.saveLanguage(currentUser, null, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(languageMapper, never()).insert(any(LanguageEntity.class));
        verify(languageMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void deleteEntry_shouldConstrainCascadeDeletesToActiveParentEntry() {
        CurrentUser currentUser = trustedUser();
        when(entryMapper.update(any(), any())).thenReturn(1);

        service.deleteEntry(currentUser, 42L);

        ArgumentCaptor<UpdateWrapper<EntryEntity>> entryCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        ArgumentCaptor<UpdateWrapper<TranslationEntity>> translationCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        ArgumentCaptor<UpdateWrapper<UsageRefEntity>> usageCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(entryMapper).update(any(), entryCaptor.capture());
        verify(translationMapper).update(any(), translationCaptor.capture());
        verify(usageRefMapper).update(any(), usageCaptor.capture());

        assertThat(entryCaptor.getValue().getSqlSegment()).contains("id", "deleted");
        assertThat(translationCaptor.getValue().getSqlSegment())
                .contains("entry_id", "deleted", "sys_localization_entry", "e.deleted = 1");
        assertThat(usageCaptor.getValue().getSqlSegment())
                .contains("entry_id", "deleted", "sys_localization_entry", "e.deleted = 1");
    }

    @Test
    void deleteEntry_shouldNotCascadeWhenParentDeleteMisses() {
        CurrentUser currentUser = trustedUser();
        when(entryMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.deleteEntry(currentUser, 42L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(translationMapper, never()).update(any(), any());
        verify(usageRefMapper, never()).update(any(), any());
    }

    @Test
    void deleteLanguageAndNamespace_shouldOnlyDeleteActiveRows() {
        CurrentUser currentUser = trustedUser();
        when(languageMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(namespaceMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        service.deleteLanguage(currentUser, 10L);
        service.deleteNamespace(currentUser, 20L);

        ArgumentCaptor<UpdateWrapper<LanguageEntity>> languageCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        ArgumentCaptor<UpdateWrapper<NamespaceEntity>> namespaceCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(languageMapper).update(any(), languageCaptor.capture());
        verify(namespaceMapper).update(any(), namespaceCaptor.capture());

        assertThat(languageCaptor.getValue().getSqlSegment()).contains("id", "deleted");
        assertThat(namespaceCaptor.getValue().getSqlSegment()).contains("id", "deleted");
    }

    @Test
    void deleteLanguage_shouldRejectWhenTargetWriteMisses() {
        CurrentUser currentUser = trustedUser();
        when(languageMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.deleteLanguage(currentUser, 10L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void deleteNamespace_shouldRejectWhenTargetWriteMisses() {
        CurrentUser currentUser = trustedUser();
        when(namespaceMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.deleteNamespace(currentUser, 20L))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void rollback_shouldBindReleaseBoundaryWhenReactivatingVersion() {
        CurrentUser currentUser = trustedUser("localization:rollback");
        LocalizationDTO.RollbackRequest request = new LocalizationDTO.RollbackRequest();
        request.setReleaseId(77L);
        ReleaseEntity release = new ReleaseEntity();
        release.id = 77L;
        release.localeCode = "zh-CN";
        release.releaseVersion = 12L;
        release.activeFlag = 0;
        release.deleted = 0;
        when(releaseMapper.selectOne(any(QueryWrapper.class))).thenReturn(release);
        when(releaseMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        service.rollback(request, currentUser);

        ArgumentCaptor<UpdateWrapper<ReleaseEntity>> releaseCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(releaseMapper, org.mockito.Mockito.times(2)).update(any(), releaseCaptor.capture());
        assertThat(releaseCaptor.getAllValues().get(0).getSqlSegment())
                .contains("locale_code", "active_flag", "deleted");
        assertThat(releaseCaptor.getAllValues().get(1).getSqlSegment())
                .contains("id", "locale_code", "release_version", "active_flag", "deleted");
    }

    @Test
    void saveEntry_shouldBindTranslationAndUsageBusinessBoundaryOnUpdate() {
        CurrentUser currentUser = trustedUser();
        LocalizationDTO.EntryUpsertRequest request = new LocalizationDTO.EntryUpsertRequest();
        request.setId(42L);
        request.setNamespaceCode("common");
        request.setMessageKey("button.save");
        request.setDefaultMessage("Save");
        request.setSourceLocale("en-US");
        request.setSourceType("UI");
        request.setSourceRef("settings.save");
        request.setStatus("ENABLED");
        request.setTranslations(Map.of("zh-CN", "保存"));

        NamespaceEntity namespace = new NamespaceEntity();
        namespace.id = 9L;
        namespace.namespaceCode = "common";
        namespace.namespaceName = "Common";
        namespace.deleted = 0;
        when(namespaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(namespace);
        TranslationEntity translation = new TranslationEntity();
        translation.id = 88L;
        when(translationMapper.selectOne(any(QueryWrapper.class))).thenReturn(translation);
        UsageRefEntity usageRef = new UsageRefEntity();
        usageRef.id = 99L;
        when(usageRefMapper.selectOne(any(QueryWrapper.class))).thenReturn(usageRef);
        when(namespaceMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(entryMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(translationMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(usageRefMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        LocalizationVO.EntryVO saved = new LocalizationVO.EntryVO();
        saved.setId(42L);
        when(localizationManagementMapper.findEntry(any(), any(), any())).thenReturn(saved);

        service.saveEntry(currentUser, request);

        ArgumentCaptor<UpdateWrapper<TranslationEntity>> translationCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        ArgumentCaptor<UpdateWrapper<UsageRefEntity>> usageCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(translationMapper).update(any(), translationCaptor.capture());
        verify(usageRefMapper).update(any(), usageCaptor.capture());
        assertThat(translationCaptor.getValue().getSqlSegment())
                .contains("id", "entry_id", "locale_code", "deleted");
        assertThat(usageCaptor.getValue().getSqlSegment())
                .contains("id", "entry_id", "source_type", "source_ref", "source_line", "deleted");
    }

    @Test
    void saveEntry_shouldRejectWhenEntryWriteMisses() {
        CurrentUser currentUser = trustedUser();
        LocalizationDTO.EntryUpsertRequest request = entryUpdateRequest();
        NamespaceEntity namespace = new NamespaceEntity();
        namespace.id = 9L;
        namespace.namespaceCode = "common";
        namespace.namespaceName = "Common";
        namespace.deleted = 0;
        when(namespaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(namespace);
        when(namespaceMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(entryMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.saveEntry(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    @Test
    void saveEntry_shouldRejectWhenTranslationWriteMisses() {
        CurrentUser currentUser = trustedUser();
        LocalizationDTO.EntryUpsertRequest request = entryUpdateRequest();
        NamespaceEntity namespace = new NamespaceEntity();
        namespace.id = 9L;
        namespace.namespaceCode = "common";
        namespace.namespaceName = "Common";
        namespace.deleted = 0;
        when(namespaceMapper.selectOne(any(QueryWrapper.class))).thenReturn(namespace);
        TranslationEntity translation = new TranslationEntity();
        translation.id = 88L;
        when(translationMapper.selectOne(any(QueryWrapper.class))).thenReturn(translation);
        when(namespaceMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(entryMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(translationMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.saveEntry(currentUser, request))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR));
    }

    private LocalizationDTO.EntryUpsertRequest entryUpdateRequest() {
        LocalizationDTO.EntryUpsertRequest request = new LocalizationDTO.EntryUpsertRequest();
        request.setId(42L);
        request.setNamespaceCode("common");
        request.setMessageKey("button.save");
        request.setDefaultMessage("Save");
        request.setSourceLocale("en-US");
        request.setSourceType("UI");
        request.setSourceRef("settings.save");
        request.setStatus("ENABLED");
        request.setTranslations(Map.of("zh-CN", "淇濆瓨"));
        return request;
    }

    private CurrentUser trustedUser(String... permissions) {
        CurrentUser currentUser = new CurrentUser(100L, "alice", null, "session-1", 1, true, Set.of(permissions));
        currentUser.setUserUuid("user-uuid-100");
        currentUser.setPermissionsVersion("permissions-1");
        when(systemInternalApi.findUserIdentityById(100L)).thenReturn(userSnapshot(100L, "alice", "ENABLED"));
        when(systemInternalApi.permissionSnapshot(100L, "user-uuid-100"))
                .thenReturn(permissionSnapshot(List.of(permissions)));
        return currentUser;
    }

    private CurrentUser trustedUser() {
        return trustedUser("localization:update");
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String username, String status) {
        return new SystemUserSnapshotDTO(userId, "user-uuid-" + userId, username, null, status, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PermissionSnapshotDTO permissionSnapshot(List<String> permissions) {
        return new PermissionSnapshotDTO(
                "perm-v100",
                permissions,
                List.of(31L),
                41L,
                List.of(41L),
                List.of(41L, 42L),
                List.of(),
                "/localization"
        );
    }
}
