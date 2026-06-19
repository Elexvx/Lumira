package com.lumira.file.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/internal/files")
public class InternalFileController implements com.lumira.api.client.FileInternalApi {

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;

    public InternalFileController(FileManagementAppService fileManagementAppService, SecurityContextFacade securityContextFacade) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadImage(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "remark", required = false) String remark
    ) {
        return fileManagementAppService.uploadPublicImage(securityContextFacade.getCurrentUser(), file, category, remark);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadDocument(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket
    ) {
        return fileManagementAppService.uploadDocument(securityContextFacade.getCurrentUser(), file, category, tags, remark, bucket);
    }

    @PostMapping(value = "/documents/as-user", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadDocumentForUser(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("username") String username
    ) {
        CurrentUser currentUser = new CurrentUser(userId, username, tenantId, null, 0, true, Set.of("*"));
        return fileManagementAppService.uploadDocument(currentUser, file, category, tags, remark, bucket);
    }

    @GetMapping("/content")
    public FileContentDTO readFileContentForUser(
            @RequestParam("fileId") Long fileId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("username") String username
    ) {
        CurrentUser currentUser = new CurrentUser(userId, username, tenantId, null, 0, true, Set.of("*"));
        return fileManagementAppService.readFileContent(currentUser, fileId, true, false);
    }

    @GetMapping("/artifacts")
    public FileProcessingArtifactDTO readProcessingArtifactForUser(
            @RequestParam("fileId") Long fileId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("username") String username,
            @RequestParam("artifactType") String artifactType
    ) {
        CurrentUser currentUser = new CurrentUser(userId, username, tenantId, null, 0, true, Set.of("*"));
        return fileManagementAppService.readProcessingArtifact(currentUser, fileId, artifactType, true, false);
    }

    @GetMapping("/metadata")
    public FileObjectDTO getFileForUser(
            @RequestParam("fileId") Long fileId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("username") String username,
            @RequestParam(name = "tenantScope", defaultValue = "true") boolean tenantScope,
            @RequestParam(name = "downloadCenterScope", defaultValue = "false") boolean downloadCenterScope
    ) {
        CurrentUser currentUser = new CurrentUser(userId, username, tenantId, null, 0, true, Set.of("*"));
        return fileManagementAppService.getFile(currentUser, fileId, tenantScope, downloadCenterScope);
    }

    @GetMapping("/search")
    public List<FileObjectDTO> searchFilesForUser(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("username") String username,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "contentType", required = false) String contentType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tenantScope", defaultValue = "false") boolean tenantScope,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        CurrentUser currentUser = new CurrentUser(userId, username, tenantId, null, 0, true, Set.of("*"));
        return fileManagementAppService.searchFilesForInternalTool(currentUser, keyword, contentType, status, tenantScope, limit);
    }
}
