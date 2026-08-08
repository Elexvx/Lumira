package com.lumira.saas.modules.activity.vo;

import java.time.LocalDateTime;

public final class ActivityVO {
    private ActivityVO() {
    }

    public static class Activity {
        private Long id;
        private String code;
        private String locale;
        private String title;
        private String subtitle;
        private String description;
        private String imageUrl;
        private Integer sort;
        private String status;
        private String tags;
        private String ctaLabel;
        private String ctaHref;
        private String activityDate;
        private String activityTime;
        private String location;
        private Boolean featured;
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
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getCtaLabel() { return ctaLabel; }
        public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }
        public String getCtaHref() { return ctaHref; }
        public void setCtaHref(String ctaHref) { this.ctaHref = ctaHref; }
        public String getActivityDate() { return activityDate; }
        public void setActivityDate(String activityDate) { this.activityDate = activityDate; }
        public String getActivityTime() { return activityTime; }
        public void setActivityTime(String activityTime) { this.activityTime = activityTime; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Boolean getFeatured() { return featured; }
        public void setFeatured(Boolean featured) { this.featured = featured; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class PublicActivity {
        private Long id;
        private String locale;
        private String title;
        private String subtitle;
        private String description;
        private String imageUrl;
        private String tags;
        private String ctaLabel;
        private String ctaHref;
        private String activityDate;
        private String activityTime;
        private String location;
        private Boolean featured;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getCtaLabel() { return ctaLabel; }
        public void setCtaLabel(String ctaLabel) { this.ctaLabel = ctaLabel; }
        public String getCtaHref() { return ctaHref; }
        public void setCtaHref(String ctaHref) { this.ctaHref = ctaHref; }
        public String getActivityDate() { return activityDate; }
        public void setActivityDate(String activityDate) { this.activityDate = activityDate; }
        public String getActivityTime() { return activityTime; }
        public void setActivityTime(String activityTime) { this.activityTime = activityTime; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public Boolean getFeatured() { return featured; }
        public void setFeatured(Boolean featured) { this.featured = featured; }
    }
}
