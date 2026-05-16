package com.legendary.invention.saas.modules.site.controller;

import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.saas.common.annotation.RepeatSubmit;
import com.legendary.invention.saas.common.api.ApiResponse;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.SecurityContextFacade;
import com.legendary.invention.saas.modules.file.app.FileManagementAppService;
import com.legendary.invention.saas.modules.file.vo.FileVO;
import com.legendary.invention.saas.modules.iam.service.PermissionGuard;
import com.legendary.invention.saas.modules.site.app.SiteManagementAppService;
import com.legendary.invention.saas.modules.site.dto.SiteDTO;
import com.legendary.invention.saas.modules.site.vo.SiteVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/site")
public class SiteController {

    private final SiteManagementAppService siteManagementAppService;
    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public SiteController(
            SiteManagementAppService siteManagementAppService,
            FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.siteManagementAppService = siteManagementAppService;
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/settings")
    public ApiResponse<SiteVO.SiteSettingsVO> settings() {
        require("site:settings");
        return ApiResponse.success(siteManagementAppService.settings(currentUser()), TraceContext.getRequestId());
    }

    @PutMapping("/settings")
    @RepeatSubmit
    public ApiResponse<SiteVO.SiteSettingsVO> updateSettings(@Valid @RequestBody SiteDTO.SiteSettingsRequest request) {
        require("site:settings:update");
        return ApiResponse.success(siteManagementAppService.updateSettings(currentUser(), request), TraceContext.getRequestId());
    }

    @PostMapping(value = "/uploads/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<FileVO.FileObjectVO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "usage", required = false) String usage
    ) {
        requireSiteUploadPermission(usage);
        String normalizedUsage = normalizeSiteUploadUsage(usage);
        return ApiResponse.success(
                fileManagementAppService.uploadFile(
                        currentUser(),
                        file,
                        "site-" + normalizedUsage,
                        "site," + normalizedUsage,
                        siteUploadRemark(normalizedUsage)
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/navigation")
    public ApiResponse<List<SiteVO.NavigationVO>> navigation() {
        require("site:navigation");
        return ApiResponse.success(siteManagementAppService.navigations(currentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/navigation")
    @RepeatSubmit
    public ApiResponse<SiteVO.NavigationVO> createNavigation(@Valid @RequestBody SiteDTO.NavigationRequest request) {
        require("site:navigation:create");
        return ApiResponse.success(siteManagementAppService.createNavigation(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/navigation/{id}")
    @RepeatSubmit
    public ApiResponse<SiteVO.NavigationVO> updateNavigation(@PathVariable Long id, @Valid @RequestBody SiteDTO.NavigationRequest request) {
        require("site:navigation:update");
        return ApiResponse.success(siteManagementAppService.updateNavigation(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/navigation/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteNavigation(@PathVariable Long id) {
        require("site:navigation:delete");
        return ApiResponse.success(siteManagementAppService.deleteNavigation(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/carousels")
    public ApiResponse<List<SiteVO.CarouselVO>> carousels() {
        require("site:carousel");
        return ApiResponse.success(siteManagementAppService.carousels(currentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/carousels")
    @RepeatSubmit
    public ApiResponse<SiteVO.CarouselVO> createCarousel(@Valid @RequestBody SiteDTO.CarouselRequest request) {
        require("site:carousel:create");
        return ApiResponse.success(siteManagementAppService.createCarousel(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/carousels/{id}")
    @RepeatSubmit
    public ApiResponse<SiteVO.CarouselVO> updateCarousel(@PathVariable Long id, @Valid @RequestBody SiteDTO.CarouselRequest request) {
        require("site:carousel:update");
        return ApiResponse.success(siteManagementAppService.updateCarousel(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/carousels/{id}")
    public ApiResponse<Boolean> deleteCarousel(@PathVariable Long id) {
        require("site:carousel:delete");
        return ApiResponse.success(siteManagementAppService.deleteCarousel(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/pages")
    public ApiResponse<PageResponse<SiteVO.PageVO>> pages(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("site:page");
        return ApiResponse.success(siteManagementAppService.pages(currentUser(), status, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/pages")
    @RepeatSubmit
    public ApiResponse<SiteVO.PageVO> createPage(@Valid @RequestBody SiteDTO.PageRequest request) {
        require("site:page:create");
        return ApiResponse.success(siteManagementAppService.createPage(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/pages/{id}")
    @RepeatSubmit
    public ApiResponse<SiteVO.PageVO> updatePage(@PathVariable Long id, @Valid @RequestBody SiteDTO.PageRequest request) {
        require("site:page:update");
        return ApiResponse.success(siteManagementAppService.updatePage(currentUser(), id, request), TraceContext.getRequestId());
    }

    @PostMapping("/pages/{id}/publish")
    @RepeatSubmit
    public ApiResponse<SiteVO.PageVO> publishPage(@PathVariable Long id) {
        require("site:page:publish");
        return ApiResponse.success(siteManagementAppService.publishPage(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/pages/{id}/offline")
    @RepeatSubmit
    public ApiResponse<SiteVO.PageVO> offlinePage(@PathVariable Long id) {
        require("site:page:publish");
        return ApiResponse.success(siteManagementAppService.offlinePage(currentUser(), id), TraceContext.getRequestId());
    }

    @DeleteMapping("/pages/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deletePage(@PathVariable Long id) {
        require("site:page:update");
        return ApiResponse.success(siteManagementAppService.deletePage(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/contents")
    public ApiResponse<PageResponse<SiteVO.ContentVO>> contents(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("site:content");
        return ApiResponse.success(siteManagementAppService.contents(currentUser(), status, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/contents")
    @RepeatSubmit
    public ApiResponse<SiteVO.ContentVO> createContent(@Valid @RequestBody SiteDTO.ContentRequest request) {
        require("site:content:create");
        return ApiResponse.success(siteManagementAppService.createContent(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/contents/{id}")
    @RepeatSubmit
    public ApiResponse<SiteVO.ContentVO> updateContent(@PathVariable Long id, @Valid @RequestBody SiteDTO.ContentRequest request) {
        require("site:content:update");
        return ApiResponse.success(siteManagementAppService.updateContent(currentUser(), id, request), TraceContext.getRequestId());
    }

    @PostMapping("/contents/{id}/publish")
    @RepeatSubmit
    public ApiResponse<SiteVO.ContentVO> publishContent(@PathVariable Long id) {
        require("site:content:publish");
        return ApiResponse.success(siteManagementAppService.publishContent(currentUser(), id), TraceContext.getRequestId());
    }

    @PostMapping("/contents/{id}/offline")
    @RepeatSubmit
    public ApiResponse<SiteVO.ContentVO> offlineContent(@PathVariable Long id) {
        require("site:content:publish");
        return ApiResponse.success(siteManagementAppService.offlineContent(currentUser(), id), TraceContext.getRequestId());
    }

    @DeleteMapping("/contents/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteContent(@PathVariable Long id) {
        require("site:content:update");
        return ApiResponse.success(siteManagementAppService.deleteContent(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/categories")
    public ApiResponse<List<SiteVO.ContentCategoryVO>> categories() {
        require("site:content");
        return ApiResponse.success(siteManagementAppService.categories(currentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/categories")
    @RepeatSubmit
    public ApiResponse<SiteVO.ContentCategoryVO> createCategory(@Valid @RequestBody SiteDTO.CategoryRequest request) {
        require("site:content:create");
        return ApiResponse.success(siteManagementAppService.createCategory(currentUser(), request), TraceContext.getRequestId());
    }

    @GetMapping("/forms")
    public ApiResponse<PageResponse<SiteVO.FormVO>> forms(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("site:form");
        return ApiResponse.success(siteManagementAppService.forms(currentUser(), pageNo, pageSize), TraceContext.getRequestId());
    }

    @PostMapping("/forms")
    @RepeatSubmit
    public ApiResponse<SiteVO.FormVO> createForm(@Valid @RequestBody SiteDTO.FormRequest request) {
        require("site:form:create");
        return ApiResponse.success(siteManagementAppService.createForm(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/forms/{id}")
    @RepeatSubmit
    public ApiResponse<SiteVO.FormVO> updateForm(@PathVariable Long id, @Valid @RequestBody SiteDTO.FormRequest request) {
        require("site:form:update");
        return ApiResponse.success(siteManagementAppService.updateForm(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/forms/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteForm(@PathVariable Long id) {
        require("site:form:delete");
        return ApiResponse.success(siteManagementAppService.deleteForm(currentUser(), id), TraceContext.getRequestId());
    }

    @GetMapping("/submissions")
    public ApiResponse<PageResponse<SiteVO.SubmissionVO>> submissions(@RequestParam(required = false) Long formId, @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "10") long pageSize) {
        require("site:submission");
        return ApiResponse.success(siteManagementAppService.submissions(currentUser(), formId, status, pageNo, pageSize), TraceContext.getRequestId());
    }

    @PutMapping("/submissions/{id}/review")
    @RepeatSubmit
    public ApiResponse<SiteVO.SubmissionVO> reviewSubmission(@PathVariable Long id, @Valid @RequestBody SiteDTO.ReviewRequest request) {
        require("site:submission:review");
        return ApiResponse.success(siteManagementAppService.reviewSubmission(currentUser(), id, request), TraceContext.getRequestId());
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    private void requireSiteUploadPermission(String usage) {
        String normalizedUsage = normalizeSiteUploadUsage(usage);
        if ("carousel".equals(normalizedUsage)) {
            requireAny("site:carousel:create", "site:carousel:update", "site:carousel");
            return;
        }
        requireAny("site:settings:update", "site:settings");
    }

    private void requireAny(String... permissionKeys) {
        CurrentUser user = currentUser();
        Set<String> permissions = user == null ? Set.of() : user.getPermissions();
        if (permissions != null && (permissions.contains("*") || java.util.Arrays.stream(permissionKeys).anyMatch(permissions::contains))) {
            return;
        }
        permissionGuard.requirePermission(user, permissionKeys.length == 0 ? null : permissionKeys[0]);
    }

    private String normalizeSiteUploadUsage(String usage) {
        if (usage == null || usage.isBlank()) {
            return "settings";
        }
        return "carousel".equalsIgnoreCase(usage.trim()) ? "carousel" : "settings";
    }

    private String siteUploadRemark(String usage) {
        return "carousel".equals(usage) ? "官网轮播图片" : "官网设置图片";
    }
}
