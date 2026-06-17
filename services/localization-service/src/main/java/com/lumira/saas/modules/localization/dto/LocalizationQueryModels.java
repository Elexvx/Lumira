package com.lumira.saas.modules.localization.dto;

public final class LocalizationQueryModels {

    private LocalizationQueryModels() {
    }

    public static class EntryQuery {
        private String targetLocale;
        private String fallbackLocale;
        private String namespaceCode;
        private String keywordLike;
        private String status;
        private String translationStatus;
        private String sortColumn;
        private String sortDirection;
        private long limit;
        private long offset;
        private long countLimit;

        public String getTargetLocale() {
            return targetLocale;
        }

        public void setTargetLocale(String targetLocale) {
            this.targetLocale = targetLocale;
        }

        public String getFallbackLocale() {
            return fallbackLocale;
        }

        public void setFallbackLocale(String fallbackLocale) {
            this.fallbackLocale = fallbackLocale;
        }

        public String getNamespaceCode() {
            return namespaceCode;
        }

        public void setNamespaceCode(String namespaceCode) {
            this.namespaceCode = namespaceCode;
        }

        public String getKeywordLike() {
            return keywordLike;
        }

        public void setKeywordLike(String keywordLike) {
            this.keywordLike = keywordLike;
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

        public String getSortColumn() {
            return sortColumn;
        }

        public void setSortColumn(String sortColumn) {
            this.sortColumn = sortColumn;
        }

        public String getSortDirection() {
            return sortDirection;
        }

        public void setSortDirection(String sortDirection) {
            this.sortDirection = sortDirection;
        }

        public long getLimit() {
            return limit;
        }

        public void setLimit(long limit) {
            this.limit = limit;
        }

        public long getOffset() {
            return offset;
        }

        public void setOffset(long offset) {
            this.offset = offset;
        }

        public long getCountLimit() {
            return countLimit;
        }

        public void setCountLimit(long countLimit) {
            this.countLimit = countLimit;
        }
    }

    public static class RuntimeMessageRow {
        private String messageKey;
        private String defaultMessage;
        private String targetMessage;
        private String fallbackMessage;

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

        public String getTargetMessage() {
            return targetMessage;
        }

        public void setTargetMessage(String targetMessage) {
            this.targetMessage = targetMessage;
        }

        public String getFallbackMessage() {
            return fallbackMessage;
        }

        public void setFallbackMessage(String fallbackMessage) {
            this.fallbackMessage = fallbackMessage;
        }
    }

    public static class LanguageStatRow {
        private String localeCode;
        private Long translatedCount;
        private Long publishedVersion;
        private java.time.LocalDateTime lastPublishedAt;

        public String getLocaleCode() {
            return localeCode;
        }

        public void setLocaleCode(String localeCode) {
            this.localeCode = localeCode;
        }

        public Long getTranslatedCount() {
            return translatedCount;
        }

        public void setTranslatedCount(Long translatedCount) {
            this.translatedCount = translatedCount;
        }

        public Long getPublishedVersion() {
            return publishedVersion;
        }

        public void setPublishedVersion(Long publishedVersion) {
            this.publishedVersion = publishedVersion;
        }

        public java.time.LocalDateTime getLastPublishedAt() {
            return lastPublishedAt;
        }

        public void setLastPublishedAt(java.time.LocalDateTime lastPublishedAt) {
            this.lastPublishedAt = lastPublishedAt;
        }
    }

    public static class NamespaceStatRow {
        private String namespaceCode;
        private Long entryCount;
        private Long translatedCount;

        public String getNamespaceCode() {
            return namespaceCode;
        }

        public void setNamespaceCode(String namespaceCode) {
            this.namespaceCode = namespaceCode;
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
    }
}
