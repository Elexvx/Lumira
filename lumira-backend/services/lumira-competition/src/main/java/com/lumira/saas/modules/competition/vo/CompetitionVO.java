package com.lumira.saas.modules.competition.vo;

import java.time.LocalDateTime;

public final class CompetitionVO {
    private CompetitionVO() {
    }

    public static class Competition {
        private Long id;
        private String uuid;
        private String competitionNo;
        private String code;
        private String locale;
        private String title;
        private String shortName;
        private String category;
        private String level;
        private String competitionLevel;
        private String organizer;
        private String organizersJson;
        private String registrationStart;
        private String registrationEnd;
        private String competitionStart;
        private String competitionEnd;
        private String location;
        private String participationScope;
        private String participationRequirement;
        private String scheduleJson;
        private String description;
        private String imageUrl;
        private String contactName;
        private String contactQrCodeUrl;
        private String tags;
        private String status;
        private String feeMode;
        private Long entryFeeMinor;
        private String currency;
        private Boolean featured;
        private Integer sort;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
        public String getCompetitionNo() { return competitionNo; }
        public void setCompetitionNo(String competitionNo) { this.competitionNo = competitionNo; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getCompetitionLevel() { return competitionLevel; }
        public void setCompetitionLevel(String competitionLevel) { this.competitionLevel = competitionLevel; }
        public String getOrganizer() { return organizer; }
        public void setOrganizer(String organizer) { this.organizer = organizer; }
        public String getOrganizersJson() { return organizersJson; }
        public void setOrganizersJson(String organizersJson) { this.organizersJson = organizersJson; }
        public String getRegistrationStart() { return registrationStart; }
        public void setRegistrationStart(String registrationStart) { this.registrationStart = registrationStart; }
        public String getRegistrationEnd() { return registrationEnd; }
        public void setRegistrationEnd(String registrationEnd) { this.registrationEnd = registrationEnd; }
        public String getCompetitionStart() { return competitionStart; }
        public void setCompetitionStart(String competitionStart) { this.competitionStart = competitionStart; }
        public String getCompetitionEnd() { return competitionEnd; }
        public void setCompetitionEnd(String competitionEnd) { this.competitionEnd = competitionEnd; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getParticipationScope() { return participationScope; }
        public void setParticipationScope(String participationScope) { this.participationScope = participationScope; }
        public String getParticipationRequirement() { return participationRequirement; }
        public void setParticipationRequirement(String participationRequirement) { this.participationRequirement = participationRequirement; }
        public String getScheduleJson() { return scheduleJson; }
        public void setScheduleJson(String scheduleJson) { this.scheduleJson = scheduleJson; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        public String getContactQrCodeUrl() { return contactQrCodeUrl; }
        public void setContactQrCodeUrl(String contactQrCodeUrl) { this.contactQrCodeUrl = contactQrCodeUrl; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFeeMode() { return feeMode; }
        public void setFeeMode(String feeMode) { this.feeMode = feeMode; }
        public Long getEntryFeeMinor() { return entryFeeMinor; }
        public void setEntryFeeMinor(Long entryFeeMinor) { this.entryFeeMinor = entryFeeMinor; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Boolean getFeatured() { return featured; }
        public void setFeatured(Boolean featured) { this.featured = featured; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class Settings {
        private Competition competition;
        private ConfigSet activeConfigSet;
        private java.util.List<ConfigItem> documents = java.util.List.of();
        private java.util.List<ConfigItem> fields = java.util.List.of();
        private java.util.List<ConfigItem> payments = java.util.List.of();
        private java.util.List<ConfigItem> files = java.util.List.of();
        private java.util.List<ConfigItem> stageMaterials = java.util.List.of();
        private java.util.List<ConfigItem> timeline = java.util.List.of();

        public Competition getCompetition() { return competition; }
        public void setCompetition(Competition competition) { this.competition = competition; }
        public ConfigSet getActiveConfigSet() { return activeConfigSet; }
        public void setActiveConfigSet(ConfigSet activeConfigSet) { this.activeConfigSet = activeConfigSet; }
        public java.util.List<ConfigItem> getDocuments() { return documents; }
        public void setDocuments(java.util.List<ConfigItem> documents) { this.documents = documents; }
        public java.util.List<ConfigItem> getFields() { return fields; }
        public void setFields(java.util.List<ConfigItem> fields) { this.fields = fields; }
        public java.util.List<ConfigItem> getPayments() { return payments; }
        public void setPayments(java.util.List<ConfigItem> payments) { this.payments = payments; }
        public java.util.List<ConfigItem> getFiles() { return files; }
        public void setFiles(java.util.List<ConfigItem> files) { this.files = files; }
        public java.util.List<ConfigItem> getStageMaterials() { return stageMaterials; }
        public void setStageMaterials(java.util.List<ConfigItem> stageMaterials) { this.stageMaterials = stageMaterials; }
        public java.util.List<ConfigItem> getTimeline() { return timeline; }
        public void setTimeline(java.util.List<ConfigItem> timeline) { this.timeline = timeline; }
    }

    public static class ConfigSet {
        private Long id;
        private String competitionUuid;
        private Integer version;
        private String status;
        private LocalDateTime publishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCompetitionUuid() { return competitionUuid; }
        public void setCompetitionUuid(String competitionUuid) { this.competitionUuid = competitionUuid; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class ConfigItem {
        private Long id;
        private String competitionUuid;
        private Long configSetId;
        private String itemType;
        private String itemKey;
        private String title;
        private String contentJson;
        private String contentText;
        private Integer sortOrder;
        private Boolean requiredFlag;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getCompetitionUuid() { return competitionUuid; }
        public void setCompetitionUuid(String competitionUuid) { this.competitionUuid = competitionUuid; }
        public Long getConfigSetId() { return configSetId; }
        public void setConfigSetId(Long configSetId) { this.configSetId = configSetId; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        public String getItemKey() { return itemKey; }
        public void setItemKey(String itemKey) { this.itemKey = itemKey; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContentJson() { return contentJson; }
        public void setContentJson(String contentJson) { this.contentJson = contentJson; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Boolean getRequiredFlag() { return requiredFlag; }
        public void setRequiredFlag(Boolean requiredFlag) { this.requiredFlag = requiredFlag; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
