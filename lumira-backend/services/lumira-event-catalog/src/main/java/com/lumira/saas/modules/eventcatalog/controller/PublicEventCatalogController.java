package com.lumira.saas.modules.eventcatalog.controller;

import com.lumira.api.event.EventCatalogPage;
import com.lumira.api.event.EventCatalogQueryPort;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public cross-owner catalog endpoint backed exclusively by event_catalog_item. */
@RestController
@RequestMapping("/api/v1/public/event-catalog")
public class PublicEventCatalogController {

    private final EventCatalogQueryPort eventCatalogQueryPort;

    public PublicEventCatalogController(EventCatalogQueryPort eventCatalogQueryPort) {
        this.eventCatalogQueryPort = eventCatalogQueryPort;
    }

    @GetMapping
    public ApiResponse<EventCatalogPage> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sourceType", required = false) String sourceType,
            @RequestParam(name = "locale", required = false) String locale,
            @RequestParam(name = "featured", required = false) Boolean featured,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        return ApiResponse.success(
                eventCatalogQueryPort.listPublished(keyword, sourceType, locale, featured, pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }
}
