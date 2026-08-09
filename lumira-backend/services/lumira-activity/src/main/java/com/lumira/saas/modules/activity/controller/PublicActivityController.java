package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.api.event.EventCatalogItem;
import com.lumira.api.event.EventCatalogPage;
import com.lumira.api.event.EventCatalogQueryPort;
import com.lumira.saas.modules.activity.vo.ActivityPageResponse;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/aiadc/activities")
public class PublicActivityController {
    private final EventCatalogQueryPort eventCatalogQueryPort;

    public PublicActivityController(EventCatalogQueryPort eventCatalogQueryPort) {
        this.eventCatalogQueryPort = eventCatalogQueryPort;
    }

    @GetMapping
    public ApiResponse<ActivityPageResponse<ActivityVO.PublicActivity>> activities(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        EventCatalogPage catalogPage = eventCatalogQueryPort.listPublished(
                keyword,
                "ACTIVITY",
                locale,
                featured,
                pageNo,
                pageSize
        );
        ActivityPageResponse<ActivityVO.PublicActivity> page = new ActivityPageResponse<>();
        page.setRecords(catalogPage.records().stream().map(this::toPublicActivity).toList());
        page.setTotal(catalogPage.total());
        page.setPageNo(catalogPage.pageNo());
        page.setPageSize(catalogPage.pageSize());
        page.setHasMore(catalogPage.hasMore());
        return ApiResponse.success(page, TraceContext.getRequestId());
    }

    private ActivityVO.PublicActivity toPublicActivity(EventCatalogItem item) {
        ActivityVO.PublicActivity activity = new ActivityVO.PublicActivity();
        activity.setId(item.sourceId());
        activity.setLocale(item.locale());
        activity.setTitle(item.title());
        activity.setSubtitle(item.subtitle());
        activity.setDescription(item.summary());
        activity.setImageUrl(item.imageUrl());
        activity.setTags(item.tags());
        activity.setCtaLabel(item.ctaLabel());
        activity.setCtaHref(item.ctaHref());
        activity.setActivityDate(item.eventStart());
        activity.setActivityTime(item.eventTime());
        activity.setLocation(item.location());
        activity.setFeatured(item.featured());
        return activity;
    }
}
