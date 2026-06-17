package com.lumira.api.client;

import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileInternalApi {

    
    FileObjectDTO uploadImage(
             MultipartFile file,
             String category,
             String remark
    );

    
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
             Long tenantId,
             Long userId,
             String username
    ) {
        return uploadDocument(file, category, tags, remark, bucket);
    }

    default FileContentDTO readFileContentForUser(
            Long fileId,
            Long tenantId,
            Long userId,
            String username
    ) {
        throw new UnsupportedOperationException("readFileContentForUser is not implemented");
    }

    default FileObjectDTO getFileForUser(
            Long fileId,
            Long tenantId,
            Long userId,
            String username,
            boolean tenantScope,
            boolean downloadCenterScope
    ) {
        throw new UnsupportedOperationException("getFileForUser is not implemented");
    }

    default List<FileObjectDTO> searchFilesForUser(
            Long tenantId,
            Long userId,
            String username,
            String keyword,
            String contentType,
            String status,
            boolean tenantScope,
            int limit
    ) {
        throw new UnsupportedOperationException("searchFilesForUser is not implemented");
    }

    default FileProcessingArtifactDTO readProcessingArtifactForUser(
            Long fileId,
            Long tenantId,
            Long userId,
            String username,
            String artifactType
    ) {
        throw new UnsupportedOperationException("readProcessingArtifactForUser is not implemented");
    }
}
