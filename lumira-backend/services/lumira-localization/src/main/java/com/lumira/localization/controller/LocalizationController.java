package com.lumira.localization.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.common.web.TraceContext;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.security.PermissionGuard;
import com.lumira.localization.app.LocalizationManagementAppService;
import com.lumira.localization.dto.LocalizationDTO;
import com.lumira.localization.vo.LocalizationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/localization")
public class LocalizationController {
    private static final String STATUS_ENABLED = "ENABLED";

    private final LocalizationManagementAppService localizationManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final SystemInternalApi systemInternalApi;

    public LocalizationController(
            LocalizationManagementAppService localizationManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this(localizationManagementAppService, securityContextFacade, permissionGuard, null);
    }

    @Autowired
    public LocalizationController(
            LocalizationManagementAppService localizationManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            SystemInternalApi systemInternalApi
    ) {
        this.localizationManagementAppService = localizationManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.systemInternalApi = systemInternalApi;
    }

    @GetMapping("/languages")
    public ApiResponse<List<LocalizationVO.LanguageVO>> listLanguages() {
        require("localization:view");
        return ApiResponse.success(localizationManagementAppService.listLanguages(), TraceContext.getRequestId());
    }

    @PostMapping("/languages")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.LanguageVO> createLanguage(@Valid @RequestBody LocalizationDTO.LanguageUpsertRequest request) {
        require("localization:create");
        return ApiResponse.success(localizationManagementAppService.saveLanguage(currentUser(), null, request), TraceContext.getRequestId());
    }

    @PutMapping("/languages/{id}")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.LanguageVO> updateLanguage(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody LocalizationDTO.LanguageUpsertRequest request
    ) {
        require("localization:update");
        return ApiResponse.success(localizationManagementAppService.saveLanguage(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/languages/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteLanguage(@PathVariable("id") @Positive Long id) {
        require("localization:delete");
        localizationManagementAppService.deleteLanguage(currentUser(), id);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @GetMapping("/namespaces")
    public ApiResponse<List<LocalizationVO.NamespaceVO>> listNamespaces(@RequestParam(name = "localeCode", required = false) String localeCode) {
        require("localization:view");
        return ApiResponse.success(localizationManagementAppService.listNamespaces(localeCode), TraceContext.getRequestId());
    }

    @PostMapping("/namespaces")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.NamespaceVO> createNamespace(@Valid @RequestBody LocalizationDTO.NamespaceUpsertRequest request) {
        require("localization:create");
        return ApiResponse.success(localizationManagementAppService.saveNamespace(currentUser(), null, request), TraceContext.getRequestId());
    }

    @PutMapping("/namespaces/{id}")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.NamespaceVO> updateNamespace(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody LocalizationDTO.NamespaceUpsertRequest request
    ) {
        require("localization:update");
        return ApiResponse.success(localizationManagementAppService.saveNamespace(currentUser(), id, request), TraceContext.getRequestId());
    }

    @DeleteMapping("/namespaces/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteNamespace(@PathVariable("id") @Positive Long id) {
        require("localization:delete");
        localizationManagementAppService.deleteNamespace(currentUser(), id);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
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
    public ApiResponse<LocalizationVO.EntryVO> createEntry(@Valid @RequestBody LocalizationDTO.EntryUpsertRequest request) {
        require("localization:create");
        return ApiResponse.success(localizationManagementAppService.saveEntry(currentUser(), request), TraceContext.getRequestId());
    }

    @PutMapping("/entries/{id}")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.EntryVO> updateEntry(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody LocalizationDTO.EntryUpsertRequest request
    ) {
        require("localization:update");
        request.setId(id);
        return ApiResponse.success(localizationManagementAppService.saveEntry(currentUser(), request), TraceContext.getRequestId());
    }

    @DeleteMapping("/entries/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteEntry(@PathVariable("id") @Positive Long id) {
        require("localization:delete");
        localizationManagementAppService.deleteEntry(currentUser(), id);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/sync")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.SyncResultVO> sync(@Valid @RequestBody LocalizationDTO.SyncRequest request) {
        require("localization:sync");
        return ApiResponse.success(localizationManagementAppService.sync(currentUser(), request), TraceContext.getRequestId());
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
        return ApiResponse.success(localizationManagementAppService.publish(request, currentUser()), TraceContext.getRequestId());
    }

    @PostMapping("/rollback")
    @RepeatSubmit
    public ApiResponse<LocalizationVO.ReleaseVO> rollback(@Valid @RequestBody LocalizationDTO.RollbackRequest request) {
        require("localization:rollback");
        return ApiResponse.success(localizationManagementAppService.rollback(request, currentUser()), TraceContext.getRequestId());
    }

    @GetMapping("/runtime/{localeCode}")
    public ApiResponse<LocalizationVO.RuntimeBundleVO> runtime(@PathVariable("localeCode") String localeCode) {
        return ApiResponse.success(localizationManagementAppService.runtimeBundle(localeCode), TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(currentUser(), permissionKey);
    }

    private CurrentUser currentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return refreshTrustedCurrentUser(currentUser);
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser) || systemInternalApi == null) {
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userId.equals(userSnapshot.userId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid())
                || !normalizedUserUuid.equals(userSnapshot.userUuid().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!StringUtils.hasText(userSnapshot.status())
                || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        PermissionSnapshotDTO permissionSnapshot = systemInternalApi.permissionSnapshot(
                userId,
                userSnapshot.userUuid().trim()
        );
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username());
        currentUser.setPermissions(permissionSnapshot.permissions() == null ? Set.of() : Set.copyOf(permissionSnapshot.permissions()));
        currentUser.setRoleIds(permissionSnapshot.roleIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.roleIds()));
        currentUser.setPrimaryDeptId(permissionSnapshot.primaryDeptId());
        currentUser.setDeptIds(permissionSnapshot.deptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.deptIds()));
        currentUser.setDescendantDeptIds(
                permissionSnapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(permissionSnapshot.descendantDeptIds())
        );
        currentUser.setDataScopes(permissionSnapshot.dataScopes() == null ? List.of() : List.copyOf(permissionSnapshot.dataScopes()));
        currentUser.setPermissionsVersion(permissionSnapshot.version().trim());
        currentUser.setDefaultHomePath(permissionSnapshot.defaultHomePath());
        return currentUser;
    }
}
