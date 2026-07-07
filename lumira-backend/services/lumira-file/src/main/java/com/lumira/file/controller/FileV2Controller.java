package com.lumira.file.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.common.api.ApiResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.repeatsubmit.RepeatSubmit;
import com.lumira.file.app.FileManagementAppService;
import com.lumira.file.dto.FileStorageSpaceRequest;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/files")
public class FileV2Controller {
    private static final String STATUS_ENABLED = "ENABLED";

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final FileUploadMetrics fileUploadMetrics;
    private final SystemInternalApi systemInternalApi;
    private final boolean enforceTrustedUserResolution;

    public FileV2Controller(
            FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileUploadMetrics fileUploadMetrics
    ) {
        this(fileManagementAppService, securityContextFacade, permissionGuard, fileUploadMetrics, null, false);
    }

    @Autowired
    public FileV2Controller(
            FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileUploadMetrics fileUploadMetrics,
            SystemInternalApi systemInternalApi
    ) {
        this(fileManagementAppService, securityContextFacade, permissionGuard, fileUploadMetrics, systemInternalApi, true);
    }

    private FileV2Controller(
            FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileUploadMetrics fileUploadMetrics,
            SystemInternalApi systemInternalApi,
            boolean enforceTrustedUserResolution
    ) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.fileUploadMetrics = fileUploadMetrics;
        this.systemInternalApi = systemInternalApi;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @GetMapping
    public ApiResponse<PageResponse<FileObjectDTO>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "fileExtension", required = false) String fileExtension,
            @RequestParam(name = "previewMode", required = false) String previewMode,
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "sortField", required = false) String sortField,
            @RequestParam(name = "sortOrder", required = false) String sortOrder,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        CurrentUser currentUser = currentUser();
        require(resolveReadPermission(scope));
        return ApiResponse.success(
                fileManagementAppService.listFiles(
                        currentUser,
                        keyword,
                        category,
                        fileExtension,
                        previewMode,
                        bucket,
                        scope,
                        pageNo,
                        pageSize,
                        sortField,
                        sortOrder
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/storage-spaces")
    public ApiResponse<PageResponse<StorageSpaceDTO>> storageSpaces(
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "50") long pageSize
    ) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.listStorageSpaces(currentUser(), pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/storage-spaces/{storageKey}")
    public ApiResponse<StorageSpaceDTO> storageSpace(@PathVariable("storageKey") String storageKey) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.getStorageSpace(currentUser(), storageKey),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/storage-spaces")
    @RepeatSubmit
    public ApiResponse<StorageSpaceDTO> createStorageSpace(@RequestBody FileStorageSpaceRequest request) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.createStorageSpace(currentUser(), request),
                TraceContext.getRequestId()
        );
    }

    @PutMapping("/storage-spaces/{id}")
    @RepeatSubmit
    public ApiResponse<StorageSpaceDTO> updateStorageSpace(
            @PathVariable("id") @Positive Long id,
            @RequestBody FileStorageSpaceRequest request
    ) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.updateStorageSpace(currentUser(), id, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/storage-spaces/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteStorageSpace(@PathVariable("id") @Positive Long id) {
        require("system:file:manage:delete");
        fileManagementAppService.deleteStorageSpace(currentUser(), id);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/storage-spaces/{id}/test")
    @RepeatSubmit
    public ApiResponse<FileStorageSpaceRequest.TestResult> testStorageSpace(@PathVariable("id") @Positive Long id) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.testStorageSpace(currentUser(), id),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<FileObjectDTO> detail(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean sharedScope = isSharedScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        CurrentUser currentUser = currentUser();
        require(resolveReadPermission(scope));
        return ApiResponse.success(
                fileManagementAppService.getFile(currentUser, id, sharedScope, downloadCenterScope),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean sharedScope = isSharedScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        CurrentUser currentUser = currentUser();
        require(resolveReadPermission(scope));
        FileObjectDTO file = fileManagementAppService.getFile(currentUser, id, sharedScope, downloadCenterScope);
        var path = fileManagementAppService.resolveFilePath(currentUser, id, sharedScope, downloadCenterScope);
        return fileResponse(file, path, ContentDisposition.attachment()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean sharedScope = isSharedScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        CurrentUser currentUser = currentUser();
        require(resolveReadPermission(scope));
        FileObjectDTO file = fileManagementAppService.getPreviewableFile(currentUser, id, sharedScope, downloadCenterScope);
        var path = fileManagementAppService.resolveFilePath(currentUser, id, sharedScope, downloadCenterScope);
        return fileResponse(file, path, ContentDisposition.inline()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RepeatSubmit
    public ApiResponse<FileObjectDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        CurrentUser currentUser = currentUser();
        Instant startedAt = Instant.now();
        try {
            require(resolveUploadPermission(scope));
            ApiResponse<FileObjectDTO> response = ApiResponse.success(
                    fileManagementAppService.uploadFile(currentUser, file, category, tags, remark, bucket, scope),
                    TraceContext.getRequestId()
            );
            fileUploadMetrics.recordSucceeded(scope, Duration.between(startedAt, Instant.now()));
            return response;
        } catch (RuntimeException exception) {
            fileUploadMetrics.recordFailed(scope, Duration.between(startedAt, Instant.now()));
            throw exception;
        }
    }

    @DeleteMapping("/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> delete(
            @PathVariable("id") @Positive Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean sharedScope = isSharedScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        CurrentUser currentUser = currentUser();
        require(downloadCenterScope
                ? "download:center:delete"
                : FileManagementAppService.SCOPE_SHARED.equalsIgnoreCase(scope)
                ? "system:file:manage:delete"
                : "system:file:delete");
        fileManagementAppService.deleteFile(currentUser, id, sharedScope, downloadCenterScope);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    private ResponseEntity<Resource> fileResponse(FileObjectDTO file, java.nio.file.Path path, ContentDisposition contentDisposition) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.mimeType() != null && !file.mimeType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(file.mimeType());
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(new FileSystemResource(path));
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
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (systemInternalApi == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
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
        if (!StringUtils.hasText(userSnapshot.username())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        Long simulatedRoleId = currentUser.getSimulatedRoleId();
        if (simulatedRoleId != null && simulatedRoleId <= 0) {
            simulatedRoleId = null;
        }
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim())
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, userSnapshot.userUuid().trim(), simulatedRoleId);
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(userSnapshot.userId());
        currentUser.setUserUuid(userSnapshot.userUuid().trim());
        currentUser.setUsername(userSnapshot.username().trim());
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

    private String resolveReadPermission(String scope) {
        if (FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope)) {
            return "download:center:view";
        }
        if (FileManagementAppService.SCOPE_SHARED.equalsIgnoreCase(scope)) {
            return "system:file:manage";
        }
        return "system:file:view";
    }

    private String resolveUploadPermission(String scope) {
        if (FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope)) {
            return "download:center:create";
        }
        if ("PUBLIC".equalsIgnoreCase(scope)) {
            return "system:file:publish";
        }
        return "system:file:upload";
    }

    private boolean isSharedScope(String scope) {
        return FileManagementAppService.SCOPE_SHARED.equalsIgnoreCase(scope)
                || FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
    }
}
