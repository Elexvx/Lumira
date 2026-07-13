package com.lumira.file.infrastructure;

import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.file.repository.FileProcessingArtifactRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFileProcessingArtifactRepository implements FileProcessingArtifactRepository {
    private final JdbcTemplate database;

    public JdbcFileProcessingArtifactRepository(JdbcTemplate database) { this.database = database; }

    @Override
    public Optional<FileProcessingArtifactDTO> findLatest(Long fileId, String artifactType) {
        return database.query("""
                select id, file_id, task_type, artifact_type, artifact_path,
                       content_text, content_length, updated_at
                from file_processing_artifact
                where file_id = ? and artifact_type = ? and deleted = 0
                order by updated_at desc, id desc
                limit 1
                """, (rs, rowNum) -> new FileProcessingArtifactDTO(
                rs.getLong("id"), rs.getLong("file_id"), rs.getString("task_type"),
                rs.getString("artifact_type"), rs.getString("artifact_path"), rs.getString("content_text"),
                rs.getInt("content_length"), rs.getObject("updated_at", LocalDateTime.class)),
                fileId, artifactType).stream().findFirst();
    }
}
