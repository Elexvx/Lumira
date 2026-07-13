package com.lumira.file.repository;

import com.lumira.api.file.FileProcessingArtifactDTO;
import java.util.Optional;

public interface FileProcessingArtifactRepository {
    Optional<FileProcessingArtifactDTO> findLatest(Long fileId, String artifactType);
}
