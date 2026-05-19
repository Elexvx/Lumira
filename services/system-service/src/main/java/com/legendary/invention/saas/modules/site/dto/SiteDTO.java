package com.legendary.invention.saas.modules.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class SiteDTO {
    private SiteDTO() {
    }

    public static class SiteSettingsRequest {
        @NotBlank
        public String code;
        @NotBlank
        public String name;
        public String primaryDomain;
        public String loginRoute;
        public Long logoFileId;
        public Long faviconFileId;
        public String themeJson;
        public String seoJson;
        public String status;
    }

    public static class NavigationRequest {
        public Long parentId;
        @NotBlank
        public String title;
        @NotBlank
        public String linkType;
        @NotBlank
        public String linkTarget;
        public String openType;
        public Integer sortOrder;
        public String status;
    }

    public static class CarouselRequest {
        @NotBlank
        public String title;
        public String subtitle;
        public Long imageFileId;
        public String imageUrl;
        public String linkType;
        public String linkTarget;
        public String openType;
        public Integer sortOrder;
        public String status;
    }

    public static class PageRequest {
        @NotBlank
        public String title;
        @NotBlank
        public String slug;
        public String pageType;
        public String seoJson;
        @NotBlank
        public String blocksJson;
    }

    public static class ContentRequest {
        public Long categoryId;
        @NotBlank
        public String title;
        @NotBlank
        public String slug;
        public String summary;
        public Long coverFileId;
        public String bodyType;
        public String bodyText;
        public String bodyJson;
        public String seoJson;
        public String tagsJson;
    }

    public static class CategoryRequest {
        public Long parentId;
        @NotBlank
        public String code;
        @NotBlank
        public String name;
        public Integer sortOrder;
        public String status;
    }

    public static class FormRequest {
        @NotBlank
        public String code;
        @NotBlank
        public String name;
        public String submitPolicy;
        @NotBlank
        public String schemaJson;
        public String notificationJson;
        public String status;
    }

    public static class SubmissionRequest {
        @NotBlank
        public String dataJson;
        public String attachmentFileIdsJson;
        public String website;
    }

    public static class ReviewRequest {
        @NotBlank
        public String status;
        public String reviewRemark;
    }

    public static class IdRequest {
        @NotNull
        public Long id;
    }
}
