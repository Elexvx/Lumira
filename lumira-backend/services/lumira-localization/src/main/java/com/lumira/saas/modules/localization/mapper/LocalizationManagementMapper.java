package com.lumira.saas.modules.localization.mapper;

import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.EntryQuery;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.LanguageStatRow;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.NamespaceStatRow;
import com.lumira.saas.modules.localization.dto.LocalizationQueryModels.RuntimeMessageRow;
import com.lumira.saas.modules.localization.vo.LocalizationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LocalizationManagementMapper {

    List<LocalizationVO.EntryVO> listEntries(@Param("query") EntryQuery query);

    Long countEntries(@Param("query") EntryQuery query);

    LocalizationVO.EntryVO findEntry(
            @Param("entryId") Long entryId,
            @Param("targetLocale") String targetLocale,
            @Param("fallbackLocale") String fallbackLocale
    );

    List<RuntimeMessageRow> listRuntimeMessages(
            @Param("targetLocale") String targetLocale,
            @Param("fallbackLocale") String fallbackLocale
    );

    Long countTranslatedEntries(@Param("localeCode") String localeCode);

    List<LanguageStatRow> listLanguageStats();

    Long countEntriesByNamespace(@Param("namespaceCode") String namespaceCode);

    List<NamespaceStatRow> listNamespaceStats(@Param("localeCode") String localeCode);

    Long countTranslatedEntriesByNamespace(
            @Param("namespaceCode") String namespaceCode,
            @Param("localeCode") String localeCode
    );
}
