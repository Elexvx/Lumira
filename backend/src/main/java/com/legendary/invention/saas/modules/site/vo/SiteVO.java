package com.legendary.invention.saas.modules.site.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class SiteVO {
    private SiteVO() {
    }

    public static class SiteSettingsVO {
        public Long id;
        public Long tenantId;
        public String code;
        public String name;
        public String primaryDomain;
        public Long logoFileId;
        public String logoUrl;
        public Long faviconFileId;
        public String faviconUrl;
        public String themeJson;
        public String seoJson;
        public String status;
        public LocalDateTime updatedAt;
    }

    public static class NavigationVO {
        public Long id;
        public Long parentId;
        public String title;
        public String linkType;
        public String linkTarget;
        public String openType;
        public Integer sortOrder;
        public String status;
        public List<NavigationVO> children;
    }

    public static class PageVO {
        public Long id;
        public String title;
        public String slug;
        public String pageType;
        public String seoJson;
        public Long currentDraftVersion;
        public Long currentPublishedVersion;
        public String blocksJson;
        public String status;
        public LocalDateTime publishedAt;
        public LocalDateTime updatedAt;
    }

    public static class ContentCategoryVO {
        public Long id;
        public Long parentId;
        public String code;
        public String name;
        public Integer sortOrder;
        public String status;
    }

    public static class ContentVO {
        public Long id;
        public Long categoryId;
        public String title;
        public String slug;
        public String summary;
        public Long coverFileId;
        public String coverUrl;
        public String bodyType;
        public String bodyText;
        public String bodyJson;
        public String seoJson;
        public String tagsJson;
        public String status;
        public LocalDateTime publishedAt;
        public LocalDateTime updatedAt;
    }

    public static class FormVO {
        public Long id;
        public String code;
        public String name;
        public String submitPolicy;
        public String schemaJson;
        public String notificationJson;
        public String status;
        public LocalDateTime updatedAt;
    }

    public static class SubmissionVO {
        public Long id;
        public Long formId;
        public String formName;
        public Long submitterUserId;
        public String submitterIp;
        public String dataJson;
        public String attachmentFileIdsJson;
        public String status;
        public Long reviewedBy;
        public LocalDateTime reviewedAt;
        public String reviewRemark;
        public LocalDateTime createdAt;
    }

    public static class PublicRuntimeVO {
        public SiteSettingsVO site;
        public List<NavigationVO> navigation;
    }

    public static class PublicPageVO {
        public SiteSettingsVO site;
        public PageVO page;
        public String blocksJson;
    }
}
