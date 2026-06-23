package com.lumira.saas.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProjectDTO {
    private ProjectDTO() {
    }

    public static class ProjectUpsertRequest {
        @NotBlank
        @Size(max = 64)
        private String code;
        @Size(max = 16)
        private String locale;
        @NotBlank
        @Size(max = 128)
        private String title;
        @Size(max = 64)
        private String category;
        @Size(max = 1000)
        private String description;
        @Size(max = 512)
        private String imageUrl;
        @Size(max = 128)
        private String ownerName;
        @Size(max = 32)
        private String rating;
        private Integer sort;
        @Size(max = 32)
        private String status;
        @Size(max = 1000)
        private String tags;
        @Size(max = 64)
        private String ctaLabel;
        @Size(max = 512)
        private String ctaHref;
        private Boolean featured;

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
    }
}
