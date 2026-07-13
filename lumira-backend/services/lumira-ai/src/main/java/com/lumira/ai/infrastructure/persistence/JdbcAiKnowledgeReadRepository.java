package com.lumira.ai.infrastructure.persistence;

import com.lumira.ai.repository.AiKnowledgeReadRepository;
import com.lumira.ai.vo.AiKnowledgeBaseVO;
import com.lumira.ai.vo.AiKnowledgeDocumentVO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcAiKnowledgeReadRepository implements AiKnowledgeReadRepository {

    private static final String BASE_SELECT = """
            select kb.id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                   kb.owner_user_id, kb.owner_user_uuid, kb.created_by, kb.create_time, kb.update_time,
                   count(distinct d.id) as document_count, count(c.id) as chunk_count
            from ai_knowledge_base kb
            left join ai_knowledge_document d on d.knowledge_base_id = kb.id and d.is_deleted = 0
            left join ai_knowledge_chunk c on c.knowledge_base_id = kb.id and c.is_deleted = 0
            """;
    private static final String BASE_GROUP = """
            group by kb.id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                     kb.owner_user_id, kb.owner_user_uuid, kb.created_by, kb.create_time, kb.update_time
            """;
    private static final String DOCUMENT_SELECT = """
            select id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                   mime_type, file_size_bytes, status, parse_error, extracted_char_count, chunk_count,
                   created_by, create_time, update_time
            from ai_knowledge_document
            """;
    private final JdbcTemplate database;

    public JdbcAiKnowledgeReadRepository(JdbcTemplate database) { this.database = database; }

    @Override
    public List<AiKnowledgeBaseVO> findKnowledgeBases(AccessContext access, String keyword, String status, String scope, long limit, long offset) {
        StringBuilder where = new StringBuilder(" where kb.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        appendAccessible(where, args, access, scope);
        if (StringUtils.hasText(keyword)) {
            where.append(" and (kb.name like ? or kb.description like ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern); args.add(pattern);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and kb.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        args.add(limit); args.add(offset);
        return database.query(BASE_SELECT + where + BASE_GROUP + " order by kb.id desc limit ? offset ?", this::mapBase, args.toArray());
    }

    @Override
    public Optional<AiKnowledgeBaseVO> findAccessibleKnowledgeBase(Long id, AccessContext access) {
        StringBuilder where = new StringBuilder(" where kb.id = ? and kb.is_deleted = 0");
        List<Object> args = new ArrayList<>(List.of(id));
        appendAccessible(where, args, access, null);
        return database.query(BASE_SELECT + where + BASE_GROUP + " limit 1", this::mapBase, args.toArray()).stream().findFirst();
    }

    @Override
    public Optional<AiKnowledgeBaseVO> findManageableKnowledgeBase(Long id, AccessContext access) {
        StringBuilder where = new StringBuilder(" where kb.id = ? and kb.is_deleted = 0");
        List<Object> args = new ArrayList<>(List.of(id));
        if (!access.unrestricted()) {
            where.append(" and ((kb.owner_user_id = ? and kb.owner_user_uuid = ?) or ");
            args.add(access.userId()); args.add(access.userUuid());
            where.append(aclClause(args, access, List.of("MANAGE"))).append(")");
        }
        return database.query(BASE_SELECT + where + BASE_GROUP + " limit 1", this::mapBase, args.toArray()).stream().findFirst();
    }

    @Override
    public List<AiKnowledgeDocumentVO> findDocuments(Long knowledgeBaseId, long limit, long offset) {
        return database.query(DOCUMENT_SELECT + " where knowledge_base_id = ? and is_deleted = 0 order by id desc limit ? offset ?",
                this::mapDocument, knowledgeBaseId, limit, offset);
    }

    @Override
    public Optional<AiKnowledgeDocumentVO> findDocument(Long knowledgeBaseId, Long documentId) {
        return database.query(DOCUMENT_SELECT + " where knowledge_base_id = ? and id = ? and is_deleted = 0 limit 1",
                this::mapDocument, knowledgeBaseId, documentId).stream().findFirst();
    }

    private void appendAccessible(StringBuilder where, List<Object> args, AccessContext access, String scope) {
        String normalized = StringUtils.hasText(scope) ? scope.trim().toUpperCase(Locale.ROOT) : null;
        if (access.unrestricted()) {
            appendScope(where, args, access, normalized);
        } else if ("OWNED".equals(normalized)) {
            owner(where, args, access, false);
        } else if ("PLATFORM".equals(normalized)) {
            where.append(" and kb.visibility_scope = ?"); args.add("PLATFORM");
        } else if ("SHARED".equals(normalized)) {
            owner(where, args, access, true);
            where.append(" and ").append(aclClause(args, access, List.of("VIEW", "USE", "MANAGE")));
        } else {
            where.append(" and ((kb.owner_user_id = ? and kb.owner_user_uuid = ?) or kb.visibility_scope = ? or ");
            args.add(access.userId()); args.add(access.userUuid()); args.add("PLATFORM");
            where.append(aclClause(args, access, List.of("VIEW", "USE", "MANAGE"))).append(")");
        }
    }

    private void appendScope(StringBuilder where, List<Object> args, AccessContext access, String scope) {
        if ("OWNED".equals(scope)) owner(where, args, access, false);
        else if ("SHARED".equals(scope)) { owner(where, args, access, true); where.append(" and ").append(aclClause(args, access, List.of("VIEW", "USE", "MANAGE"))); }
        else if ("PLATFORM".equals(scope)) { where.append(" and kb.visibility_scope = ?"); args.add("PLATFORM"); }
    }

    private void owner(StringBuilder where, List<Object> args, AccessContext access, boolean negate) {
        where.append(negate ? " and not (kb.owner_user_id = ? and kb.owner_user_uuid = ?)" : " and kb.owner_user_id = ? and kb.owner_user_uuid = ?");
        args.add(access.userId()); args.add(access.userUuid());
    }

    private String aclClause(List<Object> args, AccessContext access, List<String> permissions) {
        StringBuilder sql = new StringBuilder("exists (select 1 from ai_knowledge_base_acl acl where acl.knowledge_base_id = kb.id and acl.is_deleted = 0 and acl.permission in (");
        sql.append(placeholders(permissions.size())).append(") and ((acl.subject_type = 'USER' and acl.subject_id = ?)");
        args.addAll(permissions); args.add(access.userId());
        appendSubjects(sql, args, "ROLE", access.roleIds());
        appendSubjects(sql, args, "DEPARTMENT", access.departmentIds());
        return sql.append("))").toString();
    }

    private void appendSubjects(StringBuilder sql, List<Object> args, String type, Set<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            sql.append(" or (acl.subject_type = '").append(type).append("' and acl.subject_id in (").append(placeholders(ids.size())).append("))");
            args.addAll(ids);
        }
    }

    private String placeholders(int size) { return String.join(",", java.util.Collections.nCopies(size, "?")); }

    private AiKnowledgeBaseVO mapBase(ResultSet rs, int rowNum) throws SQLException {
        return new AiKnowledgeBaseVO(rs.getLong("id"), rs.getString("kb_code"), rs.getString("name"), rs.getString("description"),
                rs.getString("status"), rs.getString("visibility_scope"), objectLong(rs, "owner_user_id"), rs.getString("owner_user_uuid"),
                objectLong(rs, "document_count"), objectLong(rs, "chunk_count"), objectLong(rs, "created_by"),
                localDateTime(rs, "create_time"), localDateTime(rs, "update_time"));
    }

    private AiKnowledgeDocumentVO mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new AiKnowledgeDocumentVO(rs.getLong("id"), rs.getLong("knowledge_base_id"), objectLong(rs, "file_id"),
                rs.getString("title"), rs.getString("original_file_name"), rs.getString("file_extension"), rs.getString("mime_type"),
                objectLong(rs, "file_size_bytes"), rs.getString("status"), rs.getString("parse_error"), objectInt(rs, "extracted_char_count"),
                objectInt(rs, "chunk_count"), objectLong(rs, "created_by"), localDateTime(rs, "create_time"), localDateTime(rs, "update_time"));
    }

    private Long objectLong(ResultSet rs, String column) throws SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }
    private Integer objectInt(ResultSet rs, String column) throws SQLException { int value = rs.getInt(column); return rs.wasNull() ? null : value; }
    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException { var value = rs.getTimestamp(column); return value == null ? null : value.toLocalDateTime(); }
}
