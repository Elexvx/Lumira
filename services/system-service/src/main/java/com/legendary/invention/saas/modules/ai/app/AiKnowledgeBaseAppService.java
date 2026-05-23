package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.api.client.FileInternalApi;
import com.legendary.invention.api.file.FileObjectDTO;
import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.SqlRow;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AiKnowledgeBaseAppService {

    private static final int CHUNK_SIZE = 1400;
    private static final int CHUNK_OVERLAP = 180;
    private static final long MAX_PAGE_SIZE = 100L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final FileInternalApi fileInternalApi;
    private final AiKnowledgeTextExtractor textExtractor;
    private final OperationAuditService operationAuditService;

    public AiKnowledgeBaseAppService(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            OperationAuditService operationAuditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileInternalApi = fileInternalApi;
        this.textExtractor = textExtractor;
        this.operationAuditService = operationAuditService;
    }

    public PageResponse<AiVO.KnowledgeBaseVO> listKnowledgeBases(CurrentUser currentUser, String keyword, String status, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        StringBuilder where = new StringBuilder(" where kb.tenant_id = ? and kb.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (StringUtils.hasText(keyword)) {
            where.append(" and (kb.name like ? or kb.description like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and kb.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }

        Long total = jdbcTemplate.queryForObject("select count(1) from ai_knowledge_base kb" + where, Long.class, args.toArray());
        args.add(safePageSize);
        args.add((safePageNo - 1L) * safePageSize);
        List<AiVO.KnowledgeBaseVO> records = jdbcTemplate.query(
                """
                        select kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.created_by, kb.create_time, kb.update_time,
                               count(distinct d.id) as document_count,
                               count(c.id) as chunk_count
                        from ai_knowledge_base kb
                        left join ai_knowledge_document d
                          on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c
                          on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                        """ + where + """
                        group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                                 kb.created_by, kb.create_time, kb.update_time
                        order by kb.id desc
                        limit ? offset ?
                        """,
                this::mapKnowledgeBase,
                args.toArray()
        );
        PageResponse<AiVO.KnowledgeBaseVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public AiVO.KnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id) {
        return requireKnowledgeBase(currentTenantId(currentUser), id);
    }

    @Transactional
    public AiVO.KnowledgeBaseVO createKnowledgeBase(CurrentUser currentUser, AiDTO.KnowledgeBaseUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        validateKnowledgeName(tenantId, request.getName(), null);
        String code = "kb_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into ai_knowledge_base (
                            tenant_id, kb_code, name, description, status, visibility_scope, created_by, updated_by,
                            is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                code,
                request.getName().trim(),
                cleanNullable(request.getDescription()),
                defaultStatus(request.getStatus()),
                defaultVisibility(request.getVisibilityScope()),
                currentUser.getUserId(),
                currentUser.getUserId(),
                now,
                now
        );
        Long id = jdbcTemplate.queryForObject(
                "select id from ai_knowledge_base where tenant_id = ? and kb_code = ? and is_deleted = 0 limit 1",
                Long.class,
                tenantId,
                code
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-create", "CREATE", "SUCCESS", "创建知识库: " + request.getName());
        return requireKnowledgeBase(tenantId, id);
    }

    @Transactional
    public AiVO.KnowledgeBaseVO updateKnowledgeBase(CurrentUser currentUser, Long id, AiDTO.KnowledgeBaseUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(tenantId, id);
        validateKnowledgeName(tenantId, request.getName(), id);
        jdbcTemplate.update(
                """
                        update ai_knowledge_base
                        set name = ?, description = ?, status = ?, visibility_scope = ?, updated_by = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                request.getName().trim(),
                cleanNullable(request.getDescription()),
                defaultStatus(request.getStatus()),
                defaultVisibility(request.getVisibilityScope()),
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-update", "UPDATE", "SUCCESS", "更新知识库: " + id);
        return requireKnowledgeBase(tenantId, id);
    }

    @Transactional
    public boolean deleteKnowledgeBase(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(tenantId, id);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update ai_knowledge_base set is_deleted = 1, updated_by = ?, update_time = ? where tenant_id = ? and id = ? and is_deleted = 0", currentUser.getUserId(), now, tenantId, id);
        jdbcTemplate.update("update ai_knowledge_document set is_deleted = 1, updated_by = ?, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", currentUser.getUserId(), now, tenantId, id);
        jdbcTemplate.update("update ai_knowledge_chunk set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", now, tenantId, id);
        jdbcTemplate.update("update ai_employee_knowledge_base set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", now, tenantId, id);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-delete", "DELETE", "SUCCESS", "删除知识库: " + id);
        return true;
    }

    public PageResponse<AiVO.KnowledgeDocumentVO> listDocuments(CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(tenantId, knowledgeBaseId);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        Long total = jdbcTemplate.queryForObject(
                "select count(1) from ai_knowledge_document where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0",
                Long.class,
                tenantId,
                knowledgeBaseId
        );
        List<AiVO.KnowledgeDocumentVO> records = jdbcTemplate.query(
                """
                        select id, tenant_id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                               mime_type, file_size_bytes, status, parse_error, extracted_char_count, chunk_count,
                               created_by, create_time, update_time
                        from ai_knowledge_document
                        where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0
                        order by id desc
                        limit ? offset ?
                        """,
                this::mapKnowledgeDocument,
                tenantId,
                knowledgeBaseId,
                safePageSize,
                (safePageNo - 1L) * safePageSize
        );
        PageResponse<AiVO.KnowledgeDocumentVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    @Transactional
    public AiVO.KnowledgeDocumentVO uploadDocument(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(tenantId, knowledgeBaseId);
        AiKnowledgeTextExtractor.ExtractedText extracted = textExtractor.extract(file);
        FileObjectDTO uploaded = fileInternalApi.uploadDocument(file, "AI 知识库", "knowledge-base", "知识库文档");
        LocalDateTime now = LocalDateTime.now();
        String title = cleanTitle(file.getOriginalFilename());
        jdbcTemplate.update(
                """
                        insert into ai_knowledge_document (
                            tenant_id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                            mime_type, file_size_bytes, status, extracted_text, extracted_char_count,
                            chunk_count, created_by, updated_by, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 'INDEXING', ?, ?, 0, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                knowledgeBaseId,
                uploaded.id(),
                title,
                uploaded.originalFileName(),
                normalizeExtension(uploaded.fileExtension(), extracted.extension()),
                uploaded.mimeType(),
                uploaded.fileSizeBytes(),
                extracted.text(),
                extracted.text().length(),
                currentUser.getUserId(),
                currentUser.getUserId(),
                now,
                now
        );
        Long documentId = jdbcTemplate.queryForObject(
                "select id from ai_knowledge_document where tenant_id = ? and knowledge_base_id = ? and file_id = ? and is_deleted = 0 order by id desc limit 1",
                Long.class,
                tenantId,
                knowledgeBaseId,
                uploaded.id()
        );
        rebuildChunks(tenantId, knowledgeBaseId, documentId, extracted.text());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-document-upload", "CREATE", "SUCCESS", "上传知识库文档: " + title);
        return requireDocument(tenantId, knowledgeBaseId, documentId);
    }

    @Transactional
    public AiVO.KnowledgeDocumentVO reindexDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.KnowledgeDocumentVO document = requireDocument(tenantId, knowledgeBaseId, documentId);
        String extractedText = jdbcTemplate.queryForObject(
                "select extracted_text from ai_knowledge_document where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0",
                String.class,
                tenantId,
                knowledgeBaseId,
                documentId
        );
        if (!StringUtils.hasText(extractedText)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文档没有可重建索引的文本内容");
        }
        rebuildChunks(tenantId, knowledgeBaseId, documentId, extractedText);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-document-reindex", "UPDATE", "SUCCESS", "重建知识库文档索引: " + document.getTitle());
        return requireDocument(tenantId, knowledgeBaseId, documentId);
    }

    @Transactional
    public boolean deleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long tenantId = currentTenantId(currentUser);
        requireDocument(tenantId, knowledgeBaseId, documentId);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update ai_knowledge_document set is_deleted = 1, updated_by = ?, update_time = ? where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0",
                currentUser.getUserId(),
                now,
                tenantId,
                knowledgeBaseId,
                documentId
        );
        jdbcTemplate.update("update ai_knowledge_chunk set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and document_id = ? and is_deleted = 0", now, tenantId, knowledgeBaseId, documentId);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-document-delete", "DELETE", "SUCCESS", "删除知识库文档: " + documentId);
        return true;
    }

    public List<AiVO.KnowledgeReferenceVO> retrieve(CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit) {
        return retrieve(currentTenantId(currentUser), query, knowledgeBaseIds, limit);
    }

    public List<AiVO.KnowledgeReferenceVO> retrieve(Long tenantId, String query, List<Long> knowledgeBaseIds, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(12, limit));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        StringBuilder where = new StringBuilder("""
                where c.tenant_id = ?
                  and c.is_deleted = 0
                  and d.is_deleted = 0
                  and d.status = 'READY'
                  and kb.is_deleted = 0
                  and kb.status = 'ENABLED'
                """);
        List<Long> safeKbIds = normalizeIds(knowledgeBaseIds);
        if (!safeKbIds.isEmpty()) {
            where.append(" and kb.id in (").append("?,".repeat(safeKbIds.size()));
            where.setLength(where.length() - 1);
            where.append(")");
            args.addAll(safeKbIds);
        }

        String like = "%" + query.trim() + "%";
        where.append(" and (lower(c.search_text) like lower(?) or lower(d.title) like lower(?) or lower(kb.name) like lower(?))");
        args.add(like);
        args.add(like);
        args.add(like);
        args.add(safeLimit);
        return jdbcTemplate.query(
                """
                        select c.id as chunk_id, c.knowledge_base_id, kb.name as knowledge_base_name,
                               c.document_id, d.title as document_title, d.file_id, d.original_file_name,
                               c.chunk_index, c.content
                        from ai_knowledge_chunk c
                        join ai_knowledge_document d on d.tenant_id = c.tenant_id and d.id = c.document_id
                        join ai_knowledge_base kb on kb.tenant_id = c.tenant_id and kb.id = c.knowledge_base_id
                        """ + where + """
                        order by c.update_time desc, c.id desc
                        limit ?
                        """,
                this::mapKnowledgeReference,
                args.toArray()
        );
    }

    public List<AiVO.KnowledgeBaseVO> listEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                """
                        select kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.created_by, kb.create_time, kb.update_time,
                               count(distinct d.id) as document_count,
                               count(c.id) as chunk_count
                        from ai_employee_knowledge_base rel
                        join ai_knowledge_base kb on kb.tenant_id = rel.tenant_id and kb.id = rel.knowledge_base_id and kb.is_deleted = 0
                        left join ai_knowledge_document d on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                        where rel.tenant_id = ? and rel.employee_id = ? and rel.is_deleted = 0
                        group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                                 kb.created_by, kb.create_time, kb.update_time
                        order by kb.id desc
                        """,
                this::mapKnowledgeBase,
                tenantId,
                employeeId
        );
    }

    @Transactional
    public boolean updateEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeKnowledgeBasesUpdateRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Integer employeeExists = jdbcTemplate.queryForObject(
                "select count(1) from ai_employee where tenant_id = ? and id = ? and is_deleted = 0",
                Integer.class,
                tenantId,
                employeeId
        );
        if (employeeExists == null || employeeExists == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "数字员工不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update ai_employee_knowledge_base set is_deleted = 1, update_time = ? where tenant_id = ? and employee_id = ? and is_deleted = 0", now, tenantId, employeeId);
        for (Long kbId : normalizeIds(request.getKnowledgeBaseIds())) {
            requireKnowledgeBase(tenantId, kbId);
            jdbcTemplate.update(
                    """
                            insert into ai_employee_knowledge_base (
                                tenant_id, employee_id, knowledge_base_id, is_deleted, create_time, update_time
                            ) values (?, ?, ?, 0, ?, ?)
                            on duplicate key update is_deleted = 0, update_time = values(update_time)
                            """,
                    tenantId,
                    employeeId,
                    kbId,
                    now,
                    now
            );
        }
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-bind", "UPDATE", "SUCCESS", "更新数字员工知识库: " + employeeId);
        return true;
    }

    public List<AiVO.KnowledgeReferenceVO> retrieveForEmployee(Long tenantId, Long employeeId, String query, int limit) {
        List<Long> boundIds = jdbcTemplate.queryForList(
                """
                        select knowledge_base_id
                        from ai_employee_knowledge_base
                        where tenant_id = ? and employee_id = ? and is_deleted = 0
                        """,
                Long.class,
                tenantId,
                employeeId
        );
        if (boundIds == null || boundIds.isEmpty()) {
            return retrieve(tenantId, query, List.of(), limit);
        }
        return retrieve(tenantId, query, boundIds, limit);
    }

    private void rebuildChunks(Long tenantId, Long knowledgeBaseId, Long documentId, String text) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update ai_knowledge_chunk set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and document_id = ? and is_deleted = 0", now, tenantId, knowledgeBaseId, documentId);
        List<String> chunks = splitChunks(text);
        int index = 0;
        for (String chunk : chunks) {
            jdbcTemplate.update(
                    """
                            insert into ai_knowledge_chunk (
                                tenant_id, knowledge_base_id, document_id, chunk_index, content, search_text,
                                token_count, is_deleted, create_time, update_time
                            ) values (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                            """,
                    tenantId,
                    knowledgeBaseId,
                    documentId,
                    index,
                    chunk,
                    chunk.toLowerCase(Locale.ROOT),
                    Math.max(1, chunk.length() / 2),
                    now,
                    now
            );
            index++;
        }
        jdbcTemplate.update(
                "update ai_knowledge_document set status = 'READY', parse_error = null, chunk_count = ?, update_time = ? where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0",
                chunks.size(),
                now,
                tenantId,
                knowledgeBaseId,
                documentId
        );
    }

    private List<String> splitChunks(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            int softEnd = text.lastIndexOf('\n', end);
            if (softEnd > start + CHUNK_SIZE / 2) {
                end = softEnd;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return result;
    }

    private AiVO.KnowledgeBaseVO requireKnowledgeBase(Long tenantId, Long id) {
        AiVO.KnowledgeBaseVO result = jdbcTemplate.queryForObject(
                """
                        select kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.created_by, kb.create_time, kb.update_time,
                               count(distinct d.id) as document_count,
                               count(c.id) as chunk_count
                        from ai_knowledge_base kb
                        left join ai_knowledge_document d on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                        where kb.tenant_id = ? and kb.id = ? and kb.is_deleted = 0
                        group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                                 kb.created_by, kb.create_time, kb.update_time
                        limit 1
                        """,
                this::mapKnowledgeBase,
                tenantId,
                id
        );
        if (result == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return result;
    }

    private AiVO.KnowledgeDocumentVO requireDocument(Long tenantId, Long knowledgeBaseId, Long documentId) {
        AiVO.KnowledgeDocumentVO result = jdbcTemplate.queryForObject(
                """
                        select id, tenant_id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                               mime_type, file_size_bytes, status, parse_error, extracted_char_count, chunk_count,
                               created_by, create_time, update_time
                        from ai_knowledge_document
                        where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                        limit 1
                        """,
                this::mapKnowledgeDocument,
                tenantId,
                knowledgeBaseId,
                documentId
        );
        if (result == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库文档不存在");
        }
        return result;
    }

    private AiVO.KnowledgeBaseVO mapKnowledgeBase(SqlRow row, int rowNum) {
        AiVO.KnowledgeBaseVO vo = new AiVO.KnowledgeBaseVO();
        vo.setId(row.getLong("id"));
        vo.setTenantId(row.getLong("tenant_id"));
        vo.setKbCode(row.getString("kb_code"));
        vo.setName(row.getString("name"));
        vo.setDescription(row.getString("description"));
        vo.setStatus(row.getString("status"));
        vo.setVisibilityScope(row.getString("visibility_scope"));
        vo.setCreatedBy(row.getObject("created_by", Long.class));
        vo.setDocumentCount(row.getObject("document_count", Long.class));
        vo.setChunkCount(row.getObject("chunk_count", Long.class));
        vo.setCreateTime(toLocalDateTime(row.getTimestamp("create_time")));
        vo.setUpdateTime(toLocalDateTime(row.getTimestamp("update_time")));
        return vo;
    }

    private AiVO.KnowledgeDocumentVO mapKnowledgeDocument(SqlRow row, int rowNum) {
        AiVO.KnowledgeDocumentVO vo = new AiVO.KnowledgeDocumentVO();
        vo.setId(row.getLong("id"));
        vo.setTenantId(row.getLong("tenant_id"));
        vo.setKnowledgeBaseId(row.getLong("knowledge_base_id"));
        vo.setFileId(row.getObject("file_id", Long.class));
        vo.setTitle(row.getString("title"));
        vo.setOriginalFileName(row.getString("original_file_name"));
        vo.setFileExtension(row.getString("file_extension"));
        vo.setMimeType(row.getString("mime_type"));
        vo.setFileSizeBytes(row.getObject("file_size_bytes", Long.class));
        vo.setStatus(row.getString("status"));
        vo.setParseError(row.getString("parse_error"));
        vo.setExtractedCharCount(row.getObject("extracted_char_count", Integer.class));
        vo.setChunkCount(row.getObject("chunk_count", Integer.class));
        vo.setCreatedBy(row.getObject("created_by", Long.class));
        vo.setCreateTime(toLocalDateTime(row.getTimestamp("create_time")));
        vo.setUpdateTime(toLocalDateTime(row.getTimestamp("update_time")));
        return vo;
    }

    private AiVO.KnowledgeReferenceVO mapKnowledgeReference(SqlRow row, int rowNum) {
        AiVO.KnowledgeReferenceVO vo = new AiVO.KnowledgeReferenceVO();
        vo.setChunkId(row.getLong("chunk_id"));
        vo.setKnowledgeBaseId(row.getLong("knowledge_base_id"));
        vo.setKnowledgeBaseName(row.getString("knowledge_base_name"));
        vo.setDocumentId(row.getLong("document_id"));
        vo.setDocumentTitle(row.getString("document_title"));
        vo.setFileId(row.getObject("file_id", Long.class));
        vo.setOriginalFileName(row.getString("original_file_name"));
        vo.setChunkIndex(row.getInt("chunk_index"));
        vo.setContent(row.getString("content"));
        return vo;
    }

    private void validateKnowledgeName(Long tenantId, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "知识库名称不能为空");
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from ai_knowledge_base
                        where tenant_id = ? and name = ? and is_deleted = 0 and (? is null or id <> ?)
                        """,
                Integer.class,
                tenantId,
                name.trim(),
                excludeId,
                excludeId
        );
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "知识库名称已存在");
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        return com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ENABLED";
    }

    private String defaultVisibility(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "TENANT";
    }

    private String normalizeExtension(String value, String fallback) {
        String extension = StringUtils.hasText(value) ? value : fallback;
        return extension == null ? null : extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
    }

    private String cleanTitle(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "未命名文档";
        }
        String trimmed = filename.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot > 0 ? trimmed.substring(0, dot) : trimmed;
    }
}
