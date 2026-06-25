package com.lumira.saas.modules.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ActivityDTO {
    private ActivityDTO() {
    }

    public static class ActivityUpsertRequest {
        @Size(max = 64)
        private String code;
        @Size(max = 64)
        private String locale;
        @NotBlank
        @Size(max = 128)
        private String title;
        @Size(max = 64)
        private String subtitle;
        @Size(max = 1000)
        private String description;
        @Size(max = 512)
        private String imageUrl;
        @Size(max = 64)
        private String iconKey;
        private Integer sort;
        @Size(max = 32)
        private String status;
        @Size(max = 1000)
        private String tags;
        @Size(max = 64)
        private String ctaLabel;
        @Size(max = 512)
        private String ctaHref;
        @Size(max = 64)
        private String badgeText;
        @Size(max = 32)
        private String badgeTone;
        @NotBlank
        @Size(max = 64)
        private String activityDate;
        @NotBlank
        @Size(max = 64)
        private String activityTime;
        @NotBlank
        @Size(max = 255)
        private String location;
        private Boolean featured;

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
        public String getIconKey() { return iconKey; }
        public void setIconKey(String iconKey) { this.iconKey = iconKey; }
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
        public String getBadgeText() { return badgeText; }
        public void setBadgeText(String badgeText) { this.badgeText = badgeText; }
        public String getBadgeTone() { return badgeTone; }
        public void setBadgeTone(String badgeTone) { this.badgeTone = badgeTone; }
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
