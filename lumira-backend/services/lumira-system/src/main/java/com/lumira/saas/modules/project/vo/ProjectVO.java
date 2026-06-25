package com.lumira.saas.modules.project.vo;

import java.time.LocalDateTime;

public final class ProjectVO {
    private ProjectVO() {
    }

    public static class Project {
        private Long id;
        private String code;
        private String locale;
        private String title;
        private String category;
        private String description;
        private String imageUrl;
        private String ownerName;
        private String rating;
        private Integer sort;
        private String status;
        private String tags;
        private String ctaLabel;
        private String ctaHref;
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
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public String getRating() { return rating; }
        public void setRating(String rating) { this.rating = rating; }
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
        public Boolean getFeatured() { return featured; }
        public void setFeatured(Boolean featured) { this.featured = featured; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
