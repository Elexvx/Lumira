package com.lumira.saas.modules.competition.vo;

import java.time.LocalDateTime;

public final class CompetitionVO {
    private CompetitionVO() {
    }

    public static class Competition {
        private Long id;
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
        private String homepageContent;
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
        public String getHomepageContent() { return homepageContent; }
        public void setHomepageContent(String homepageContent) { this.homepageContent = homepageContent; }
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
}
