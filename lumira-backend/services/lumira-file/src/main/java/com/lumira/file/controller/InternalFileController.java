package com.lumira.file.controller;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.file.service.FileInternalApiService;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/internal/files")
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class InternalFileController {

    private final FileInternalApiService fileInternalApiService;

    public InternalFileController(FileInternalApiService fileInternalApiService) {
        this.fileInternalApiService = fileInternalApiService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadImage(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket
    ) {
        return fileInternalApiService.uploadImage(file, category, remark, bucket);
    }

    @PostMapping(value = "/images/as-user", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadImageForUser(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("username") String username
    ) {
        requireInternalServicePrincipal();
        return fileInternalApiService.uploadImageForUser(file, category, remark, bucket, userId, userUuid, username);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadDocument(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket
    ) {
        return fileInternalApiService.uploadDocument(file, category, tags, remark, bucket);
    }

    @PostMapping(value = "/documents/as-user", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileObjectDTO uploadDocumentForUser(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "tags", required = false) String tags,
            @RequestParam(name = "remark", required = false) String remark,
            @RequestParam(name = "bucket", required = false) String bucket,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("username") String username
    ) {
        requireInternalServicePrincipal();
        return fileInternalApiService.uploadDocumentForUser(file, category, tags, remark, bucket, userId, userUuid, username);
    }

    @GetMapping("/content")
    public FileContentDTO readFileContentForUser(
            @RequestParam("fileId") Long fileId,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("username") String username,
            @RequestParam(name = "sharedScope", defaultValue = "false") boolean sharedScope
    ) {
        requireInternalServicePrincipal();
        return fileInternalApiService.readFileContentForUser(fileId, userId, userUuid, username, sharedScope);
    }

    @GetMapping("/artifacts")
    public FileProcessingArtifactDTO readProcessingArtifactForUser(
            @RequestParam("fileId") Long fileId,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("username") String username,
            @RequestParam("artifactType") String artifactType,
            @RequestParam(name = "sharedScope", defaultValue = "false") boolean sharedScope
    ) {
        requireInternalServicePrincipal();
        return fileInternalApiService.readProcessingArtifactForUser(fileId, userId, userUuid, username, artifactType, sharedScope);
    }

    @GetMapping("/metadata")
    public FileObjectDTO getFileForUser(
            @RequestParam("fileId") Long fileId,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("username") String username,
            @RequestParam(name = "sharedScope", defaultValue = "false") boolean sharedScope,
            @RequestParam(name = "downloadCenterScope", defaultValue = "false") boolean downloadCenterScope
    ) {
        requireInternalServicePrincipal();
        return fileInternalApiService.getFileForUser(fileId, userId, userUuid, username, sharedScope, downloadCenterScope);
    }

    @GetMapping("/search")
    public List<FileObjectDTO> searchFilesForUser(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("username") String username,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "contentType", required = false) String contentType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "sharedScope", defaultValue = "false") boolean sharedScope,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        requireInternalServicePrincipal();
        return fileInternalApiService.searchFilesForUser(userId, userUuid, username, keyword, contentType, status, sharedScope, limit);
    }

    private void requireInternalServicePrincipal() {
        if (!AuthenticationTrustSupport.isInternalServiceAuthentication(SecurityContextHolder.getContext().getAuthentication())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Internal service token is required");
        }
    }
}
