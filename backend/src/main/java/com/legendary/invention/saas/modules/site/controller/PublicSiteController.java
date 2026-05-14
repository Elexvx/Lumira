package com.legendary.invention.saas.modules.site.controller;

import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.common.annotation.RepeatSubmit;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.site.app.PublicSiteAppService;
import com.legendary.invention.saas.modules.site.dto.SiteDTO;
import com.legendary.invention.saas.modules.site.vo.SiteVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/site")
public class PublicSiteController {

    private final PublicSiteAppService publicSiteAppService;
    private final SecurityContextFacade securityContextFacade;

    public PublicSiteController(PublicSiteAppService publicSiteAppService, SecurityContextFacade securityContextFacade) {
        this.publicSiteAppService = publicSiteAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @GetMapping("/runtime")
    public ApiResponse<SiteVO.PublicRuntimeVO> runtime() {
        return ApiResponse.success(publicSiteAppService.runtime(), TraceContext.getRequestId());
    }

    @GetMapping("/navigation")
    public ApiResponse<List<SiteVO.NavigationVO>> navigation() {
        return ApiResponse.success(publicSiteAppService.navigation(), TraceContext.getRequestId());
    }

    @GetMapping("/pages/{slug}")
    public ApiResponse<SiteVO.PublicPageVO> page(@PathVariable String slug) {
        return ApiResponse.success(publicSiteAppService.page(slug), TraceContext.getRequestId());
    }

    @GetMapping("/pages")
    public ApiResponse<SiteVO.PublicPageVO> pageByQuery(@RequestParam(defaultValue = "/") String slug) {
        return ApiResponse.success(publicSiteAppService.page(slug), TraceContext.getRequestId());
    }

    @GetMapping("/contents")
    public ApiResponse<PageResponse<SiteVO.ContentVO>> contents(@RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.success(publicSiteAppService.contents(categoryId, pageNo, pageSize), TraceContext.getRequestId());
    }

    @GetMapping("/contents/{slug}")
    public ApiResponse<SiteVO.ContentVO> content(@PathVariable String slug) {
        return ApiResponse.success(publicSiteAppService.content(slug), TraceContext.getRequestId());
    }

    @GetMapping("/forms/{code}")
    public ApiResponse<SiteVO.FormVO> form(@PathVariable String code) {
        return ApiResponse.success(publicSiteAppService.form(code), TraceContext.getRequestId());
    }

    @PostMapping("/forms/{code}/submissions")
    @RepeatSubmit
    public ApiResponse<SiteVO.SubmissionVO> submit(@PathVariable String code, @Valid @RequestBody SiteDTO.SubmissionRequest request, HttpServletRequest httpRequest) {
        Long userId = securityContextFacade.getCurrentUser() == null ? null : securityContextFacade.getCurrentUser().getUserId();
        return ApiResponse.success(publicSiteAppService.submit(code, request, clientIp(httpRequest), userId), TraceContext.getRequestId());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
