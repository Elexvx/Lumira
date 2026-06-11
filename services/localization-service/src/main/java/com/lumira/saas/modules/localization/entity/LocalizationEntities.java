package com.lumira.saas.modules.localization.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

public final class LocalizationEntities {

    private LocalizationEntities() {
    }

    @TableName("sys_localization_language")
    public static class LanguageEntity {
        @TableId(type = IdType.AUTO)
        public Long id;
        public String localeCode;
        public String languageName;
        public String nativeName;
        public String fallbackLocale;
        public Integer sortNo;
        public Integer isDefault;
        public String status;
        public Long createdBy;
        public LocalDateTime createdAt;
        public Long updatedBy;
        public LocalDateTime updatedAt;
        public Integer deleted;
    }

    @TableName("sys_localization_namespace")
    public static class NamespaceEntity {
        @TableId(type = IdType.AUTO)
        public Long id;
        public String namespaceCode;
        public String namespaceName;
        public String sourceType;
        public String sourceRef;
        public Integer sortNo;
        public String status;
        public Long createdBy;
        public LocalDateTime createdAt;
        public Long updatedBy;
        public LocalDateTime updatedAt;
        public Integer deleted;
    }

    @TableName("sys_localization_entry")
    public static class EntryEntity {
        @TableId(type = IdType.AUTO)
        public Long id;
        public Long namespaceId;
        public String messageKey;
        public String defaultMessage;
        public String sourceLocale;
        public String sourceType;
        public String sourceRef;
        public String status;
        public Long createdBy;
        public LocalDateTime createdAt;
        public Long updatedBy;
        public LocalDateTime updatedAt;
        public Integer deleted;
    }

    @TableName("sys_localization_translation")
    public static class TranslationEntity {
        @TableId(type = IdType.AUTO)
        public Long id;
        public Long entryId;
        public String localeCode;
        public String translatedMessage;
        public String translationStatus;
        public Integer machineGenerated;
        public String reviewStatus;
        public Long translatedBy;
        public LocalDateTime translatedAt;
        public Long createdBy;
        public LocalDateTime createdAt;
        public Long updatedBy;
        public LocalDateTime updatedAt;
        public Integer deleted;
    }

    @TableName("sys_localization_usage_ref")
    public static class UsageRefEntity {
        @TableId(type = IdType.AUTO)
        public Long id;
        public Long entryId;
        public String sourceType;
        public String sourceRef;
        public Integer sourceLine;
        public String sourceText;
        public Long createdBy;
        public LocalDateTime createdAt;
        public Long updatedBy;
        public LocalDateTime updatedAt;
        public Integer deleted;
    }

    @TableName("sys_localization_release")
    public static class ReleaseEntity {
        @TableId(type = IdType.AUTO)
        public Long id;
        public String localeCode;
        public Long releaseVersion;
        public String fallbackLocale;
        public String bundleJson;
        public String note;
        public Integer activeFlag;
        public Long publishedBy;
        public LocalDateTime publishedAt;
        public Long createdBy;
        public LocalDateTime createdAt;
        public Long updatedBy;
        public LocalDateTime updatedAt;
        public Integer deleted;
    }
}
