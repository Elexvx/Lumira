package com.lumira.saas.modules.localization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocalizationDTO {

    private LocalizationDTO() {
    }

    public static class LanguageUpsertRequest {
        @NotBlank
        private String localeCode;

        @NotBlank
        private String languageName;

        private String nativeName;

        private String fallbackLocale;

        private Integer sortNo;

        private String status;

        private Boolean defaultLanguage;

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
    }

    public static class NamespaceUpsertRequest {
        @NotBlank
        private String namespaceCode;

        @NotBlank
        private String namespaceName;

        private String sourceType;

        private String sourceRef;

        private Integer sortNo;

        private String status;

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
    }

    public static class EntryUpsertRequest {
        private Long id;

        @NotBlank
        private String namespaceCode;

        @NotBlank
        private String messageKey;

        @NotBlank
        private String defaultMessage;

        @NotBlank
        private String sourceLocale;

        private String sourceType;

        private String sourceRef;

        private String status;

        private String localeCode;

        private String translatedMessage;

        private Map<String, String> translations = new LinkedHashMap<>();

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

        public String getLocaleCode() {
            return localeCode;
        }

        public void setLocaleCode(String localeCode) {
            this.localeCode = localeCode;
        }

        public String getTranslatedMessage() {
            return translatedMessage;
        }

        public void setTranslatedMessage(String translatedMessage) {
            this.translatedMessage = translatedMessage;
        }

        public Map<String, String> getTranslations() {
            return translations;
        }

        public void setTranslations(Map<String, String> translations) {
            this.translations = translations == null ? new LinkedHashMap<>() : new LinkedHashMap<>(translations);
        }
    }

    public static class SyncRequest {
        @NotBlank
        private String sourceLocale;

        @NotNull
        @Valid
        private List<EntryUpsertRequest> items;

        public String getSourceLocale() {
            return sourceLocale;
        }

        public void setSourceLocale(String sourceLocale) {
            this.sourceLocale = sourceLocale;
        }

        public List<EntryUpsertRequest> getItems() {
            return items;
        }

        public void setItems(List<EntryUpsertRequest> items) {
            this.items = items;
        }
    }

    public static class PublishRequest {
        @NotBlank
        private String localeCode;

        private String note;

        public String getLocaleCode() {
            return localeCode;
        }

        public void setLocaleCode(String localeCode) {
            this.localeCode = localeCode;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public static class RollbackRequest {
        @NotNull
        private Long releaseId;

        public Long getReleaseId() {
            return releaseId;
        }

        public void setReleaseId(Long releaseId) {
            this.releaseId = releaseId;
        }
    }
}
