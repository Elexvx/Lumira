package com.lumira.saas.modules.system.profile.repository;

import java.util.List;
import java.util.Map;

public interface SystemProfileSettingsRepository {
    Map<String, String> findPlatformConfigValues(List<String> keys);
    List<FieldDefinition> findEnabledFieldDefinitions(String pageKey);
    List<String> findEnabledDictionaryValues(String dictionaryCode);
    int upsertPlatformConfig(String configKey, String configName, String configValue, String remark,
                             Long operatorId, String operatorUuid);

    record FieldDefinition(String fieldKey, String fieldLabel, String fieldDescription,
                           String groupKey, String groupLabel, String visibleConfigKey, String weightConfigKey,
                           boolean defaultVisible, int defaultWeight, String fieldType, boolean required,
                           String placeholder, int sortNo) {}
}
