package com.lumira.saas.modules.ai.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.event.PlatformEventPublisher;
import com.lumira.saas.infrastructure.event.PlatformEventTypes;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.domain.model.AiAssistantDomainModels.KnowledgeBaseAggregate;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AiKnowledgeBaseAppService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeBaseAppService.class);

    private static final int CHUNK_SIZE = 1400;
    private static final int CHUNK_OVERLAP = 180;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String SCOPE_PERSONAL = "PERSONAL";
    private static final String SCOPE_TENANT = "TENANT";
    private static final String KNOWLEDGE_STORAGE_BUCKET = "ai_knowledge";
    private static final String FILE_TEXT_CONTENT_ARTIFACT = "TEXT_CONTENT";
    private static final int MAX_INDEX_BATCH_SIZE = 50;
    private static final int MAX_INDEX_RETRY_COUNT = 5;
    private static final int MAX_INDEX_RETRY_DELAY_SECONDS = 300;

    private final MyBatisQueryOperations jdbcTemplate;
    private final FileInternalApi fileInternalApi;
    private final AiKnowledgeTextExtractor textExtractor;
    private final OperationAuditService operationAuditService;
    private final PlatformEventPublisher platformEventPublisher;
    private final DomainEventPublisher domainEventPublisher;
    private final AiKnowledgeVectorService vectorService;

    public AiKnowledgeBaseAppService(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            OperationAuditService operationAuditService,
            PlatformEventPublisher platformEventPublisher,
            @Qualifier("systemDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            AiKnowledgeVectorService vectorService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileInternalApi = fileInternalApi;
        this.textExtractor = textExtractor;
        this.operationAuditService = operationAuditService;
        this.platformEventPublisher = platformEventPublisher;
        this.domainEventPublisher = domainEventPublisher;
        this.vectorService = vectorService;
    }

    private enum KnowledgeAccess {
        VIEW,
        USE,
        MANAGE
    }

    public PageResponse<AiVO.KnowledgeBaseVO> listKnowledgeBases(CurrentUser currentUser, String keyword, String status, String scope, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        StringBuilder where = new StringBuilder(" where kb.tenant_id = ? and kb.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, "kb", KnowledgeAccess.VIEW, scope);
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

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(safePageSize);
        queryArgs.add((safePageNo - 1L) * safePageSize);
        List<AiVO.KnowledgeBaseVO> records = jdbcTemplate.query(
                """
                        select kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time,
                               count(distinct d.id) as document_count,
                               count(c.id) as chunk_count
                        from ai_knowledge_base kb
                        left join ai_knowledge_document d
                          on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c
                          on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                        """ + sqlClause(where) + """
                        group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                                 kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time
                        order by kb.id desc
                        limit ? offset ?
                        """,
                this::mapKnowledgeBase,
                queryArgs.toArray()
        );
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject("select count(1) from ai_knowledge_base kb" + where, Long.class, args.toArray()));
        PageResponse<AiVO.KnowledgeBaseVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public AiVO.KnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id) {
        return requireKnowledgeBase(currentUser, id, KnowledgeAccess.VIEW);
    }

    @Transactional
    public AiVO.KnowledgeBaseVO createKnowledgeBase(CurrentUser currentUser, AiDTO.KnowledgeBaseUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long ownerUserId = currentUserId(currentUser);
        validateKnowledgeName(tenantId, ownerUserId, request.getName(), null);
        String visibilityScope = defaultVisibility(currentUser, request.getVisibilityScope());
        String code = "kb_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into ai_knowledge_base (
                            tenant_id, kb_code, name, description, status, visibility_scope, owner_user_id, created_by, updated_by,
                            is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                code,
                request.getName().trim(),
                cleanNullable(request.getDescription()),
                defaultStatus(request.getStatus()),
                visibilityScope,
                ownerUserId,
                ownerUserId,
                ownerUserId,
                now,
                now
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-create", "CREATE", "SUCCESS", "创建知识库: " + request.getName());
        return requireKnowledgeBase(currentUser, id, KnowledgeAccess.VIEW);
    }

    @Transactional
    public AiVO.KnowledgeBaseVO updateKnowledgeBase(CurrentUser currentUser, Long id, AiDTO.KnowledgeBaseUpsertRequest request) {
        Long tenantId = currentTenantId(currentUser);
        AiVO.KnowledgeBaseVO knowledgeBase = requireKnowledgeBase(currentUser, id, KnowledgeAccess.MANAGE);
        validateKnowledgeName(tenantId, knowledgeBase.getOwnerUserId(), request.getName(), id);
        String visibilityScope = defaultVisibility(currentUser, request.getVisibilityScope());
        jdbcTemplate.update(
                """
                        update ai_knowledge_base
                        set name = ?, description = ?, status = ?, visibility_scope = ?, updated_by = ?, update_time = ?
                        where tenant_id = ? and id = ? and is_deleted = 0
                        """,
                request.getName().trim(),
                cleanNullable(request.getDescription()),
                defaultStatus(request.getStatus()),
                visibilityScope,
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId,
                id
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-update", "UPDATE", "SUCCESS", "更新知识库: " + id);
        return requireKnowledgeBase(currentUser, id, KnowledgeAccess.VIEW);
    }

    @Transactional
    public boolean deleteKnowledgeBase(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(currentUser, id, KnowledgeAccess.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update ai_knowledge_base set is_deleted = 1, updated_by = ?, update_time = ? where tenant_id = ? and id = ? and is_deleted = 0", currentUser.getUserId(), now, tenantId, id);
        jdbcTemplate.update("update ai_knowledge_document set is_deleted = 1, updated_by = ?, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", currentUser.getUserId(), now, tenantId, id);
        jdbcTemplate.update("update ai_knowledge_chunk set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", now, tenantId, id);
        jdbcTemplate.update("update ai_employee_knowledge_base set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", now, tenantId, id);
        jdbcTemplate.update("update ai_knowledge_base_acl set is_deleted = 1, updated_by = ?, update_time = ? where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0", currentUser.getUserId(), now, tenantId, id);
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-delete", "DELETE", "SUCCESS", "删除知识库: " + id);
        return true;
    }

    public PageResponse<AiVO.KnowledgeDocumentVO> listDocuments(CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.VIEW);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
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
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(
                "select count(1) from ai_knowledge_document where tenant_id = ? and knowledge_base_id = ? and is_deleted = 0",
                Long.class,
                tenantId,
                knowledgeBaseId
        ));
        PageResponse<AiVO.KnowledgeDocumentVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    @Transactional
    public AiVO.KnowledgeDocumentVO uploadDocument(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.MANAGE);
        FileObjectDTO uploaded = uploadKnowledgeDocumentFile(file);
        LocalDateTime now = LocalDateTime.now();
        String title = cleanTitle(file.getOriginalFilename());
        jdbcTemplate.update(
                """
                        insert into ai_knowledge_document (
                            tenant_id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                            mime_type, file_size_bytes, status, extracted_text, extracted_char_count,
                            chunk_count, index_retry_count, index_next_retry_at, index_last_error,
                            created_by, updated_by, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 'INDEXING', null, 0, 0, 0, null, null, ?, ?, 0, ?, ?)
                        """,
                tenantId,
                knowledgeBaseId,
                uploaded.id(),
                title,
                uploaded.originalFileName(),
                normalizeExtension(uploaded.fileExtension(), null),
                uploaded.mimeType(),
                uploaded.fileSizeBytes(),
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
        KnowledgeBaseAggregate knowledgeBase = new KnowledgeBaseAggregate(knowledgeBaseId, tenantId);
        knowledgeBase.requestIndex(documentId, uploaded.id());
        domainEventPublisher.publishAll(knowledgeBase.pullDomainEvents());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-document-upload", "CREATE", "SUCCESS", "上传知识库文档并提交索引任务: " + title);
        return requireDocument(tenantId, knowledgeBaseId, documentId);
    }

    private FileObjectDTO uploadKnowledgeDocumentFile(MultipartFile file) {
        try {
            return fileInternalApi.uploadDocument(file, "AI 知识库", "knowledge-base", "知识库文档", KNOWLEDGE_STORAGE_BUCKET);
        } catch (BizException exception) {
            ErrorCode errorCode = exception.getErrorCode();
            if (errorCode == ErrorCode.BAD_REQUEST || errorCode == ErrorCode.VALIDATION_ERROR) {
                throw exception;
            }
            String message = "知识库文件上传失败：" + visibleUploadMessage(exception);
            throw new BizException(ErrorCode.BIZ_ERROR, message, message);
        } catch (RuntimeException exception) {
            log.warn("Knowledge document file upload failed", exception);
            String message = "知识库文件上传失败，请检查存储空间配置或稍后重试";
            throw new BizException(ErrorCode.BIZ_ERROR, message, message);
        }
    }

    @Transactional
    public AiVO.KnowledgeDocumentVO reindexDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.MANAGE);
        AiVO.KnowledgeDocumentVO document = requireDocument(tenantId, knowledgeBaseId, documentId);
        String extractedText = jdbcTemplate.queryForObject(
                "select extracted_text from ai_knowledge_document where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0",
                String.class,
                tenantId,
                knowledgeBaseId,
                documentId
        );
        if (!StringUtils.hasText(extractedText)) {
            FileContentDTO content = fileInternalApi.readFileContentForUser(
                    document.getFileId(),
                    tenantId,
                    currentUserId(currentUser),
                    currentUser == null ? "system" : currentUser.getUsername()
            );
            extractedText = textExtractor.extract(toMultipartFile(content)).text();
        }
        markDocumentIndexing(tenantId, knowledgeBaseId, documentId, currentUser.getUserId());
        KnowledgeBaseAggregate knowledgeBase = new KnowledgeBaseAggregate(knowledgeBaseId, tenantId);
        knowledgeBase.requestIndex(documentId, document.getFileId());
        domainEventPublisher.publishAll(knowledgeBase.pullDomainEvents());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-document-reindex", "UPDATE", "SUCCESS", "提交知识库文档重建索引任务: " + document.getTitle());
        return requireDocument(tenantId, knowledgeBaseId, documentId);
    }

    @Transactional
    public int processPendingIndexTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_INDEX_BATCH_SIZE));
        List<PendingIndexTask> tasks = jdbcTemplate.query(
                """
                        select id, tenant_id, knowledge_base_id, file_id, title, created_by,
                               coalesce(index_retry_count, 0) as index_retry_count
                        from ai_knowledge_document
                        where is_deleted = 0
                          and file_id is not null
                          and (
                                status = 'INDEXING'
                                or (status = 'FAILED'
                                    and coalesce(index_retry_count, 0) < ?
                                    and (index_next_retry_at is null or index_next_retry_at <= ?))
                          )
                        order by update_time asc, id asc
                        limit ?
                        """,
                (row, rowNum) -> new PendingIndexTask(
                        row.getLong("id"),
                        row.getLong("tenant_id"),
                        row.getLong("knowledge_base_id"),
                        row.getObject("file_id", Long.class),
                        row.getString("title"),
                        row.getObject("created_by", Long.class),
                        row.getObject("index_retry_count", Integer.class)
                ),
                MAX_INDEX_RETRY_COUNT,
                LocalDateTime.now(),
                safeLimit
        );
        int processed = 0;
        for (PendingIndexTask task : tasks) {
            processKnowledgeDocumentIndex(task);
            processed++;
        }
        return processed;
    }

    @Transactional
    public void processKnowledgeDocumentIndex(Long tenantId, Long knowledgeBaseId, Long documentId) {
        PendingIndexTask task = jdbcTemplate.queryForObject(
                """
                        select id, tenant_id, knowledge_base_id, file_id, title, created_by,
                               coalesce(index_retry_count, 0) as index_retry_count
                        from ai_knowledge_document
                        where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                          and status in ('INDEXING', 'FAILED')
                        limit 1
                        """,
                (row, rowNum) -> new PendingIndexTask(
                        row.getLong("id"),
                        row.getLong("tenant_id"),
                        row.getLong("knowledge_base_id"),
                        row.getObject("file_id", Long.class),
                        row.getString("title"),
                        row.getObject("created_by", Long.class),
                        row.getObject("index_retry_count", Integer.class)
                ),
                tenantId,
                knowledgeBaseId,
                documentId
        );
        if (task != null) {
            processKnowledgeDocumentIndex(task);
        }
    }

    @Transactional
    public boolean deleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long tenantId = currentTenantId(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.MANAGE);
        AiVO.KnowledgeDocumentVO document = requireDocument(tenantId, knowledgeBaseId, documentId);
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
        publishKnowledgeDocumentEvent(
                PlatformEventTypes.AI_KNOWLEDGE_DOCUMENT_DELETED,
                currentUser,
                tenantId,
                knowledgeBaseId,
                documentId,
                document.getTitle(),
                "DELETED",
                0
        );
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "ai", "knowledge-document-delete", "DELETE", "SUCCESS", "删除知识库文档: " + documentId);
        return true;
    }

    public List<AiVO.KnowledgeReferenceVO> retrieve(CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit) {
        return retrieve(currentUser, query, knowledgeBaseIds, limit, false);
    }

    private List<AiVO.KnowledgeReferenceVO> retrieve(CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit, boolean ownedOnly) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        Long tenantId = currentTenantId(currentUser);
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
        if (ownedOnly) {
            where.append(" and kb.owner_user_id = ?");
            args.add(currentUserId(currentUser));
        } else {
            appendAccessibleKnowledgeBaseFilter(where, args, currentUser, "kb", KnowledgeAccess.USE, null);
        }
        List<Long> safeKbIds = normalizeIds(knowledgeBaseIds);
        if (!safeKbIds.isEmpty()) {
            where.append(" and kb.id in (").append("?,".repeat(safeKbIds.size()));
            where.setLength(where.length() - 1);
            where.append(")");
            args.addAll(safeKbIds);
        }

        int candidateLimit = Math.min(120, Math.max(safeLimit * 8, safeLimit));
        args.add(candidateLimit);
        AiEmbeddingVector queryVector = vectorService.embedQuery(query);
        List<VectorSearchCandidate> candidates = jdbcTemplate.query(
                """
                        select c.id as chunk_id, c.knowledge_base_id, kb.name as knowledge_base_name,
                               c.document_id, d.title as document_title, d.file_id, d.original_file_name,
                               c.chunk_index, c.content, c.embedding_vector_json
                        from ai_knowledge_chunk c
                        join ai_knowledge_document d on d.tenant_id = c.tenant_id and d.id = c.document_id
                        join ai_knowledge_base kb on kb.tenant_id = c.tenant_id and kb.id = c.knowledge_base_id
                        """ + sqlClause(where) + """
                        order by c.update_time desc, c.id desc
                        limit ?
                        """,
                (row, rowNum) -> mapVectorSearchCandidate(row, query, queryVector),
                args.toArray()
        );
        return vectorService.top(candidates, safeLimit).stream()
                .map(VectorSearchCandidate::reference)
                .toList();
    }

    public List<AiVO.KnowledgeBaseVO> listEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId) {
        Long tenantId = currentTenantId(currentUser);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(employeeId);
        StringBuilder where = new StringBuilder(" where rel.tenant_id = ? and rel.employee_id = ? and rel.is_deleted = 0");
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, "kb", KnowledgeAccess.VIEW, null);
        return jdbcTemplate.query(
                """
                        select kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time,
                               count(distinct d.id) as document_count,
                               count(c.id) as chunk_count
                        from ai_employee_knowledge_base rel
                        join ai_knowledge_base kb on kb.tenant_id = rel.tenant_id and kb.id = rel.knowledge_base_id and kb.is_deleted = 0
                        left join ai_knowledge_document d on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                        """ + sqlClause(where) + """
                        group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                                 kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time
                        order by kb.id desc
                        """,
                this::mapKnowledgeBase,
                args.toArray()
        );
    }

    @Transactional
    public boolean updateEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeKnowledgeBasesUpdateRequest request) {
        Long tenantId = currentTenantId(currentUser);
        boolean employeeExists = jdbcTemplate.exists(
                "select 1 from ai_employee where tenant_id = ? and id = ? and is_deleted = 0 limit 1",
                tenantId,
                employeeId
        );
        if (!employeeExists) {
            throw new BizException(ErrorCode.NOT_FOUND, "数字员工不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update ai_employee_knowledge_base set is_deleted = 1, update_time = ? where tenant_id = ? and employee_id = ? and is_deleted = 0", now, tenantId, employeeId);
        for (Long kbId : normalizeIds(request.getKnowledgeBaseIds())) {
            requireKnowledgeBase(currentUser, kbId, KnowledgeAccess.USE);
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

    public List<AiVO.KnowledgeReferenceVO> retrieveForEmployee(CurrentUser currentUser, Long employeeId, String query, int limit) {
        Long tenantId = currentTenantId(currentUser);
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
            return List.of();
        }
        return retrieve(currentUser, query, boundIds, limit, false);
    }

    private int rebuildChunks(Long tenantId, Long knowledgeBaseId, Long documentId, String text) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("update ai_knowledge_chunk set is_deleted = 1, update_time = ? where tenant_id = ? and knowledge_base_id = ? and document_id = ? and is_deleted = 0", now, tenantId, knowledgeBaseId, documentId);
        List<String> chunks = splitChunks(text);
        int index = 0;
        for (String chunk : chunks) {
            AiKnowledgeVectorService.VectorProjection projection = vectorService.project(chunk);
            jdbcTemplate.update(
                    """
                            insert into ai_knowledge_chunk (
                                tenant_id, knowledge_base_id, document_id, chunk_index, content, search_text,
                                token_count, embedding_model, embedding_dim, embedding_vector_json, vector_indexed_at,
                                is_deleted, create_time, update_time
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                            """,
                    tenantId,
                    knowledgeBaseId,
                    documentId,
                    index,
                    chunk,
                    chunk.toLowerCase(Locale.ROOT),
                    Math.max(1, chunk.length() / 2),
                    projection.model(),
                    projection.dimensions(),
                    projection.vectorJson(),
                    now,
                    now,
                    now
            );
            index++;
        }
        jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'READY', parse_error = null, chunk_count = ?, index_retry_count = 0,
                            index_next_retry_at = null, index_last_error = null, update_time = ?
                        where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                        """,
                chunks.size(),
                now,
                tenantId,
                knowledgeBaseId,
                documentId
        );
        return chunks.size();
    }

    private void processKnowledgeDocumentIndex(PendingIndexTask task) {
        CurrentUser jobUser = new CurrentUser(
                task.createdBy() == null ? 0L : task.createdBy(),
                "ai-indexer",
                task.tenantId(),
                null,
                0,
                true,
                Set.of("*")
        );
        try {
            AiKnowledgeTextExtractor.ExtractedText extracted = resolveExtractedText(task, jobUser);
            jdbcTemplate.update(
                    """
                            update ai_knowledge_document
                            set extracted_text = ?, extracted_char_count = ?, file_extension = ?, parse_error = null,
                                updated_by = ?, update_time = ?
                            where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                            """,
                    extracted.text(),
                    extracted.text().length(),
                    normalizeExtension(extracted.extension(), "txt"),
                    jobUser.getUserId(),
                    LocalDateTime.now(),
                    task.tenantId(),
                    task.knowledgeBaseId(),
                    task.documentId()
            );
            int chunkCount = rebuildChunks(task.tenantId(), task.knowledgeBaseId(), task.documentId(), extracted.text());
            publishKnowledgeDocumentEvent(
                    PlatformEventTypes.AI_KNOWLEDGE_DOCUMENT_INDEXED,
                    jobUser,
                    task.tenantId(),
                    task.knowledgeBaseId(),
                    task.documentId(),
                    task.title(),
                    "READY",
                    chunkCount
            );
        } catch (RuntimeException exception) {
            markDocumentIndexFailed(task, exception);
        }
    }

    private AiKnowledgeTextExtractor.ExtractedText resolveExtractedText(PendingIndexTask task, CurrentUser jobUser) {
        FileProcessingArtifactDTO artifact = readTextArtifact(task, jobUser);
        if (artifact != null && StringUtils.hasText(artifact.contentText())) {
            return new AiKnowledgeTextExtractor.ExtractedText(
                    normalizeExtension(extensionFromFilename(task.title()), "txt"),
                    artifact.contentText()
            );
        }
        FileContentDTO content = fileInternalApi.readFileContentForUser(
                task.fileId(),
                task.tenantId(),
                jobUser.getUserId(),
                jobUser.getUsername()
        );
        return textExtractor.extract(toMultipartFile(content));
    }

    private FileProcessingArtifactDTO readTextArtifact(PendingIndexTask task, CurrentUser jobUser) {
        try {
            return fileInternalApi.readProcessingArtifactForUser(
                    task.fileId(),
                    task.tenantId(),
                    jobUser.getUserId(),
                    jobUser.getUsername(),
                    FILE_TEXT_CONTENT_ARTIFACT
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void markDocumentIndexing(Long tenantId, Long knowledgeBaseId, Long documentId, Long userId) {
        jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'INDEXING', parse_error = null, index_retry_count = 0,
                            index_next_retry_at = null, index_last_error = null, updated_by = ?, update_time = ?
                        where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                        """,
                userId == null ? 0L : userId,
                LocalDateTime.now(),
                tenantId,
                knowledgeBaseId,
                documentId
        );
    }

    private void markDocumentIndexFailed(PendingIndexTask task, RuntimeException exception) {
        int retryCount = task.retryCount() == null ? 0 : task.retryCount();
        int nextRetryCount = retryCount + 1;
        boolean deadLetter = nextRetryCount >= MAX_INDEX_RETRY_COUNT;
        LocalDateTime now = LocalDateTime.now();
        String error = truncateError(exception == null ? "unknown error" : exception.getMessage());
        jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = ?, parse_error = ?, index_retry_count = ?, index_next_retry_at = ?,
                            index_last_error = ?, updated_by = ?, update_time = ?
                        where tenant_id = ? and knowledge_base_id = ? and id = ? and is_deleted = 0
                        """,
                deadLetter ? "DEAD_LETTER" : "FAILED",
                error,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateIndexRetryDelaySeconds(nextRetryCount)),
                error,
                task.createdBy() == null ? 0L : task.createdBy(),
                now,
                task.tenantId(),
                task.knowledgeBaseId(),
                task.documentId()
        );
    }

    private long calculateIndexRetryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 1), MAX_INDEX_RETRY_COUNT);
        return Math.min(MAX_INDEX_RETRY_DELAY_SECONDS, (long) Math.pow(2, exponent));
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private MultipartFile toMultipartFile(FileContentDTO content) {
        if (content == null || content.content() == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库文件内容不存在");
        }
        return new InMemoryMultipartFile(
                "file",
                content.originalFileName(),
                content.mimeType(),
                content.content()
        );
    }

    private void publishKnowledgeDocumentEvent(
            String eventType,
            CurrentUser currentUser,
            Long tenantId,
            Long knowledgeBaseId,
            Long documentId,
            String title,
            String status,
            int chunkCount
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("knowledgeBaseId", knowledgeBaseId);
        attributes.put("documentId", documentId);
        attributes.put("title", title);
        attributes.put("status", status);
        attributes.put("chunkCount", chunkCount);
        platformEventPublisher.publishAfterCommit(
                PlatformEventTypes.SOURCE_AI,
                eventType,
                tenantId,
                currentUserId(currentUser),
                PlatformEventTypes.AGGREGATE_KNOWLEDGE_DOCUMENT,
                documentId,
                attributes
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

    private record PendingIndexTask(
            Long documentId,
            Long tenantId,
            Long knowledgeBaseId,
            Long fileId,
            String title,
            Long createdBy,
            Integer retryCount
    ) {
    }

    private record InMemoryMultipartFile(
            String name,
            String originalFilename,
            String contentType,
            byte[] bytes
    ) implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0L : bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), getBytes());
        }
    }

    private AiVO.KnowledgeBaseVO requireKnowledgeBase(CurrentUser currentUser, Long id, KnowledgeAccess access) {
        Long tenantId = currentTenantId(currentUser);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(id);
        StringBuilder where = new StringBuilder(" where kb.tenant_id = ? and kb.id = ? and kb.is_deleted = 0");
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, "kb", access, null);
        AiVO.KnowledgeBaseVO result = jdbcTemplate.queryForObject(
                """
                        select kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time,
                               count(distinct d.id) as document_count,
                               count(c.id) as chunk_count
                        from ai_knowledge_base kb
                        left join ai_knowledge_document d on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                        """ + sqlClause(where) + """
                        group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                                 kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time
                        limit 1
                        """,
                this::mapKnowledgeBase,
                args.toArray()
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
        vo.setOwnerUserId(row.getObject("owner_user_id", Long.class));
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

    private VectorSearchCandidate mapVectorSearchCandidate(SqlRow row, String query, AiEmbeddingVector queryVector) {
        AiVO.KnowledgeReferenceVO reference = mapKnowledgeReference(row, 0);
        double score = vectorService.score(
                queryVector,
                row.getString("embedding_vector_json"),
                query,
                row.getString("content"),
                row.getString("document_title"),
                row.getString("knowledge_base_name")
        );
        return new VectorSearchCandidate(reference, score);
    }

    private record VectorSearchCandidate(
            AiVO.KnowledgeReferenceVO reference,
            double score
    ) implements AiKnowledgeVectorService.ScoredCandidate {
    }

    private void validateKnowledgeName(Long tenantId, Long ownerUserId, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "知识库名称不能为空", "知识库名称不能为空");
        }
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from ai_knowledge_base
                        where tenant_id = ? and owner_user_id = ? and name = ? and is_deleted = 0 and (? is null or id <> ?)
                        limit 1
                        """,
                tenantId,
                ownerUserId,
                name.trim(),
                excludeId,
                excludeId
        );
        if (exists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "知识库名称已存在", "知识库名称已存在");
        }
    }

    private String visibleUploadMessage(BizException exception) {
        if (StringUtils.hasText(exception.getUserMessage())
                && !ErrorCode.SYSTEM_ERROR.getDefaultUserMessage().equals(exception.getUserMessage())
                && !ErrorCode.BIZ_ERROR.getDefaultUserMessage().equals(exception.getUserMessage())) {
            return exception.getUserMessage().trim();
        }
        if (exception.getErrorCode() == ErrorCode.SYSTEM_ERROR) {
            return "请检查存储空间配置或稍后重试";
        }
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage().trim();
        }
        return "请稍后重试";
    }

    private void appendAccessibleKnowledgeBaseFilter(
            StringBuilder where,
            List<Object> args,
            CurrentUser currentUser,
            String alias,
            KnowledgeAccess access,
            String scope
    ) {
        if (hasAllPermission(currentUser)) {
            appendScopeFilter(where, args, currentUser, alias, scope);
            return;
        }
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toUpperCase(Locale.ROOT) : null;
        if ("OWNED".equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".owner_user_id = ?");
            args.add(currentUserId(currentUser));
            return;
        }
        if (SCOPE_TENANT.equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".visibility_scope = ?");
            args.add(SCOPE_TENANT);
            return;
        }
        if ("SHARED".equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".owner_user_id <> ?");
            args.add(currentUserId(currentUser));
            where.append(" and ").append(buildAclExistsClause(alias, currentUser, access, args));
            return;
        }

        where.append(" and (").append(alias).append(".owner_user_id = ?");
        args.add(currentUserId(currentUser));
        if (access != KnowledgeAccess.MANAGE) {
            where.append(" or ").append(alias).append(".visibility_scope = ?");
            args.add(SCOPE_TENANT);
        }
        where.append(" or ").append(buildAclExistsClause(alias, currentUser, access, args)).append(")");
    }

    private void appendScopeFilter(StringBuilder where, List<Object> args, CurrentUser currentUser, String alias, String scope) {
        if (!StringUtils.hasText(scope)) {
            return;
        }
        String normalizedScope = scope.trim().toUpperCase(Locale.ROOT);
        if ("OWNED".equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".owner_user_id = ?");
            args.add(currentUserId(currentUser));
        } else if ("SHARED".equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".owner_user_id <> ?");
            args.add(currentUserId(currentUser));
            where.append(" and ").append(buildAclExistsClause(alias, currentUser, KnowledgeAccess.VIEW, args));
        } else if (SCOPE_TENANT.equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".visibility_scope = ?");
            args.add(SCOPE_TENANT);
        }
    }

    private String buildAclExistsClause(String alias, CurrentUser currentUser, KnowledgeAccess access, List<Object> args) {
        List<String> permissions = switch (access) {
            case MANAGE -> List.of("MANAGE");
            case USE -> List.of("USE", "MANAGE");
            case VIEW -> List.of("VIEW", "USE", "MANAGE");
        };
        StringBuilder clause = new StringBuilder();
        clause.append("exists (select 1 from ai_knowledge_base_acl acl where acl.tenant_id = ")
                .append(alias)
                .append(".tenant_id and acl.knowledge_base_id = ")
                .append(alias)
                .append(".id and acl.is_deleted = 0 and acl.permission in (")
                .append("?,".repeat(permissions.size()));
        clause.setLength(clause.length() - 1);
        clause.append(") and (");
        args.addAll(permissions);

        List<String> subjectClauses = new ArrayList<>();
        subjectClauses.add("(acl.subject_type = 'USER' and acl.subject_id = ?)");
        args.add(currentUserId(currentUser));
        Set<Long> roleIds = currentUser == null ? Set.of() : currentUser.getRoleIds();
        if (!roleIds.isEmpty()) {
            subjectClauses.add("(acl.subject_type = 'ROLE' and acl.subject_id in (" + "?,".repeat(roleIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(roleIds);
        }
        Set<Long> deptIds = new LinkedHashSet<>(currentUser == null ? Set.of() : currentUser.getDeptIds());
        if (currentUser != null && currentUser.getPrimaryDeptId() != null) {
            deptIds.add(currentUser.getPrimaryDeptId());
        }
        if (!deptIds.isEmpty()) {
            subjectClauses.add("(acl.subject_type = 'DEPARTMENT' and acl.subject_id in (" + "?,".repeat(deptIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(deptIds);
        }
        clause.append(String.join(" or ", subjectClauses)).append("))");
        return clause.toString();
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
        return com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private Long currentUserId(CurrentUser currentUser) {
        return currentUser == null || currentUser.getUserId() == null ? 0L : currentUser.getUserId();
    }

    private String sqlClause(StringBuilder clause) {
        return clause + "\n";
    }

    private boolean hasAllPermission(CurrentUser currentUser) {
        return currentUser != null && currentUser.getPermissions().contains("*");
    }

    private boolean canPublishTenantKnowledge(CurrentUser currentUser) {
        return currentUser != null && (currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains("ai:knowledge:share"));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "ENABLED";
    }

    private String defaultVisibility(CurrentUser currentUser, String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : SCOPE_PERSONAL;
        if ("PRIVATE".equals(normalized)) {
            normalized = SCOPE_PERSONAL;
        }
        if (!SCOPE_PERSONAL.equals(normalized) && !SCOPE_TENANT.equals(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "知识库可见范围不支持");
        }
        if (SCOPE_TENANT.equals(normalized) && !canPublishTenantKnowledge(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "没有发布企业知识库的权限");
        }
        return normalized;
    }

    private String normalizeExtension(String value, String fallback) {
        String extension = StringUtils.hasText(value) ? value : fallback;
        return extension == null ? null : extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
    }

    private String extensionFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1);
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
