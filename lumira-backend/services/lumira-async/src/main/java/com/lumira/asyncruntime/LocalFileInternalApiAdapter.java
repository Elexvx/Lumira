package com.lumira.asyncruntime;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.file.app.FileManagementAppService;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
@Primary
class LocalFileInternalApiAdapter implements FileInternalApi {

    private final FileManagementAppService fileManagementAppService;
    private final SecurityContextFacade securityContextFacade;

    LocalFileInternalApiAdapter(@Lazy FileManagementAppService fileManagementAppService, SecurityContextFacade securityContextFacade) {
        this.fileManagementAppService = fileManagementAppService;
        this.securityContextFacade = securityContextFacade;
    }

    @Override
    public FileObjectDTO uploadImage(MultipartFile file, String category, String remark, String bucket) {
        return fileManagementAppService.uploadPublicImage(currentUser(), file, category, remark, bucket);
    }

    @Override
    public FileObjectDTO uploadDocument(MultipartFile file, String category, String tags, String remark, String bucket) {
        return fileManagementAppService.uploadDocument(currentUser(), file, category, tags, remark, bucket);
    }

    @Override
    public FileObjectDTO uploadDocumentForUser(
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            Long userId,
            String username
    ) {
        return fileManagementAppService.uploadDocument(asInternalUser(userId, username), file, category, tags, remark, bucket);
    }

    @Override
    public FileContentDTO readFileContentForUser(Long fileId, Long userId, String username) {
        return fileManagementAppService.readFileContent(asInternalUser(userId, username), fileId, true, false);
    }

    @Override
    public FileProcessingArtifactDTO readProcessingArtifactForUser(Long fileId, Long userId, String username, String artifactType) {
        return fileManagementAppService.readProcessingArtifact(asInternalUser(userId, username), fileId, artifactType, true, false);
    }

    @Override
    public FileObjectDTO getFileForUser(Long fileId, Long userId, String username, boolean sharedScope, boolean downloadCenterScope) {
        return fileManagementAppService.getFile(asInternalUser(userId, username), fileId, sharedScope, downloadCenterScope);
    }

    @Override
    public List<FileObjectDTO> searchFilesForUser(
            Long userId,
            String username,
            String keyword,
            String contentType,
            String status,
            boolean sharedScope,
            int limit
    ) {
        return fileManagementAppService.searchFilesForInternalTool(
                asInternalUser(userId, username),
                keyword,
                contentType,
                status,
                sharedScope,
                limit
        );
    }

    private CurrentUser currentUser() {
        return securityContextFacade.getCurrentUser();
    }

    private CurrentUser asInternalUser(Long userId, String username) {
        return new CurrentUser(userId, username, null, null, 0, true, Set.of("*"));
    }
}
