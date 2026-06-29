package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiKnowledgeDocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class JdbcAiKnowledgeDocumentRepository extends JdbcAiRepositorySupport implements AiKnowledgeDocumentRepository {
    public JdbcAiKnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public Long createDocument(Long knowledgeBaseId, String title, String originalFilename, String extension, String mimeType,
                               long fileSizeBytes, String extractedText, Long operatorId, LocalDateTime now) {
        return insertAndReturnId("""
                        insert into ai_knowledge_document (
                            knowledge_base_id, file_id, title, original_file_name, file_extension,
                            mime_type, file_size_bytes, status, parse_error, extracted_text, extracted_char_count,
                            chunk_count, created_by, updated_by, is_deleted, create_time, update_time
                        ) values (?, null, ?, ?, ?, ?, ?, 'INDEXED', null, ?, ?, 0, ?, ?, 0, ?, ?)
                        """,
                ps -> {
                    ps.setLong(1, knowledgeBaseId);
                    ps.setString(2, title);
                    ps.setString(3, originalFilename);
                    ps.setString(4, extension);
                    ps.setString(5, mimeType);
                    ps.setLong(6, fileSizeBytes);
                    ps.setString(7, extractedText);
                    ps.setInt(8, extractedText.length());
                    ps.setLong(9, operatorId);
                    ps.setLong(10, operatorId);
                    ps.setTimestamp(11, Timestamp.valueOf(now));
                    ps.setTimestamp(12, Timestamp.valueOf(now));
                });
    }

    @Override
    public String findExtractedText(Long knowledgeBaseId, Long documentId) {
        Map<String, Object> document = jdbcTemplate.queryForMap(
                """
                        select extracted_text
                        from ai_knowledge_document
                        where knowledge_base_id = ? and id = ? and is_deleted = 0
                        """,
                knowledgeBaseId,
                documentId
        );
        return String.valueOf(document.getOrDefault("extracted_text", ""));
    }

    @Override
    public void updateChunkCount(Long documentId, int chunkCount, LocalDateTime now) {
        jdbcTemplate.update("update ai_knowledge_document set chunk_count = ?, update_time = ? where id = ?",
                chunkCount, now, documentId);
    }

    @Override
    public void markIndexed(Long documentId, int extractedCharCount, int chunkCount, LocalDateTime now) {
        jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'INDEXED', parse_error = null, extracted_char_count = ?, chunk_count = ?, update_time = ?
                        where id = ?
                        """,
                extractedCharCount,
                chunkCount,
                now,
                documentId
        );
    }

    @Override
    public void softDeleteDocument(Long documentId, LocalDateTime now) {
        jdbcTemplate.update("update ai_knowledge_document set is_deleted = 1, update_time = ? where id = ?",
                now, documentId);
    }
}
