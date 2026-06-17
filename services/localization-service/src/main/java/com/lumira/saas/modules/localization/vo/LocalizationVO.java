package com.lumira.saas.modules.localization.vo;

import com.lumira.common.vo.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalizationVO {

    private LocalizationVO() {
    }

    public static class LanguageVO {
        private Long id;
        private String localeCode;
        private String languageName;
        private String nativeName;
        private String fallbackLocale;
        private Integer sortNo;
        private String status;
        private Boolean defaultLanguage;
        private Long entryCount;
        private Long translatedCount;
        private BigDecimal coverageRate;
        private Long publishedVersion;
        private LocalDateTime lastPublishedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getLocaleCode() {
            return localeCode;
        }

        public void setLocaleCode(String localeCode) {
            this.localeCode = localeCode;
        }

        public String getLanguageName() {
            return languageName;
        }

        public void setLanguageName(String languageName) {
            this.languageName = languageName;
        }

        public String getNativeName() {
            return nativeName;
        }

        public void setNativeName(String nativeName) {
            this.nativeName = nativeName;
        }

        public String getFallbackLocale() {
            return fallbackLocale;
        }

        public void setFallbackLocale(String fallbackLocale) {
            this.fallbackLocale = fallbackLocale;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Boolean getDefaultLanguage() {
            return defaultLanguage;
        }

        public void setDefaultLanguage(Boolean defaultLanguage) {
            this.defaultLanguage = defaultLanguage;
        }

        public Long getEntryCount() {
            return entryCount;
        }

        public void setEntryCount(Long entryCount) {
            this.entryCount = entryCount;
        }

        public Long getTranslatedCount() {
            return translatedCount;
        }

        public void setTranslatedCount(Long translatedCount) {
            this.translatedCount = translatedCount;
        }

        public BigDecimal getCoverageRate() {
            return coverageRate;
        }

        public void setCoverageRate(BigDecimal coverageRate) {
            this.coverageRate = coverageRate;
        }

        public Long getPublishedVersion() {
            return publishedVersion;
        }

        public void setPublishedVersion(Long publishedVersion) {
            this.publishedVersion = publishedVersion;
        }

        public LocalDateTime getLastPublishedAt() {
            return lastPublishedAt;
        }

        public void setLastPublishedAt(LocalDateTime lastPublishedAt) {
            this.lastPublishedAt = lastPublishedAt;
        }
    }

    public static class NamespaceVO {
        private Long id;
        private String namespaceCode;
        private String namespaceName;
        private String sourceType;
        private String sourceRef;
        private Integer sortNo;
        private String status;
        private Long entryCount;
        private Long translatedCount;
        private BigDecimal coverageRate;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getNamespaceCode() {
            return namespaceCode;
        }

        public void setNamespaceCode(String namespaceCode) {
            this.namespaceCode = namespaceCode;
        }

        public String getNamespaceName() {
            return namespaceName;
        }

        public void setNamespaceName(String namespaceName) {
            this.namespaceName = namespaceName;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceRef() {
            return sourceRef;
        }

        public void setSourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
        }

        public Integer getSortNo() {
            return sortNo;
        }

        public void setSortNo(Integer sortNo) {
            this.sortNo = sortNo;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getEntryCount() {
            return entryCount;
        }

        public void setEntryCount(Long entryCount) {
            this.entryCount = entryCount;
        }

        public Long getTranslatedCount() {
            return translatedCount;
        }

        public void setTranslatedCount(Long translatedCount) {
            this.translatedCount = translatedCount;
        }

        public BigDecimal getCoverageRate() {
            return coverageRate;
        }

        public void setCoverageRate(BigDecimal coverageRate) {
            this.coverageRate = coverageRate;
        }
    }

    public static class EntryVO {
        private Long id;
        private String namespaceCode;
        private String namespaceName;
        private String messageKey;
        private String defaultMessage;
        private String sourceLocale;
        private String sourceType;
        private String sourceRef;
        private String status;
        private String translationStatus;
        private String currentTranslation;
        private Long usageCount;
        private Map<String, String> translations = new LinkedHashMap<>();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getNamespaceCode() {
            return namespaceCode;
        }

        public void setNamespaceCode(String namespaceCode) {
            this.namespaceCode = namespaceCode;
        }

        public String getNamespaceName() {
            return namespaceName;
        }

        public void setNamespaceName(String namespaceName) {
            this.namespaceName = namespaceName;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public void setMessageKey(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }

        public void setDefaultMessage(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        public String getSourceLocale() {
            return sourceLocale;
        }

        public void setSourceLocale(String sourceLocale) {
            this.sourceLocale = sourceLocale;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceRef() {
            return sourceRef;
        }

        public void setSourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTranslationStatus() {
            return translationStatus;
        }

        public void setTranslationStatus(String translationStatus) {
            this.translationStatus = translationStatus;
        }

        public String getCurrentTranslation() {
            return currentTranslation;
        }

        public void setCurrentTranslation(String currentTranslation) {
            this.currentTranslation = currentTranslation;
        }

        public Long getUsageCount() {
            return usageCount;
        }

        public void setUsageCount(Long usageCount) {
            this.usageCount = usageCount;
        }

        public Map<String, String> getTranslations() {
            return translations;
        }

        public void setTranslations(Map<String, String> translations) {
            this.translations = translations == null ? new LinkedHashMap<>() : new LinkedHashMap<>(translations);
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class EntryPageResponse extends PageResponse<EntryVO> {
        private Boolean hasMore;
        private Boolean totalCapped;

        public Boolean getHasMore() {
            return hasMore;
        }

        public void setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
        }

        public Boolean getTotalCapped() {
            return totalCapped;
        }

        public void setTotalCapped(Boolean totalCapped) {
            this.totalCapped = totalCapped;
        }
    }

    public static class ReleaseVO {
        private Long id;
        private String localeCode;
        private Long releaseVersion;
        private String fallbackLocale;
        private String note;
        private Boolean active;
        private Long publishedBy;
        private LocalDateTime publishedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getLocaleCode() {
            return localeCode;
        }

        public void setLocaleCode(String localeCode) {
            this.localeCode = localeCode;
        }

        public Long getReleaseVersion() {
            return releaseVersion;
        }

        public void setReleaseVersion(Long releaseVersion) {
            this.releaseVersion = releaseVersion;
        }

        public String getFallbackLocale() {
            return fallbackLocale;
        }

        public void setFallbackLocale(String fallbackLocale) {
            this.fallbackLocale = fallbackLocale;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public Long getPublishedBy() {
            return publishedBy;
        }

        public void setPublishedBy(Long publishedBy) {
            this.publishedBy = publishedBy;
        }

        public LocalDateTime getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(LocalDateTime publishedAt) {
            this.publishedAt = publishedAt;
        }
    }

    public static class RuntimeBundleVO {
        private String localeCode;
        private String fallbackLocale;
        private Long releaseVersion;
        private Map<String, String> messages = new LinkedHashMap<>();

        public String getLocaleCode() {
            return localeCode;
        }

        public void setLocaleCode(String localeCode) {
            this.localeCode = localeCode;
        }

        public String getFallbackLocale() {
            return fallbackLocale;
        }

        public void setFallbackLocale(String fallbackLocale) {
            this.fallbackLocale = fallbackLocale;
        }

        public Long getReleaseVersion() {
            return releaseVersion;
        }

        public void setReleaseVersion(Long releaseVersion) {
            this.releaseVersion = releaseVersion;
        }

        public Map<String, String> getMessages() {
            return messages;
        }

        public void setMessages(Map<String, String> messages) {
            this.messages = messages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(messages);
        }
    }

    public static class SyncResultVO {
        private int languageCount;
        private int namespaceCount;
        private int entryCount;
        private int translationCount;
        private int usageCount;

        public int getLanguageCount() {
            return languageCount;
        }

        public void setLanguageCount(int languageCount) {
            this.languageCount = languageCount;
        }

        public int getNamespaceCount() {
            return namespaceCount;
        }

        public void setNamespaceCount(int namespaceCount) {
            this.namespaceCount = namespaceCount;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public void setEntryCount(int entryCount) {
            this.entryCount = entryCount;
        }

        public int getTranslationCount() {
            return translationCount;
        }

        public void setTranslationCount(int translationCount) {
            this.translationCount = translationCount;
        }

        public int getUsageCount() {
            return usageCount;
        }

        public void setUsageCount(int usageCount) {
            this.usageCount = usageCount;
        }
    }
}
