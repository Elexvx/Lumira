package com.lumira.saas.modules.activity.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.activity.app.ActivityManagementAppService;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/aiadc/activities")
public class PublicActivityController {
    private final ActivityManagementAppService activityManagementAppService;

    public PublicActivityController(ActivityManagementAppService activityManagementAppService) {
        this.activityManagementAppService = activityManagementAppService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ActivityVO.Activity>> activities(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        return ApiResponse.success(
                activityManagementAppService.listPublishedActivities(keyword, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }
}
