package com.lumira.api.client;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileInternalApi {

    
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
        throw new UnsupportedOperationException("uploadDocumentForUser is not implemented");
    }

    default FileContentDTO readFileContentForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username
    ) {
        return readFileContentForUser(fileId, userId, userUuid, username, false);
    }

    default FileContentDTO readFileContentForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope
    ) {
        throw new UnsupportedOperationException("readFileContentForUser is not implemented");
    }

    default FileObjectDTO getFileForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            boolean sharedScope,
            boolean downloadCenterScope
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
        throw new UnsupportedOperationException("searchFilesForUser is not implemented");
    }

    default FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType
    ) {
        return readProcessingArtifactForUser(fileId, userId, userUuid, username, artifactType, false);
    }

    default FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long userId,
            String userUuid,
            String username,
            String artifactType,
            boolean sharedScope
    ) {
        throw new UnsupportedOperationException("readProcessingArtifactForUser is not implemented");
    }
}
