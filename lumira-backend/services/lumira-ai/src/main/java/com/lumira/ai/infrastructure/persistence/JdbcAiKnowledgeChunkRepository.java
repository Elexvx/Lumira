package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiKnowledgeChunkRepository;
import com.lumira.ai.vo.AiKnowledgeReferenceVO;
import com.lumira.common.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class JdbcAiKnowledgeChunkRepository extends JdbcAiRepositorySupport implements AiKnowledgeChunkRepository {
    public JdbcAiKnowledgeChunkRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void softDeleteByDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, LocalDateTime now) {
        int updated = jdbcTemplate.update(
                """
                        update ai_knowledge_chunk
                        set is_deleted = 1, update_time = ?
                        where document_id = ?
                          and knowledge_base_id = ?
                          and is_deleted = 0
                        """ + manageableKnowledgeBaseExists(currentUser),
                softDeleteArgs(currentUser, now, documentId, knowledgeBaseId)
        );
        if (updated < 0) {
            throw new IllegalStateException("AI knowledge chunk delete state changed, please retry");
        }
    }

    @Override
    public void addChunk(Long knowledgeBaseId, Long documentId, int chunkIndex, String content, String searchText,
                         int tokenCount, String embeddingModel, int embeddingDim, String embeddingVectorJson,
                         LocalDateTime now) {
        int inserted = jdbcTemplate.update(
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
        requireSingleWrite(inserted, "AI knowledge chunk changed, please retry");
    }

    @Override
    public List<AiKnowledgeReferenceVO> search(String like, List<Long> knowledgeBaseIds, int limit, CurrentUser currentUser, boolean allPermission) {
        List<Object> args = new ArrayList<>();
        args.add(like);
        args.add(like);
        String idFilter = "";
        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
            idFilter = " and kb.id in (" + "?,".repeat(knowledgeBaseIds.size()).replaceFirst(",$", "") + ")";
            args.addAll(knowledgeBaseIds);
        }
        String accessFilter = accessFilter(currentUser, allPermission, args);
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
                        """ + idFilter + accessFilter + """
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

    private String accessFilter(CurrentUser currentUser, boolean allPermission, List<Object> args) {
        if (allPermission) {
            return "";
        }
        Long actorUserId = currentUser == null ? null : currentUser.getUserId();
        String actorUserUuid = currentUser == null ? null : currentUser.getUserUuid();
        if (actorUserId == null || actorUserId <= 0 || actorUserUuid == null || actorUserUuid.isBlank()) {
            return " and 1 = 0";
        }
        StringBuilder filter = new StringBuilder(" and ((kb.owner_user_id = ? and kb.owner_user_uuid = ?) or kb.visibility_scope = 'PLATFORM' or exists (select 1 from ai_knowledge_base_acl acl where acl.knowledge_base_id = kb.id and acl.is_deleted = 0 and acl.permission in (?,?,?) and (");
        args.add(actorUserId);
        args.add(actorUserUuid.trim());
        args.add("VIEW");
        args.add("USE");
        args.add("MANAGE");

        List<String> subjects = new ArrayList<>();
        subjects.add("(acl.subject_type = 'USER' and acl.subject_id = ?)");
        args.add(actorUserId);

        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        if (!roleIds.isEmpty()) {
            subjects.add("(acl.subject_type = 'ROLE' and acl.subject_id in (" + "?,".repeat(roleIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(roleIds);
        }

        Set<Long> deptIds = new LinkedHashSet<>(currentUser.getDeptIds() == null ? Set.of() : currentUser.getDeptIds());
        if (currentUser.getPrimaryDeptId() != null) {
            deptIds.add(currentUser.getPrimaryDeptId());
        }
        if (!deptIds.isEmpty()) {
            subjects.add("(acl.subject_type = 'DEPARTMENT' and acl.subject_id in (" + "?,".repeat(deptIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(deptIds);
        }

        filter.append(String.join(" or ", subjects)).append(")))");
        return filter.toString();
    }

    private String manageableKnowledgeBaseExists(CurrentUser currentUser) {
        if (hasAllPermission(currentUser)) {
            return """

                      and exists (
                          select 1 from ai_knowledge_base kb
                          where kb.id = ?
                            and kb.is_deleted = 0
                      )
                    """;
        }
        return """

                  and exists (
                      select 1 from ai_knowledge_base kb
                      where kb.id = ?
                        and kb.is_deleted = 0
                        and (
                            (kb.owner_user_id = ? and kb.owner_user_uuid = ?)
                            or exists (
                                select 1
                                from ai_knowledge_base_acl acl
                                where acl.knowledge_base_id = kb.id
                                  and acl.is_deleted = 0
                                  and acl.permission = 'MANAGE'
                                  and (
                                      (acl.subject_type = 'USER' and acl.subject_id = ?)
                                      or (? = 1 and acl.subject_type = 'ROLE' and acl.subject_id in (%s))
                                      or (? = 1 and acl.subject_type = 'DEPARTMENT' and acl.subject_id in (%s))
                                  )
                            )
                        )
                  )
                """.formatted(idPlaceholders(currentUser.getRoleIds()), idPlaceholders(deptIds(currentUser)));
    }

    private Object[] softDeleteArgs(CurrentUser currentUser, LocalDateTime now, Long documentId, Long knowledgeBaseId) {
        List<Object> args = new ArrayList<>();
        args.add(now);
        args.add(documentId);
        args.add(knowledgeBaseId);
        args.add(knowledgeBaseId);
        if (hasAllPermission(currentUser)) {
            return args.toArray();
        }
        args.add(requireTrustedUserId(currentUser));
        args.add(requireTrustedUserUuid(currentUser));
        args.add(requireTrustedUserId(currentUser));
        Set<Long> roleIds = currentUser.getRoleIds();
        args.add(roleIds.isEmpty() ? 0 : 1);
        args.addAll(roleIds);
        Set<Long> deptIds = deptIds(currentUser);
        args.add(deptIds.isEmpty() ? 0 : 1);
        args.addAll(deptIds);
        return args.toArray();
    }

    private String idPlaceholders(Set<Long> ids) {
        return ids.isEmpty() ? "0" : "?,".repeat(ids.size()).replaceFirst(",$", "");
    }

    private Set<Long> deptIds(CurrentUser currentUser) {
        Set<Long> deptIds = new LinkedHashSet<>(currentUser.getDeptIds());
        if (currentUser.getPrimaryDeptId() != null) {
            deptIds.add(currentUser.getPrimaryDeptId());
        }
        return deptIds;
    }

    private Long objectLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
