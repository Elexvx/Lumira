package com.lumira.saas.modules.localization.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.saas.modules.localization.app.LocalizationManagementAppService;
import com.lumira.saas.modules.localization.dto.LocalizationDTO;
import com.lumira.saas.modules.localization.vo.LocalizationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/localization")
public class LocalizationV2Controller {

    private final LocalizationManagementAppService localizationManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public LocalizationV2Controller(
            LocalizationManagementAppService localizationManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.localizationManagementAppService = localizationManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/runtime/{localeCode}")
    public ApiResponse<LocalizationVO.RuntimeBundleVO> runtime(@PathVariable("localeCode") String localeCode) {
        return ApiResponse.success(localizationManagementAppService.runtimeBundle(localeCode), TraceContext.getRequestId());
    }

    @GetMapping("/languages")
    public ApiResponse<List<LocalizationVO.LanguageVO>> listLanguages() {
        require("localization:view");
        return ApiResponse.success(localizationManagementAppService.listLanguages(), TraceContext.getRequestId());
    }

    @GetMapping("/namespaces")
    public ApiResponse<List<LocalizationVO.NamespaceVO>> listNamespaces(@RequestParam(name = "localeCode", required = false) String localeCode) {
        require("localization:view");
        return ApiResponse.success(localizationManagementAppService.listNamespaces(localeCode), TraceContext.getRequestId());
    }

    @GetMapping("/entries")
    public ApiResponse<LocalizationVO.EntryPageResponse> listEntries(
            @RequestParam(name = "localeCode", required = false) String localeCode,
            @RequestParam(name = "namespaceCode", required = false) String namespaceCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "translationStatus", required = false) String translationStatus,
            @RequestParam(name = "sortField", required = false) String sortField,
            @RequestParam(name = "sortOrder", required = false) String sortOrder,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") long pageSize
    ) {
        require("localization:view");
        return ApiResponse.success(
                localizationManagementAppService.listEntries(localeCode, namespaceCode, keyword, status, translationStatus, pageNo, pageSize, sortField, sortOrder),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/entries")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.EntryVO> saveEntry(@Valid @RequestBody LocalizationDTO.EntryUpsertRequest request) {
        require("localization:update");
        return ApiResponse.success(localizationManagementAppService.saveEntry(request), TraceContext.getRequestId());
    }

    @GetMapping("/releases")
    public ApiResponse<List<LocalizationVO.ReleaseVO>> listReleases(@RequestParam(name = "localeCode", required = false) String localeCode) {
        require("localization:view");
        return ApiResponse.success(localizationManagementAppService.listReleases(localeCode), TraceContext.getRequestId());
    }

    @PostMapping("/publish")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.ReleaseVO> publish(@Valid @RequestBody LocalizationDTO.PublishRequest request) {
        require("localization:publish");
        return ApiResponse.success(localizationManagementAppService.publish(request, securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/rollback")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.ReleaseVO> rollback(@Valid @RequestBody LocalizationDTO.RollbackRequest request) {
        require("localization:rollback");
        return ApiResponse.success(localizationManagementAppService.rollback(request, securityContextFacade.getCurrentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/sync")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.SyncResultVO> sync(@Valid @RequestBody LocalizationDTO.SyncRequest request) {
        require("localization:sync");
        return ApiResponse.success(localizationManagementAppService.sync(request), TraceContext.getRequestId());
    }

    @PostMapping("/languages")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.LanguageVO> saveLanguage(@Valid @RequestBody LocalizationDTO.LanguageUpsertRequest request) {
        require("localization:update");
        return ApiResponse.success(localizationManagementAppService.saveLanguage(null, request), TraceContext.getRequestId());
    }

    @PostMapping("/namespaces")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.NamespaceVO> saveNamespace(@Valid @RequestBody LocalizationDTO.NamespaceUpsertRequest request) {
        require("localization:update");
        return ApiResponse.success(localizationManagementAppService.saveNamespace(null, request), TraceContext.getRequestId());
    }

    @PostMapping("/entries/{id}")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.EntryVO> updateEntry(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody LocalizationDTO.EntryUpsertRequest request
    ) {
        require("localization:update");
        request.setId(id);
        return ApiResponse.success(localizationManagementAppService.saveEntry(request), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
