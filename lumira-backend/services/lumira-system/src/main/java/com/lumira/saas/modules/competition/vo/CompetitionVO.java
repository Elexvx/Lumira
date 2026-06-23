package com.lumira.saas.modules.competition.vo;

import java.time.LocalDateTime;

public final class CompetitionVO {
    private CompetitionVO() {
    }

    public static class Competition {
        private Long id;
        private Long tenantId;
        private String code;
        private String locale;
        private String title;
        private String category;
        private String level;
        private String organizer;
        private String registrationStart;
        private String registrationEnd;
        private String competitionStart;
        private String competitionEnd;
        private String location;
        private String description;
        private String imageUrl;
        private String tags;
        private String status;
        private Boolean featured;
        private Integer sort;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getOrganizer() { return organizer; }
        public void setOrganizer(String organizer) { this.organizer = organizer; }
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
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
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
