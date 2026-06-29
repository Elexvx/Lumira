package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiKnowledgeChunkRepository;
import com.lumira.ai.vo.AiKnowledgeReferenceVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcAiKnowledgeChunkRepository extends JdbcAiRepositorySupport implements AiKnowledgeChunkRepository {
    public JdbcAiKnowledgeChunkRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void softDeleteByDocument(Long documentId, LocalDateTime now) {
        jdbcTemplate.update(
                "update ai_knowledge_chunk set is_deleted = 1, update_time = ? where document_id = ? and is_deleted = 0",
                now,
                documentId
        );
    }

    @Override
    public void addChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String searchText,
                         int tokenCount, String embeddingModel, int embeddingDim, String embeddingVectorJson,
                         LocalDateTime now) {
        jdbcTemplate.update(
                """
                        insert into ai_knowledge_chunk (
                            knowledge_base_id, document_id, chunk_index, content, search_text,
                            token_count, embedding_model, embedding_dim, embedding_vector_json, vector_indexed_at,
                            is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                knowledgeBaseId,
                documentId,
                chunkIndex,
                content,
                searchText,
                tokenCount,
                embeddingModel,
                embeddingDim,
                embeddingVectorJson,
                now,
                now,
                now
        );
    }

    @Override
    public List<AiKnowledgeReferenceVO> search(String like, List<Long> knowledgeBaseIds, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(like);
        args.add(like);
        String idFilter = "";
        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
            idFilter = " and kb.id in (" + "?,".repeat(knowledgeBaseIds.size()).replaceFirst(",$", "") + ")";
            args.addAll(knowledgeBaseIds);
        }
        args.add(limit);
        return jdbcTemplate.query(
                """
                        select c.id as chunk_id, kb.id as knowledge_base_id, kb.name as knowledge_base_name,
                               d.id as document_id, d.title as document_title, d.file_id, d.original_file_name,
                               c.chunk_index, c.content
                        from ai_knowledge_chunk c
                        join ai_knowledge_document d
                          on d.id = c.document_id and d.is_deleted = 0
                        join ai_knowledge_base kb
                          on kb.id = c.knowledge_base_id and kb.is_deleted = 0
                        where c.is_deleted = 0
                          and (c.search_text like ? or c.content like ?)
                        """ + idFilter + """
                        order by c.update_time desc, c.id desc
                        limit ?
                        """,
                (rs, rowNum) -> new AiKnowledgeReferenceVO(
                        rs.getLong("chunk_id"),
                        rs.getLong("knowledge_base_id"),
                        rs.getString("knowledge_base_name"),
                        rs.getLong("document_id"),
                        rs.getString("document_title"),
                        objectLong(rs, "file_id"),
                        rs.getString("original_file_name"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                ),
                args.toArray()
        );
    }

    private Long objectLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
