package com.lumira.saas.modules.localization.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.EntryQuery;
import com.lumira.saas.modules.localization.mapper.LocalizationEntryMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationLanguageMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationManagementMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationNamespaceMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationReleaseMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationTranslationMapper;
import com.lumira.saas.modules.localization.mapper.LocalizationUsageRefMapper;
import com.lumira.saas.modules.localization.vo.LocalizationVO;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.LanguageEntity;
import com.lumira.saas.modules.localization.entity.LocalizationEntities.NamespaceEntity;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.LanguageStatRow;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.NamespaceStatRow;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

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
}
