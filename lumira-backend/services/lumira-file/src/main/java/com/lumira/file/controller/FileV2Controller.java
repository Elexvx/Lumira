package com.lumira.file.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.common.api.ApiResponse;
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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;
    private final FileUploadMetrics fileUploadMetrics;

    public FileV2Controller(
            FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard,
            FileUploadMetrics fileUploadMetrics
    ) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
        this.fileUploadMetrics = fileUploadMetrics;
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
        require(resolveReadPermission(scope));
        return ApiResponse.success(
                fileManagementAppService.listFiles(
                        securityContextFacade.getCurrentUser(),
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
                fileManagementAppService.listStorageSpaces(securityContextFacade.getCurrentUser(), pageNo, pageSize),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/storage-spaces/{storageKey}")
    public ApiResponse<StorageSpaceDTO> storageSpace(@PathVariable("storageKey") String storageKey) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.getStorageSpace(securityContextFacade.getCurrentUser(), storageKey),
                TraceContext.getRequestId()
        );
    }

    @PostMapping("/storage-spaces")
    @RepeatSubmit
    public ApiResponse<StorageSpaceDTO> createStorageSpace(@RequestBody FileStorageSpaceRequest request) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.createStorageSpace(securityContextFacade.getCurrentUser(), request),
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
                fileManagementAppService.updateStorageSpace(securityContextFacade.getCurrentUser(), id, request),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/storage-spaces/{id}")
    @RepeatSubmit
    public ApiResponse<Boolean> deleteStorageSpace(@PathVariable("id") @Positive Long id) {
        require("system:file:manage:delete");
        fileManagementAppService.deleteStorageSpace(securityContextFacade.getCurrentUser(), id);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    @PostMapping("/storage-spaces/{id}/test")
    @RepeatSubmit
    public ApiResponse<FileStorageSpaceRequest.TestResult> testStorageSpace(@PathVariable("id") @Positive Long id) {
        require("system:file:manage");
        return ApiResponse.success(
                fileManagementAppService.testStorageSpace(securityContextFacade.getCurrentUser(), id),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<FileObjectDTO> detail(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean tenantScope = isTenantWideScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        require(resolveReadPermission(scope));
        return ApiResponse.success(
                fileManagementAppService.getFile(securityContextFacade.getCurrentUser(), id, tenantScope, downloadCenterScope),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean tenantScope = isTenantWideScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        require(resolveReadPermission(scope));
        FileObjectDTO file = fileManagementAppService.getFile(securityContextFacade.getCurrentUser(), id, tenantScope, downloadCenterScope);
        var path = fileManagementAppService.resolveFilePath(securityContextFacade.getCurrentUser(), id, tenantScope, downloadCenterScope);
        return fileResponse(file, path, ContentDisposition.attachment()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build());
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean tenantScope = isTenantWideScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        require(resolveReadPermission(scope));
        FileObjectDTO file = fileManagementAppService.getPreviewableFile(securityContextFacade.getCurrentUser(), id, tenantScope, downloadCenterScope);
        var path = fileManagementAppService.resolveFilePath(securityContextFacade.getCurrentUser(), id, tenantScope, downloadCenterScope);
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
        Instant startedAt = Instant.now();
        try {
            require(FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope) ? "download:center:create" : "system:file:upload");
            ApiResponse<FileObjectDTO> response = ApiResponse.success(
                    fileManagementAppService.uploadFile(securityContextFacade.getCurrentUser(), file, category, tags, remark, bucket, scope),
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
        boolean tenantScope = isTenantWideScope(scope);
        boolean downloadCenterScope = FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        require(downloadCenterScope
                ? "download:center:delete"
                : FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope)
                ? "system:file:manage:delete"
                : "system:file:delete");
        fileManagementAppService.deleteFile(securityContextFacade.getCurrentUser(), id, tenantScope, downloadCenterScope);
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
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }

    private String resolveReadPermission(String scope) {
        if (FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope)) {
            return "download:center:view";
        }
        if (FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope)) {
            return "system:file:manage";
        }
        return "system:file:view";
    }

    private boolean isTenantWideScope(String scope) {
        return FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope)
                || FileManagementAppService.SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
    }
}
