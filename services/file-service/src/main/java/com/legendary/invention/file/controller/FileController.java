package com.legendary.invention.file.controller;

import com.legendary.invention.api.file.FileObjectDTO;
import com.legendary.invention.common.api.ApiResponse;
import com.legendary.invention.common.constant.HeaderConstants;
import com.legendary.invention.common.security.SecurityContextFacade;
import com.legendary.invention.common.security.PermissionGuard;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.common.web.TraceContext;
import com.legendary.invention.file.app.FileManagementAppService;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;
    private final PermissionGuard permissionGuard;

    public FileController(
            FileManagementAppService fileManagementAppService,
            SecurityContextFacade securityContextFacade,
            PermissionGuard permissionGuard
    ) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public ApiResponse<PageResponse<FileObjectDTO>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "fileExtension", required = false) String fileExtension,
            @RequestParam(name = "previewMode", required = false) String previewMode,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestParam(name = "sortField", required = false) String sortField,
            @RequestParam(name = "sortOrder", required = false) String sortOrder,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize
    ) {
        boolean tenantScope = FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope);
        require(tenantScope ? "system:file:manage" : "system:file:view");
        return ApiResponse.success(
                fileManagementAppService.listFiles(
                        securityContextFacade.getCurrentUser(),
                        keyword,
                        category,
                        fileExtension,
                        previewMode,
                        scope,
                        pageNo,
                        pageSize,
                        sortField,
                        sortOrder
                ),
                TraceContext.getRequestId()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<FileObjectDTO> detail(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean tenantScope = FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope);
        require(tenantScope ? "system:file:manage" : "system:file:view");
        return ApiResponse.success(fileManagementAppService.getFile(securityContextFacade.getCurrentUser(), id, tenantScope), TraceContext.getRequestId());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("id") Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean tenantScope = FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope);
        require(tenantScope ? "system:file:manage" : "system:file:view");
        FileObjectDTO file = fileManagementAppService.getFile(securityContextFacade.getCurrentUser(), id, tenantScope);
        var path = fileManagementAppService.resolveFilePath(securityContextFacade.getCurrentUser(), id, tenantScope);
        String contentType = file.mimeType();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileObjectDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark
    ) {
        require("system:file:upload");
        return ApiResponse.success(
                fileManagementAppService.uploadDocument(securityContextFacade.getCurrentUser(), file, category, tags, remark),
                TraceContext.getRequestId()
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(
            @PathVariable("id") @Positive Long id,
            @RequestParam(name = "scope", required = false) String scope
    ) {
        boolean tenantScope = FileManagementAppService.SCOPE_TENANT.equalsIgnoreCase(scope);
        require(tenantScope ? "system:file:manage:delete" : "system:file:delete");
        fileManagementAppService.deleteFile(securityContextFacade.getCurrentUser(), id, tenantScope);
        return ApiResponse.success(Boolean.TRUE, TraceContext.getRequestId());
    }

    private void require(String permissionKey) {
        permissionGuard.requirePermission(securityContextFacade.getCurrentUser(), permissionKey);
    }
}
