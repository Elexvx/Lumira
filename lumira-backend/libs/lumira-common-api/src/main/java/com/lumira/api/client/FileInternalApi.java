package com.lumira.api.client;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.file.CompetitionStorageSpaceRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileInternalApi {

    default void ensureCompetitionStorageSpace(CompetitionStorageSpaceRequest request) {
        throw new UnsupportedOperationException("ensureCompetitionStorageSpace is not implemented");
    }

    
    default FileObjectDTO uploadImage(
             MultipartFile file,
             String category,
             String remark
    ) {
        return uploadImage(file, category, remark, null);
    }

    FileObjectDTO uploadImage(
             MultipartFile file,
             String category,
             String remark,
             String bucket
    );

    default FileObjectDTO uploadImageForUser(
             MultipartFile file,
             String category,
             String remark,
             String bucket,
             Long userId,
             String userUuid,
             String username
    ) {
        return uploadImageForUser(file, category, remark, bucket, userId, userUuid, username, null);
    }

    default FileObjectDTO uploadImageForUser(
             MultipartFile file,
             String category,
             String remark,
             String bucket,
             Long userId,
             String userUuid,
             String username,
             Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException("uploadImageForUser is not implemented");
    }

    
    default FileObjectDTO uploadDocument(
             MultipartFile file,
             String category,
             String tags,
             String remark
    ) {
        return uploadDocument(file, category, tags, remark, null);
    }

    FileObjectDTO uploadDocument(
             MultipartFile file,
             String category,
             String tags,
             String remark,
             String bucket
    );

    default FileObjectDTO uploadDocumentForUser(
             MultipartFile file,
             String category,
             String tags,
             String remark,
             String bucket,
             Long userId,
             String userUuid,
             String username
    ) {
        return uploadDocumentForUser(file, category, tags, remark, bucket, userId, userUuid, username, null);
    }

    default FileObjectDTO uploadDocumentForUser(
             MultipartFile file,
             String category,
             String tags,
             String remark,
             String bucket,
             Long userId,
             String userUuid,
             String username,
             Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException("uploadDocumentForUser is not implemented");
    }

    default FileContentDTO readFileContentForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username
    ) {
        return readFileContentForUser(fileId, userId, userUuid, username, false, null);
    }

    default FileContentDTO readFileContentForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope
    ) {
        return readFileContentForUser(fileId, userId, userUuid, username, sharedScope, null);
    }

    default FileContentDTO readFileContentForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException("readFileContentForUser is not implemented");
    }

    default FileContentDTO readFileContentForAuthorizedBusinessReference(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String referenceType,
            Long referenceId,
            Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException(
                "readFileContentForAuthorizedBusinessReference is not implemented"
        );
    }

    default FileObjectDTO getFileForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            boolean downloadCenterScope
    ) {
        return getFileForUser(fileId, userId, userUuid, username, sharedScope, downloadCenterScope, null);
    }

    default FileObjectDTO getFileForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            boolean downloadCenterScope,
            Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException("getFileForUser is not implemented");
    }

    default List<FileObjectDTO> searchFilesForUser(
            Long userId,
            String userUuid,
            String username,
            String keyword,
            String contentType,
            String status,
            boolean sharedScope,
            int limit
    ) {
        return searchFilesForUser(userId, userUuid, username, keyword, contentType, status, sharedScope, limit, null);
    }

    default List<FileObjectDTO> searchFilesForUser(
            Long userId,
            String userUuid,
            String username,
            String keyword,
            String contentType,
            String status,
            boolean sharedScope,
            int limit,
            Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException("searchFilesForUser is not implemented");
    }

    default FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType
    ) {
        return readProcessingArtifactForUser(fileId, userId, userUuid, username, artifactType, false, null);
    }

    default FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType,
            boolean sharedScope
    ) {
        return readProcessingArtifactForUser(fileId, userId, userUuid, username, artifactType, sharedScope, null);
    }

    default FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType,
            boolean sharedScope,
            Long simulatedRoleId
    ) {
        throw new UnsupportedOperationException("readProcessingArtifactForUser is not implemented");
    }
}
