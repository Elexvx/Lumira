package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiKnowledgeDocumentRepository;
import com.lumira.common.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class JdbcAiKnowledgeDocumentRepository extends JdbcAiRepositorySupport implements AiKnowledgeDocumentRepository {
    public JdbcAiKnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public Long createDocument(Long knowledgeBaseId, String title, String originalFilename, String extension, String mimeType,
                               long fileSizeBytes, String extractedText, Long operatorId, String operatorUuid, LocalDateTime now) {
        return insertAndReturnId("""
                        insert into ai_knowledge_document (
                            knowledge_base_id, file_id, title, original_file_name, file_extension,
                            mime_type, file_size_bytes, status, parse_error, extracted_text, extracted_char_count,
                            chunk_count, created_by, created_by_uuid, updated_by, updated_by_uuid, is_deleted, create_time, update_time
                        ) values (?, null, ?, ?, ?, ?, ?, 'INDEXED', null, ?, ?, 0, ?, ?, ?, ?, 0, ?, ?)
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
                    ps.setString(10, operatorUuid);
                    ps.setLong(11, operatorId);
                    ps.setString(12, operatorUuid);
                    ps.setTimestamp(13, Timestamp.valueOf(now));
                    ps.setTimestamp(14, Timestamp.valueOf(now));
                });
    }

    @Override
    public String findExtractedText(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Map<String, Object> document = jdbcTemplate.queryForMap(
                """
                        select extracted_text
                        from ai_knowledge_document
                        where knowledge_base_id = ?
                          and id = ?
                          and is_deleted = 0
                        """ + manageableKnowledgeBaseExists(currentUser),
                readArgs(currentUser, knowledgeBaseId, documentId)
        );
        return String.valueOf(document.getOrDefault("extracted_text", ""));
    }

    @Override
    public void updateChunkCount(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, int chunkCount, LocalDateTime now) {
        int updated = jdbcTemplate.update("""
                        update ai_knowledge_document
                        set chunk_count = ?, update_time = ?
                        where id = ?
                          and knowledge_base_id = ?
                          and is_deleted = 0
                        """ + manageableKnowledgeBaseExists(currentUser),
                updateArgs(currentUser, knowledgeBaseId, chunkCount, now, documentId, knowledgeBaseId));
        requireUpdated(updated, "AI knowledge document chunk state changed, please retry");
    }

    @Override
    public void markIndexed(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, int extractedCharCount, int chunkCount, LocalDateTime now) {
        int updated = jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'INDEXED', parse_error = null, extracted_char_count = ?, chunk_count = ?, update_time = ?
                        where id = ? and knowledge_base_id = ?
                          and is_deleted = 0
                        """ + manageableKnowledgeBaseExists(currentUser),
                updateArgs(currentUser, knowledgeBaseId, extractedCharCount, chunkCount, now, documentId, knowledgeBaseId)
        );
        requireUpdated(updated, "AI knowledge document index state changed, please retry");
    }

    private Object[] readArgs(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        List<Object> args = new ArrayList<>();
        args.add(knowledgeBaseId);
        args.add(documentId);
        appendManageableKnowledgeBaseArgs(currentUser, knowledgeBaseId, args);
        return args.toArray();
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

    private Object[] updateArgs(CurrentUser currentUser, Long knowledgeBaseId, Object... values) {
        List<Object> args = new ArrayList<>(List.of(values));
        appendManageableKnowledgeBaseArgs(currentUser, knowledgeBaseId, args);
        return args.toArray();
    }

    private void appendManageableKnowledgeBaseArgs(CurrentUser currentUser, Long knowledgeBaseId, List<Object> args) {
        args.add(knowledgeBaseId);
        if (hasAllPermission(currentUser)) {
            return;
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

    @Override
    public void softDeleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId, LocalDateTime now) {
        int updated = jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set is_deleted = 1, update_time = ?
                        where id = ?
                          and knowledge_base_id = ?
                          and is_deleted = 0
                        """ + manageableKnowledgeBaseExists(currentUser),
                updateArgs(currentUser, knowledgeBaseId, now, documentId, knowledgeBaseId)
        );
        requireUpdated(updated, "AI knowledge document delete state changed, please retry");
    }

    private void requireUpdated(int updated, String message) {
        if (updated <= 0) {
            throw new IllegalStateException(message);
        }
    }
}
