package com.lumira.saas.modules.ai.infrastructure;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.common.vo.PageResponse;
import com.lumira.api.event.TransactionalEventOutboxPort;
import com.lumira.saas.modules.ai.event.AiEventTypes;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.MyBatisQueryOperations;
import com.lumira.saas.modules.ai.infrastructure.persistence.support.SqlRow;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.integration.AiTrustedSessionResolver;
import com.lumira.saas.modules.ai.integration.AiPermissionSnapshotResolver;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.app.AiEmbeddingVector;
import com.lumira.saas.modules.ai.app.AiKnowledgeTextExtractor;
import com.lumira.saas.modules.ai.app.AiKnowledgeVectorService;
import com.lumira.saas.modules.ai.repository.AiKnowledgeBasePersistencePort;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.ai.integration.AiOperationAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
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

/**
 * Compatibility persistence adapter for the existing AI knowledge-base
 * aggregate.  The application facade delegates here while database access is
 * gradually split into smaller owner repositories.
 */
@Repository
public class JdbcAiKnowledgeBasePersistenceAdapter implements AiKnowledgeBasePersistencePort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAiKnowledgeBasePersistenceAdapter.class);

    private static final int CHUNK_SIZE = 1400;
    private static final int CHUNK_OVERLAP = 180;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final String SCOPE_PERSONAL = "PERSONAL";
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String KNOWLEDGE_STORAGE_BUCKET = "ai_knowledge";
    private static final String FILE_TEXT_CONTENT_ARTIFACT = "TEXT_CONTENT";
    private static final int MAX_INDEX_BATCH_SIZE = 50;
    private static final int MAX_INDEX_RETRY_COUNT = 5;
    private static final int MAX_INDEX_RETRY_DELAY_SECONDS = 300;
    private static final int INDEX_CLAIM_TTL_SECONDS = 900;
    private static final String PERMISSION_KNOWLEDGE_VIEW = "ai:knowledge:view";
    private static final String PERMISSION_KNOWLEDGE_QUERY = "ai:knowledge:query";
    private static final String PERMISSION_KNOWLEDGE_CREATE = "ai:knowledge:create";
    private static final String PERMISSION_KNOWLEDGE_UPDATE = "ai:knowledge:update";
    private static final String PERMISSION_KNOWLEDGE_DELETE = "ai:knowledge:delete";
    private static final String PERMISSION_KNOWLEDGE_BIND = "ai:knowledge:bind";
    private static final String PERMISSION_KNOWLEDGE_DOCUMENT_UPLOAD = "ai:knowledge:document:upload";
    private static final String PERMISSION_KNOWLEDGE_DOCUMENT_DELETE = "ai:knowledge:document:delete";
    private static final String PERMISSION_KNOWLEDGE_DOCUMENT_INDEX = "ai:knowledge:document:index";
    private static final String STATUS_ENABLED = "ENABLED";

    private final MyBatisQueryOperations jdbcTemplate;
    private final FileInternalApi fileInternalApi;
    private final AiKnowledgeTextExtractor textExtractor;
    private final AiOperationAuditLogger operationAuditService;
    private final TransactionalEventOutboxPort platformEventPublisher;
    private final AiKnowledgeVectorService vectorService;
    private final AiPermissionSnapshotResolver permissionSnapshotService;
    private final SystemInternalApi systemInternalApi;
    private final AiTrustedSessionResolver sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    public JdbcAiKnowledgeBasePersistenceAdapter(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            AiOperationAuditLogger operationAuditService,
            TransactionalEventOutboxPort platformEventPublisher,
            DomainEventPublisher ignoredDomainEventPublisher,
            AiKnowledgeVectorService vectorService,
            AiPermissionSnapshotResolver permissionSnapshotService
    ) {
        this(
                jdbcTemplate,
                fileInternalApi,
                textExtractor,
                operationAuditService,
                platformEventPublisher,
                vectorService,
                permissionSnapshotService,
                null,
                null,
                false
        );
    }

    /**
     * Legacy test/embedding constructor retained only as a source-compatible
     * bridge while the former domain publisher parameter is removed from the
     * runtime path.  Knowledge events are recorded through the transactional
     * outbox port above, never published after commit.
     */
    public JdbcAiKnowledgeBasePersistenceAdapter(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            AiOperationAuditLogger operationAuditService,
            TransactionalEventOutboxPort platformEventPublisher,
            DomainEventPublisher ignoredDomainEventPublisher,
            AiKnowledgeVectorService vectorService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                fileInternalApi,
                textExtractor,
                operationAuditService,
                platformEventPublisher,
                vectorService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                false
        );
    }

    @Autowired
    public JdbcAiKnowledgeBasePersistenceAdapter(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            AiOperationAuditLogger operationAuditService,
            TransactionalEventOutboxPort platformEventPublisher,
            AiKnowledgeVectorService vectorService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                fileInternalApi,
                textExtractor,
                operationAuditService,
                platformEventPublisher,
                vectorService,
                permissionSnapshotService,
                systemInternalApi,
                sessionAuthenticationService,
                true
        );
    }

    private JdbcAiKnowledgeBasePersistenceAdapter(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            AiOperationAuditLogger operationAuditService,
            TransactionalEventOutboxPort platformEventPublisher,
            AiKnowledgeVectorService vectorService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            SystemInternalApi systemInternalApi,
            AiTrustedSessionResolver sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileInternalApi = fileInternalApi;
        this.textExtractor = textExtractor;
        this.operationAuditService = operationAuditService;
        this.platformEventPublisher = platformEventPublisher;
        this.vectorService = vectorService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemInternalApi = systemInternalApi;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public JdbcAiKnowledgeBasePersistenceAdapter(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            AiOperationAuditLogger operationAuditService,
            TransactionalEventOutboxPort platformEventPublisher,
            DomainEventPublisher ignoredDomainEventPublisher,
            AiKnowledgeVectorService vectorService,
            AiPermissionSnapshotResolver permissionSnapshotService,
            AiTrustedSessionResolver sessionAuthenticationService
    ) {
        this(
                jdbcTemplate,
                fileInternalApi,
                textExtractor,
                operationAuditService,
                platformEventPublisher,
                vectorService,
                permissionSnapshotService,
                null,
                sessionAuthenticationService,
                false
        );
    }

    public JdbcAiKnowledgeBasePersistenceAdapter(
            MyBatisQueryOperations jdbcTemplate,
            FileInternalApi fileInternalApi,
            AiKnowledgeTextExtractor textExtractor,
            AiOperationAuditLogger operationAuditService,
            TransactionalEventOutboxPort platformEventPublisher,
            DomainEventPublisher domainEventPublisher,
            AiKnowledgeVectorService vectorService
    ) {
        this(jdbcTemplate,
                fileInternalApi,
                textExtractor,
                operationAuditService,
                platformEventPublisher,
                vectorService,
                null,
                null,
                null,
                false);
    }

    private enum KnowledgeAccess {
        VIEW,
        USE,
        MANAGE
    }

    public PageResponse<AiVO.KnowledgeBaseVO> listKnowledgeBases(CurrentUser currentUser, String keyword, String status, String scope, long pageNo, long pageSize) {
        requireKnowledgeViewPermission(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        StringBuilder where = new StringBuilder(" where kb.is_deleted = 0");
        List<Object> args = new ArrayList<>();
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
                        select kb.id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.owner_user_id, kb.owner_user_uuid, kb.created_by, kb.create_time, kb.update_time,
                               coalesce(kb.document_count, 0) as document_count,
                               coalesce(kb.chunk_count, 0) as chunk_count
                        from ai_knowledge_base kb
                        """ + sqlClause(where) + """
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
        requireKnowledgeViewPermission(currentUser);
        return requireKnowledgeBase(currentUser, id, KnowledgeAccess.VIEW);
    }

    @Transactional
    public AiVO.KnowledgeBaseVO createKnowledgeBase(CurrentUser currentUser, AiDTO.KnowledgeBaseUpsertRequest request) {
        Long ownerUserId = requireKnowledgeCreatePermission(currentUser);
        String ownerUserUuid = trustedUserUuid(currentUser);
        String ownerUsername = trustedUsername(currentUser);
        validateKnowledgeName(ownerUserId, ownerUserUuid, request.getName(), null);
        String visibilityScope = defaultVisibility(currentUser, request.getVisibilityScope());
        String code = "kb_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_knowledge_base (
                            kb_code, name, description, status, visibility_scope, owner_user_id, owner_user_uuid,
                            created_by, created_by_uuid, updated_by, updated_by_uuid,
                            is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                code,
                request.getName().trim(),
                cleanNullable(request.getDescription()),
                defaultStatus(request.getStatus()),
                visibilityScope,
                ownerUserId,
                ownerUserUuid,
                ownerUserId,
                ownerUserUuid,
                ownerUserId,
                ownerUserUuid,
                now,
                now
        );
        requireKnowledgeBaseWrite(inserted);
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        operationAuditService.log(ownerUserId, ownerUserUuid, ownerUsername, "ai", "knowledge-create", "CREATE", "SUCCESS", "创建知识库: " + request.getName());
        return requireKnowledgeBase(currentUser, id, KnowledgeAccess.VIEW);
    }

    @Transactional
    public AiVO.KnowledgeBaseVO updateKnowledgeBase(CurrentUser currentUser, Long id, AiDTO.KnowledgeBaseUpsertRequest request) {
        Long actorUserId = requireKnowledgeUpdatePermission(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        String actorUsername = trustedUsername(currentUser);
        AiVO.KnowledgeBaseVO knowledgeBase = requireKnowledgeBase(currentUser, id, KnowledgeAccess.MANAGE);
        validateKnowledgeName(knowledgeBase.getOwnerUserId(), knowledgeBase.getOwnerUserUuid(), request.getName(), id);
        String visibilityScope = defaultVisibility(currentUser, request.getVisibilityScope());
        int updated = jdbcTemplate.update(
                """
                        update ai_knowledge_base
                        set name = ?, description = ?, status = ?, visibility_scope = ?, updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and is_deleted = 0
                        """,
                request.getName().trim(),
                cleanNullable(request.getDescription()),
                defaultStatus(request.getStatus()),
                visibilityScope,
                actorUserId,
                actorUserUuid,
                LocalDateTime.now(),
                id,
                knowledgeBase.getOwnerUserId(),
                knowledgeBase.getOwnerUserUuid()
        );
        requireKnowledgeBaseWrite(updated);
        operationAuditService.log(actorUserId, actorUserUuid, actorUsername, "ai", "knowledge-update", "UPDATE", "SUCCESS", "更新知识库: " + id);
        return requireKnowledgeBase(currentUser, id, KnowledgeAccess.VIEW);
    }

    @Transactional
    public boolean deleteKnowledgeBase(CurrentUser currentUser, Long id) {
        Long actorUserId = requireKnowledgeDeletePermission(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        String actorUsername = trustedUsername(currentUser);
        AiVO.KnowledgeBaseVO knowledgeBase = requireKnowledgeBase(currentUser, id, KnowledgeAccess.MANAGE);
        LocalDateTime now = LocalDateTime.now();
        Long ownerUserId = knowledgeBase.getOwnerUserId();
        String ownerUserUuid = knowledgeBase.getOwnerUserUuid();
        int deleted = jdbcTemplate.update(
                """
                        update ai_knowledge_base
                        set is_deleted = 1, updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where id = ?
                          and owner_user_id = ?
                          and owner_user_uuid = ?
                          and is_deleted = 0
                        """,
                actorUserId,
                actorUserUuid,
                now,
                id,
                ownerUserId,
                ownerUserUuid
        );
        requireKnowledgeBaseWrite(deleted);
        jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set is_deleted = 1, updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where knowledge_base_id = ?
                          and is_deleted = 0
                          and exists (
                              select 1 from ai_knowledge_base kb
                              where kb.id = ai_knowledge_document.knowledge_base_id
                                and kb.owner_user_id = ?
                                and kb.owner_user_uuid = ?
                          )
                        """,
                actorUserId,
                actorUserUuid,
                now,
                id,
                ownerUserId,
                ownerUserUuid
        );
        jdbcTemplate.update(
                """
                        update ai_knowledge_chunk
                        set is_deleted = 1, update_time = ?
                        where knowledge_base_id = ?
                          and is_deleted = 0
                          and exists (
                              select 1 from ai_knowledge_base kb
                              where kb.id = ai_knowledge_chunk.knowledge_base_id
                                and kb.owner_user_id = ?
                                and kb.owner_user_uuid = ?
                          )
                        """,
                now,
                id,
                ownerUserId,
                ownerUserUuid
        );
        jdbcTemplate.update(
                """
                        update ai_employee_knowledge_base
                        set is_deleted = 1, update_time = ?
                        where knowledge_base_id = ?
                          and is_deleted = 0
                          and exists (
                              select 1 from ai_knowledge_base kb
                              where kb.id = ai_employee_knowledge_base.knowledge_base_id
                                and kb.owner_user_id = ?
                                and kb.owner_user_uuid = ?
                          )
                        """,
                now,
                id,
                ownerUserId,
                ownerUserUuid
        );
        jdbcTemplate.update(
                """
                        update ai_knowledge_base_acl
                        set is_deleted = 1, updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where knowledge_base_id = ?
                          and is_deleted = 0
                          and exists (
                              select 1 from ai_knowledge_base kb
                              where kb.id = ai_knowledge_base_acl.knowledge_base_id
                                and kb.owner_user_id = ?
                                and kb.owner_user_uuid = ?
                          )
                        """,
                actorUserId,
                actorUserUuid,
                now,
                id,
                ownerUserId,
                ownerUserUuid
        );
        operationAuditService.log(actorUserId, actorUserUuid, actorUsername, "ai", "knowledge-delete", "DELETE", "SUCCESS", "删除知识库: " + id);
        return true;
    }

    public PageResponse<AiVO.KnowledgeDocumentVO> listDocuments(CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize) {
        requireKnowledgeViewPermission(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.VIEW);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, pageSize));
        List<AiVO.KnowledgeDocumentVO> records = jdbcTemplate.query(
                """
                        select id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                               mime_type, file_size_bytes, status, parse_error, extracted_char_count, chunk_count,
                               created_by, create_time, update_time
                        from ai_knowledge_document
                        where knowledge_base_id = ? and is_deleted = 0
                        order by id desc
                        limit ? offset ?
                        """,
                this::mapKnowledgeDocument,
                knowledgeBaseId,
                safePageSize,
                (safePageNo - 1L) * safePageSize
        );
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(
                "select count(1) from ai_knowledge_document where knowledge_base_id = ? and is_deleted = 0",
                Long.class,
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
        Long actorUserId = requireKnowledgeDocumentUploadPermission(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        String actorUsername = trustedUsername(currentUser);
        Long actorSimulatedRoleId = trustedSimulatedRoleId(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.MANAGE);
        FileObjectDTO uploaded = uploadKnowledgeDocumentFile(actorUserId, actorUserUuid, actorUsername, actorSimulatedRoleId, file);
        LocalDateTime now = LocalDateTime.now();
        String title = cleanTitle(file.getOriginalFilename());
        int inserted = jdbcTemplate.update(
                """
                        insert into ai_knowledge_document (
                            knowledge_base_id, file_id, title, original_file_name, file_extension,
                            mime_type, file_size_bytes, status, extracted_text, extracted_char_count,
                            chunk_count, index_retry_count, index_next_retry_at, index_last_error,
                            created_by, created_by_uuid, simulated_role_id, updated_by, updated_by_uuid, is_deleted, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, 'INDEXING', null, 0, 0, 0, null, null, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                knowledgeBaseId,
                uploaded.id(),
                title,
                uploaded.originalFileName(),
                normalizeExtension(uploaded.fileExtension(), null),
                uploaded.mimeType(),
                uploaded.fileSizeBytes(),
                actorUserId,
                actorUserUuid,
                actorSimulatedRoleId,
                actorUserId,
                actorUserUuid,
                now,
                now
        );
        requireKnowledgeDocumentWrite(inserted, "Knowledge document changed, please retry");
        Long documentId = jdbcTemplate.queryForObject(
                "select id from ai_knowledge_document where knowledge_base_id = ? and file_id = ? and is_deleted = 0 order by id desc limit 1",
                Long.class,
                knowledgeBaseId,
                uploaded.id()
        );
        recordKnowledgeIndexRequested(knowledgeBaseId, documentId, uploaded.id(), actorUserId, actorUserUuid);
        processKnowledgeDocumentIndex(knowledgeBaseId, documentId);
        operationAuditService.log(actorUserId, actorUserUuid, actorUsername, "ai", "knowledge-document-upload", "CREATE", "SUCCESS", "上传知识库文档: " + title);
        return requireDocument(knowledgeBaseId, documentId);
    }

    private FileObjectDTO uploadKnowledgeDocumentFile(
            Long actorUserId,
            String actorUserUuid,
            String actorUsername,
            Long actorSimulatedRoleId,
            MultipartFile file
    ) {
        try {
            return fileInternalApi.uploadDocumentForUser(
                    file,
                    "AI knowledge base",
                    "knowledge-base",
                    "Knowledge document",
                    KNOWLEDGE_STORAGE_BUCKET,
                    actorUserId,
                    actorUserUuid,
                    actorUsername,
                    actorSimulatedRoleId
            );
        } catch (BizException exception) {
            ErrorCode errorCode = exception.getErrorCode();
            if (errorCode == ErrorCode.BAD_REQUEST || errorCode == ErrorCode.VALIDATION_ERROR) {
                throw exception;
            }
            String message = "Knowledge document file upload failed: " + visibleUploadMessage(exception);
            throw new BizException(ErrorCode.BIZ_ERROR, message, message);
        } catch (RuntimeException exception) {
            log.warn("Knowledge document file upload failed", exception);
            String message = "Knowledge document file upload failed. Please check storage configuration and try again later.";
            throw new BizException(ErrorCode.BIZ_ERROR, message, message);
        }
    }

    @Transactional
    public AiVO.KnowledgeDocumentVO reindexDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long actorUserId = requireKnowledgeDocumentIndexPermission(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        String actorUsername = trustedUsername(currentUser);
        Long actorSimulatedRoleId = trustedSimulatedRoleId(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.MANAGE);
        AiVO.KnowledgeDocumentVO document = requireDocument(knowledgeBaseId, documentId);
        String extractedText = jdbcTemplate.queryForObject(
                "select extracted_text from ai_knowledge_document where knowledge_base_id = ? and id = ? and is_deleted = 0",
                String.class,
                knowledgeBaseId,
                documentId
        );
        if (!StringUtils.hasText(extractedText)) {
            FileContentDTO content = fileInternalApi.readFileContentForUser(
                    document.getFileId(),
                    actorUserId,
                    actorUserUuid,
                    actorUsername,
                    false,
                    actorSimulatedRoleId
            );
            extractedText = textExtractor.extract(toMultipartFile(content)).text();
        }
        markDocumentIndexing(knowledgeBaseId, documentId, actorUserId, actorUserUuid);
        recordKnowledgeIndexRequested(knowledgeBaseId, documentId, document.getFileId(), actorUserId, actorUserUuid);
        processKnowledgeDocumentIndex(knowledgeBaseId, documentId);
        operationAuditService.log(actorUserId, actorUserUuid, actorUsername, "ai", "knowledge-document-reindex", "UPDATE", "SUCCESS", "重新索引知识库文档: " + document.getTitle());
        return requireDocument(knowledgeBaseId, documentId);
    }

    @Transactional
    public int processPendingIndexTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_INDEX_BATCH_SIZE));
        List<PendingIndexTask> candidates = jdbcTemplate.query(
                """
                        select d.id, d.knowledge_base_id, d.file_id, d.title, d.created_by,
                               d.created_by_uuid as created_by_user_uuid, d.simulated_role_id,
                               null as created_by_username,
                               coalesce(d.index_retry_count, 0) as index_retry_count
                        from ai_knowledge_document d
                        where d.is_deleted = 0
                          and d.file_id is not null
                          and (
                                d.status = 'INDEXING'
                                or (d.status = 'FAILED'
                                    and coalesce(d.index_retry_count, 0) < ?
                                    and (d.index_next_retry_at is null or d.index_next_retry_at <= ?))
                          )
                        order by d.update_time asc, d.id asc
                        limit ?
                        """,
                (row, rowNum) -> new PendingIndexTask(
                        row.getLong("id"),
                        row.getLong("knowledge_base_id"),
                        row.getObject("file_id", Long.class),
                        row.getString("title"),
                        row.getObject("created_by", Long.class),
                        row.getString("created_by_user_uuid"),
                        row.getObject("simulated_role_id", Long.class),
                        row.getString("created_by_username"),
                        row.getObject("index_retry_count", Integer.class),
                        null
                ),
                MAX_INDEX_RETRY_COUNT,
                LocalDateTime.now(),
                safeLimit
        );
        int processed = 0;
        for (PendingIndexTask candidate : candidates) {
            PendingIndexTask task;
            try {
                task = claimIndexTask(candidate);
            } catch (RuntimeException exception) {
                markDocumentIndexFailed(candidate, exception);
                processed++;
                continue;
            }
            if (task == null) {
                continue;
            }
            processKnowledgeDocumentIndex(task);
            processed++;
        }
        return processed;
    }

    @Transactional
    public void processKnowledgeDocumentIndex(Long knowledgeBaseId, Long documentId) {
        PendingIndexTask task = jdbcTemplate.queryForObject(
                """
                        select d.id, d.knowledge_base_id, d.file_id, d.title, d.created_by,
                               d.created_by_uuid as created_by_user_uuid, d.simulated_role_id,
                               null as created_by_username,
                               coalesce(d.index_retry_count, 0) as index_retry_count
                        from ai_knowledge_document d
                        where d.knowledge_base_id = ? and d.id = ? and d.is_deleted = 0
                          and d.status in ('INDEXING', 'FAILED')
                        limit 1
                        """,
                (row, rowNum) -> new PendingIndexTask(
                        row.getLong("id"),
                        row.getLong("knowledge_base_id"),
                        row.getObject("file_id", Long.class),
                        row.getString("title"),
                        row.getObject("created_by", Long.class),
                        row.getString("created_by_user_uuid"),
                        row.getObject("simulated_role_id", Long.class),
                        row.getString("created_by_username"),
                        row.getObject("index_retry_count", Integer.class),
                        null
                ),
                knowledgeBaseId,
                documentId
        );
        if (task != null) {
            PendingIndexTask claimed = claimIndexTask(task);
            if (claimed != null) {
                processKnowledgeDocumentIndex(claimed);
            }
        }
    }

    @Transactional
    public boolean deleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        Long actorUserId = requireKnowledgeDocumentDeletePermission(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        String actorUsername = trustedUsername(currentUser);
        requireKnowledgeBase(currentUser, knowledgeBaseId, KnowledgeAccess.MANAGE);
        AiVO.KnowledgeDocumentVO document = requireDocument(knowledgeBaseId, documentId);
        Long documentCreatedBy = requireIndexUserId(document.getCreatedBy());
        String documentCreatedByUuid = requireIndexUserUuid(document.getCreatedByUuid());
        LocalDateTime now = LocalDateTime.now();
        int deleted = jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set is_deleted = 1, updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where knowledge_base_id = ? and id = ? and created_by = ? and created_by_uuid = ? and is_deleted = 0
                        """,
                actorUserId,
                actorUserUuid,
                now,
                knowledgeBaseId,
                documentId,
                documentCreatedBy,
                documentCreatedByUuid
        );
        requireKnowledgeDocumentWrite(deleted, "Knowledge document changed, please retry");
        jdbcTemplate.update(
                """
                        update ai_knowledge_chunk
                        set is_deleted = 1, update_time = ?
                        where knowledge_base_id = ? and document_id = ?
                          and exists (
                              select 1
                              from ai_knowledge_document d
                              where d.knowledge_base_id = ai_knowledge_chunk.knowledge_base_id
                                and d.id = ai_knowledge_chunk.document_id
                                and d.created_by = ?
                                and d.created_by_uuid = ?
                          )
                          and is_deleted = 0
                        """,
                now,
                knowledgeBaseId,
                documentId,
                documentCreatedBy,
                documentCreatedByUuid
        );
        publishKnowledgeDocumentEvent(
                AiEventTypes.KNOWLEDGE_DOCUMENT_DELETED,
                currentUser,
                knowledgeBaseId,
                documentId,
                document.getTitle(),
                "DELETED",
                0
        );
        operationAuditService.log(actorUserId, actorUserUuid, actorUsername, "ai", "knowledge-document-delete", "DELETE", "SUCCESS", "删除知识库文档: " + documentId);
        return true;
    }

    public List<AiVO.KnowledgeReferenceVO> retrieve(CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit) {
        requireKnowledgeQueryPermission(currentUser);
        return retrieve(currentUser, query, knowledgeBaseIds, limit, false);
    }

    private List<AiVO.KnowledgeReferenceVO> retrieve(CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit, boolean ownedOnly) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        requireLogin(currentUser);
        int safeLimit = Math.max(1, Math.min(12, limit));
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                where c.is_deleted = 0
                  and d.is_deleted = 0
                  and d.status = 'READY'
                  and kb.is_deleted = 0
                  and kb.status = 'ENABLED'
                """);
        if (ownedOnly) {
            where.append(" and kb.owner_user_id = ? and kb.owner_user_uuid = ?");
            args.add(currentUserId(currentUser));
            args.add(currentUser.getUserUuid());
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

        int lexicalLimit = Math.max(300, safeLimit * 30);
        int recentLimit = Math.max(50, safeLimit * 5);
        AiEmbeddingVector queryVector = vectorService.embedQuery(query);
        List<VectorSearchCandidate> candidates = new ArrayList<>();
        List<Object> lexicalArgs = new ArrayList<>(args);
        String lexicalPredicate = buildLexicalPredicate(query, lexicalArgs);
        lexicalArgs.add(lexicalLimit);
        candidates.addAll(jdbcTemplate.query(
                """
                        select c.id as chunk_id, c.knowledge_base_id, kb.name as knowledge_base_name,
                               c.document_id, d.title as document_title, d.file_id, d.original_file_name,
                               c.chunk_index, c.content, c.embedding_vector_json,
                               c.embedding_vector_blob, c.embedding_norm
                        from ai_knowledge_chunk c
                        join ai_knowledge_document d on d.id = c.document_id
                        join ai_knowledge_base kb on kb.id = c.knowledge_base_id
                        """ + sqlClause(where) + lexicalPredicate + """
                        order by c.update_time desc, c.id desc
                        limit ?
                        """,
                (row, rowNum) -> mapVectorSearchCandidate(row, query, queryVector),
                lexicalArgs.toArray()
        ));
        List<Object> recentArgs = new ArrayList<>(args);
        recentArgs.add(recentLimit);
        candidates.addAll(jdbcTemplate.query(
                """
                        select c.id as chunk_id, c.knowledge_base_id, kb.name as knowledge_base_name,
                               c.document_id, d.title as document_title, d.file_id, d.original_file_name,
                               c.chunk_index, c.content, c.embedding_vector_json,
                               c.embedding_vector_blob, c.embedding_norm
                        from ai_knowledge_chunk c
                        join ai_knowledge_document d on d.id = c.document_id
                        join ai_knowledge_base kb on kb.id = c.knowledge_base_id
                        """ + sqlClause(where) + """
                        order by c.update_time desc, c.id desc
                        limit ?
                        """,
                (row, rowNum) -> mapVectorSearchCandidate(row, query, queryVector),
                recentArgs.toArray()
        ));
        candidates = dedupeCandidates(candidates);
        return vectorService.top(candidates, safeLimit).stream()
                .map(VectorSearchCandidate::reference)
                .toList();
    }

    public List<AiVO.KnowledgeBaseVO> listEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId) {
        requireKnowledgeViewPermission(currentUser);
        List<Object> args = new ArrayList<>();
        args.add(employeeId);
        StringBuilder where = new StringBuilder(" where rel.employee_id = ? and rel.is_deleted = 0");
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, "kb", KnowledgeAccess.VIEW, null);
        return jdbcTemplate.query(
                """
                        select kb.id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.owner_user_id, kb.owner_user_uuid, kb.created_by, kb.create_time, kb.update_time,
                               coalesce(kb.document_count, 0) as document_count,
                               coalesce(kb.chunk_count, 0) as chunk_count
                        from ai_employee_knowledge_base rel
                        join ai_knowledge_base kb on kb.id = rel.knowledge_base_id and kb.is_deleted = 0
                        """ + sqlClause(where) + """
                        order by kb.id desc
                        """,
                this::mapKnowledgeBase,
                args.toArray()
        );
    }

    @Transactional
    public boolean updateEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeKnowledgeBasesUpdateRequest request) {
        Long operatorId = requireKnowledgeBindPermission(currentUser);
        String operatorUsername = trustedUsername(currentUser);
        EmployeeBindingContext employee = requireEmployeeBindingContext(employeeId);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update ai_employee_knowledge_base
                        set is_deleted = 1, update_time = ?
                        where employee_id = ?
                          and is_deleted = 0
                          and exists (
                              select 1
                              from ai_employee e
                              where e.id = ai_employee_knowledge_base.employee_id
                                and e.username = ?
                                and e.enabled = ?
                                and e.is_deleted = 0
                          )
                        """,
                now,
                employee.id(),
                employee.username(),
                employee.enabled()
        );
        for (Long kbId : normalizeIds(request.getKnowledgeBaseIds())) {
            requireKnowledgeBase(currentUser, kbId, KnowledgeAccess.USE);
            jdbcTemplate.update(
                    """
                            insert into ai_employee_knowledge_base (
                                employee_id, knowledge_base_id, is_deleted, create_time, update_time
                            ) values (?, ?, 0, ?, ?)
                            on duplicate key update
                                is_deleted = case when employee_id = values(employee_id)
                                                       and knowledge_base_id = values(knowledge_base_id)
                                                       and exists (
                                                           select 1
                                                           from ai_employee e
                                                           where e.id = ai_employee_knowledge_base.employee_id
                                                             and e.username = ?
                                                             and e.enabled = ?
                                                             and e.is_deleted = 0
                                                       )
                                                  then 0 else is_deleted end,
                                update_time = case when employee_id = values(employee_id)
                                                        and knowledge_base_id = values(knowledge_base_id)
                                                        and exists (
                                                            select 1
                                                            from ai_employee e
                                                            where e.id = ai_employee_knowledge_base.employee_id
                                                              and e.username = ?
                                                              and e.enabled = ?
                                                              and e.is_deleted = 0
                                                        )
                                                   then values(update_time) else update_time end
                            """,
                    employee.id(),
                    kbId,
                    now,
                    now,
                    employee.username(),
                    employee.enabled(),
                    employee.username(),
                    employee.enabled()
            );
        }
        operationAuditService.log(operatorId, currentUser.getUserUuid(), operatorUsername, "ai", "knowledge-bind", "UPDATE", "SUCCESS", "更新数字员工知识库绑定: " + employee.id());
        return true;
    }

    private String buildLexicalPredicate(String query, List<Object> args) {
        List<String> tokens = tokenizeQuery(query);
        if (tokens.isEmpty()) {
            return "";
        }
        StringBuilder predicate = new StringBuilder(" and (");
        for (int i = 0; i < tokens.size(); i += 1) {
            if (i > 0) {
                predicate.append(" or ");
            }
            predicate.append("c.search_text like ?");
            args.add("%" + tokens.get(i) + "%");
        }
        predicate.append(")");
        return predicate.toString();
    }

    private List<String> tokenizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String[] parts = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                tokens.add(part);
            }
        }
        if (tokens.isEmpty() && StringUtils.hasText(query)) {
            tokens.add(query.trim().toLowerCase(Locale.ROOT));
        }
        return tokens.stream().limit(8).toList();
    }

    private List<VectorSearchCandidate> dedupeCandidates(List<VectorSearchCandidate> candidates) {
        Map<Long, VectorSearchCandidate> byChunkId = new LinkedHashMap<>();
        for (VectorSearchCandidate candidate : candidates) {
            if (candidate == null || candidate.reference() == null || candidate.reference().getChunkId() == null) {
                continue;
            }
            byChunkId.merge(candidate.reference().getChunkId(), candidate,
                    (left, right) -> left.score() >= right.score() ? left : right);
        }
        return new ArrayList<>(byChunkId.values());
    }

    public List<AiVO.KnowledgeReferenceVO> retrieveForEmployee(CurrentUser currentUser, Long employeeId, String query, int limit) {
        requireKnowledgeQueryPermission(currentUser);
        List<Long> boundIds = jdbcTemplate.queryForList(
                """
                        select knowledge_base_id
                        from ai_employee_knowledge_base
                        where employee_id = ? and is_deleted = 0
                        """,
                Long.class,
                employeeId
        );
        if (boundIds == null || boundIds.isEmpty()) {
            return List.of();
        }
        return retrieve(currentUser, query, boundIds, limit, false);
    }

    private int rebuildChunks(PendingIndexTask task, String text, Long actorUserId, String actorUserUuid) {
        LocalDateTime now = LocalDateTime.now();
        Long trustedActorUserId = requireIndexUserId(actorUserId);
        String trustedActorUserUuid = requireIndexUserUuid(actorUserUuid);
        String claimToken = requireIndexClaimToken(task);
        jdbcTemplate.update(
                """
                        update ai_knowledge_chunk
                        set is_deleted = 1, update_time = ?
                        where knowledge_base_id = ? and document_id = ? and is_deleted = 0
                          and exists (
                              select 1
                              from ai_knowledge_document d
                              where d.knowledge_base_id = ai_knowledge_chunk.knowledge_base_id
                                and d.id = ai_knowledge_chunk.document_id
                                and d.created_by = ?
                                and d.created_by_uuid = ?
                                and d.is_deleted = 0
                          )
                """,
                now,
                task.knowledgeBaseId(),
                task.documentId(),
                trustedActorUserId,
                trustedActorUserUuid
        );
        List<String> chunks = splitChunks(text);
        List<AiKnowledgeVectorService.VectorProjection> projections = vectorService.projectBatch(chunks);
        batchInsertChunks(task.knowledgeBaseId(), task.documentId(), chunks, projections, now);
        int readyUpdated = jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'READY', parse_error = null, chunk_count = ?, index_retry_count = 0,
                            index_next_retry_at = null, index_last_error = null,
                            index_claim_token = null, index_claim_expires_at = null,
                            updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where knowledge_base_id = ? and id = ? and file_id = ?
                          and status = 'INDEXING' and index_claim_token = ?
                          and created_by = ? and created_by_uuid = ? and is_deleted = 0
                        """,
                chunks.size(),
                trustedActorUserId,
                trustedActorUserUuid,
                now,
                task.knowledgeBaseId(),
                task.documentId(),
                task.fileId(),
                claimToken,
                trustedActorUserId,
                trustedActorUserUuid
        );
        if (readyUpdated != 1) {
            throw new IllegalStateException("Knowledge document index task changed, please retry");
        }
        refreshKnowledgeBaseStats(task.knowledgeBaseId());
        return chunks.size();
    }

    private PendingIndexTask claimIndexTask(PendingIndexTask task) {
        if (task == null) {
            return null;
        }
        IndexOwnerContext owner = requireTrustedIndexOwner(task);
        Long indexUserId = owner.userId();
        String indexUserUuid = owner.userUuid();
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbcTemplate.update(
                """
                        update ai_knowledge_document d
                        set d.status = 'INDEXING',
                            d.index_claim_token = ?,
                            d.index_claim_expires_at = ?,
                            d.parse_error = null,
                            d.updated_by = ?,
                            d.updated_by_uuid = ?,
                            d.update_time = ?
                        where d.knowledge_base_id = ? and d.id = ? and d.file_id = ?
                          and d.created_by = ? and d.created_by_uuid = ? and d.is_deleted = 0
                          and (
                                d.status = 'INDEXING'
                                or (d.status = 'FAILED'
                                    and coalesce(d.index_retry_count, 0) = ?
                                    and coalesce(d.index_retry_count, 0) < ?
                                    and (d.index_next_retry_at is null or d.index_next_retry_at <= ?))
                          )
                          and (d.index_claim_token is null or d.index_claim_expires_at is null or d.index_claim_expires_at <= ?)
                          and exists (
                              select 1
                              from ai_knowledge_base kb
                              where kb.id = d.knowledge_base_id
                                and kb.owner_user_id = d.created_by
                                and kb.owner_user_uuid = d.created_by_uuid
                                and kb.is_deleted = 0
                          )
                        """,
                claimToken,
                now.plusSeconds(INDEX_CLAIM_TTL_SECONDS),
                indexUserId,
                indexUserUuid,
                now,
                task.knowledgeBaseId(),
                task.documentId(),
                task.fileId(),
                indexUserId,
                indexUserUuid,
                task.retryCount() == null ? 0 : task.retryCount(),
                MAX_INDEX_RETRY_COUNT,
                now,
                now
        );
        if (updated != 1) {
            return null;
        }
        return task.withClaimToken(claimToken);
    }

    private void refreshKnowledgeBaseStats(Long knowledgeBaseId) {
        jdbcTemplate.update(
                """
                        insert into ai_knowledge_base_stats (
                            knowledge_base_id, document_count, chunk_count, vector_indexed_chunk_count, update_time
                        )
                        select kb.id,
                               count(distinct d.id),
                               count(c.id),
                               sum(case when c.embedding_vector_blob is not null or c.embedding_vector_json is not null then 1 else 0 end),
                               current_timestamp
                        from ai_knowledge_base kb
                        left join ai_knowledge_document d
                          on d.knowledge_base_id = kb.id and d.is_deleted = 0
                        left join ai_knowledge_chunk c
                          on c.knowledge_base_id = kb.id and c.is_deleted = 0
                        where kb.id = ? and kb.is_deleted = 0
                        group by kb.id
                        on duplicate key update
                            document_count = values(document_count),
                            chunk_count = values(chunk_count),
                            vector_indexed_chunk_count = values(vector_indexed_chunk_count),
                            update_time = values(update_time)
                        """,
                knowledgeBaseId
        );
        jdbcTemplate.update(
                """
                        update ai_knowledge_base kb
                        left join (
                            select d.knowledge_base_id,
                                   count(distinct d.id) as document_count,
                                   count(c.id) as chunk_count
                            from ai_knowledge_document d
                            left join ai_knowledge_chunk c
                              on c.knowledge_base_id = d.knowledge_base_id and c.document_id = d.id and c.is_deleted = 0
                            where d.knowledge_base_id = ? and d.is_deleted = 0
                            group by d.knowledge_base_id
                        ) stats on stats.knowledge_base_id = kb.id
                        set kb.document_count = coalesce(stats.document_count, 0),
                            kb.chunk_count = coalesce(stats.chunk_count, 0),
                            kb.update_time = current_timestamp
                        where kb.id = ? and kb.is_deleted = 0
                        """,
                knowledgeBaseId,
                knowledgeBaseId
        );
    }

    private void batchInsertChunks(
            Long knowledgeBaseId,
            Long documentId,
            List<String> chunks,
            List<AiKnowledgeVectorService.VectorProjection> projections,
            LocalDateTime now
    ) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        int batchSize = 100;
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(chunks.size(), start + batchSize);
            StringBuilder sql = new StringBuilder("""
                    insert into ai_knowledge_chunk (
                        knowledge_base_id, document_id, chunk_index, content, search_text,
                        token_count, embedding_model, embedding_dim, embedding_vector_json, embedding_vector_blob, embedding_norm, vector_indexed_at,
                        is_deleted, create_time, update_time
                    ) values
                    """);
            List<Object> args = new ArrayList<>((end - start) * 15);
            for (int index = start; index < end; index += 1) {
                if (index > start) {
                    sql.append(", ");
                }
                sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)");
                String chunk = chunks.get(index);
                AiKnowledgeVectorService.VectorProjection projection = index < projections.size()
                        ? projections.get(index)
                        : vectorService.project(chunk);
                args.add(knowledgeBaseId);
                args.add(documentId);
                args.add(index);
                args.add(chunk);
                args.add(chunk.toLowerCase(Locale.ROOT));
                args.add(Math.max(1, chunk.length() / 2));
                args.add(projection.model());
                args.add(projection.dimensions());
                args.add(projection.vectorJson());
                args.add(projection.vectorBlob());
                args.add(projection.vectorNorm());
                args.add(now);
                args.add(now);
                args.add(now);
            }
            int inserted = jdbcTemplate.update(sql.toString(), args.toArray());
            if (inserted != end - start) {
                throw new IllegalStateException("Knowledge document chunks changed, please retry");
            }
        }
    }

    private void processKnowledgeDocumentIndex(PendingIndexTask task) {
        try {
            String claimToken = requireIndexClaimToken(task);
            IndexOwnerContext owner = requireTrustedIndexOwner(task);
            AiKnowledgeTextExtractor.ExtractedText extracted = resolveExtractedText(task, owner);
            int extractedUpdated = jdbcTemplate.update(
                    """
                            update ai_knowledge_document
                            set extracted_text = ?, extracted_char_count = ?, file_extension = ?, parse_error = null,
                                updated_by = ?, updated_by_uuid = ?, update_time = ?
                            where knowledge_base_id = ? and id = ? and file_id = ?
                              and status = 'INDEXING' and index_claim_token = ?
                              and created_by = ? and created_by_uuid = ? and is_deleted = 0
                            """,
                    extracted.text(),
                    extracted.text().length(),
                    normalizeExtension(extracted.extension(), "txt"),
                    owner.userId(),
                    owner.userUuid(),
                    LocalDateTime.now(),
                    task.knowledgeBaseId(),
                    task.documentId(),
                    task.fileId(),
                    claimToken,
                    owner.userId(),
                    owner.userUuid()
            );
            if (extractedUpdated != 1) {
                throw new IllegalStateException("Knowledge document index task changed, please retry");
            }
            IndexOwnerContext refreshedOwner = requireTrustedIndexOwner(task);
            int chunkCount = rebuildChunks(task, extracted.text(), refreshedOwner.userId(), refreshedOwner.userUuid());
            publishKnowledgeDocumentEvent(
                    AiEventTypes.KNOWLEDGE_DOCUMENT_INDEXED,
                    refreshedOwner.userId(),
                    refreshedOwner.userUuid(),
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

    private AiKnowledgeTextExtractor.ExtractedText resolveExtractedText(PendingIndexTask task, IndexOwnerContext owner) {
        FileProcessingArtifactDTO artifact = readTextArtifact(task, owner);
        if (artifact != null && StringUtils.hasText(artifact.contentText())) {
            return new AiKnowledgeTextExtractor.ExtractedText(
                    normalizeExtension(extensionFromFilename(task.title()), "txt"),
                    artifact.contentText()
            );
        }
        FileContentDTO content = fileInternalApi.readFileContentForUser(
                task.fileId(),
                owner.userId(),
                owner.userUuid(),
                owner.username(),
                false,
                owner.simulatedRoleId()
        );
        return textExtractor.extract(toMultipartFile(content));
    }

    private FileProcessingArtifactDTO readTextArtifact(PendingIndexTask task, IndexOwnerContext owner) {
        try {
            return fileInternalApi.readProcessingArtifactForUser(
                    task.fileId(),
                    owner.userId(),
                    owner.userUuid(),
                    owner.username(),
                    FILE_TEXT_CONTENT_ARTIFACT,
                    false,
                    owner.simulatedRoleId()
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void markDocumentIndexing(Long knowledgeBaseId, Long documentId, Long userId, String userUuid) {
        int updated = jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = 'INDEXING', parse_error = null, index_retry_count = 0,
                            index_next_retry_at = null, index_last_error = null,
                            index_claim_token = null, index_claim_expires_at = null,
                            updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where knowledge_base_id = ? and id = ? and created_by = ? and created_by_uuid = ? and is_deleted = 0
                        """,
                requireIndexUserId(userId),
                requireIndexUserUuid(userUuid),
                LocalDateTime.now(),
                knowledgeBaseId,
                documentId,
                requireIndexUserId(userId),
                requireIndexUserUuid(userUuid)
        );
        requireKnowledgeDocumentWrite(updated, "Knowledge document changed, please retry");
    }

    private void markDocumentIndexFailed(PendingIndexTask task, RuntimeException exception) {
        int retryCount = task.retryCount() == null ? 0 : task.retryCount();
        int nextRetryCount = retryCount + 1;
        boolean deadLetter = nextRetryCount >= MAX_INDEX_RETRY_COUNT;
        LocalDateTime now = LocalDateTime.now();
        String error = truncateError(exception == null ? "unknown error" : exception.getMessage());
        String claimToken = indexClaimTokenOrNull(task);
        int failedUpdated = jdbcTemplate.update(
                """
                        update ai_knowledge_document
                        set status = ?, parse_error = ?, index_retry_count = ?, index_next_retry_at = ?,
                            index_last_error = ?,
                            index_claim_token = null, index_claim_expires_at = null,
                            updated_by = ?, updated_by_uuid = ?, update_time = ?
                        where knowledge_base_id = ? and id = ? and file_id = ?
                          and (? is null or status = 'INDEXING')
                          and (? is null or index_claim_token = ?)
                          and created_by = ? and created_by_uuid = ? and is_deleted = 0
                        """,
                deadLetter ? "DEAD_LETTER" : "FAILED",
                error,
                nextRetryCount,
                deadLetter ? null : now.plusSeconds(calculateIndexRetryDelaySeconds(nextRetryCount)),
                error,
                indexUserIdOrNull(task),
                indexUserUuidOrNull(task),
                now,
                task.knowledgeBaseId(),
                task.documentId(),
                task.fileId(),
                claimToken,
                claimToken,
                claimToken,
                indexUserIdOrNull(task),
                indexUserUuidOrNull(task)
        );
        if (failedUpdated <= 0) {
            log.warn("Knowledge document index failure mark changed before write knowledgeBaseId={} documentId={}",
                    task.knowledgeBaseId(), task.documentId());
        }
    }

    private Long requireIndexUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("Knowledge document index owner is required");
        }
        return userId;
    }

    private String requireIndexUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("Knowledge document index owner username is required");
        }
        return username.trim();
    }

    private String requireIndexUserUuid(String userUuid) {
        if (!StringUtils.hasText(userUuid)) {
            throw new IllegalStateException("Knowledge document index owner uuid is required");
        }
        return userUuid.trim();
    }

    private IndexOwnerContext requireTrustedIndexOwner(PendingIndexTask task) {
        Long userId = requireIndexUserId(task == null ? null : task.createdBy());
        String userUuid = requireIndexUserUuid(task == null ? null : task.createdByUserUuid());
        String username = task == null || !StringUtils.hasText(task.createdByUsername())
                ? null
                : task.createdByUsername().trim();
        Long simulatedRoleId = normalizeSimulatedRoleId(task == null ? null : task.simulatedRoleId());
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String liveUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(liveUserUuid)
                    || !userUuid.equals(liveUserUuid)
                    || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Knowledge document index owner is disabled or no longer active");
            }
            username = requireIndexUsername(userSnapshot.username());
            userUuid = liveUserUuid;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Knowledge document index trusted owner resolver is unavailable");
            }
            return new IndexOwnerContext(userId, userUuid, requireIndexUsername(username), simulatedRoleId);
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, userUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Knowledge document index owner is disabled or no longer active");
        }
        AiPermissionSnapshotResolver.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(userId, userUuid, simulatedRoleId)
                : permissionSnapshotService.loadSnapshot(userId, userUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Knowledge document index owner permission snapshot is unavailable");
            }
            return new IndexOwnerContext(userId, userUuid, requireIndexUsername(username), simulatedRoleId);
        }
        Set<String> permissions = snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions());
        if (!permissions.contains("*") && !permissions.contains(PERMISSION_KNOWLEDGE_DOCUMENT_INDEX)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + PERMISSION_KNOWLEDGE_DOCUMENT_INDEX);
        }
        return new IndexOwnerContext(userId, userUuid, requireIndexUsername(username), simulatedRoleId);
    }

    private Long indexUserIdOrNull(PendingIndexTask task) {
        if (task == null || task.createdBy() == null || task.createdBy() <= 0) {
            return null;
        }
        return task.createdBy();
    }

    private String indexUserUuidOrNull(PendingIndexTask task) {
        if (task == null || !StringUtils.hasText(task.createdByUserUuid())) {
            return null;
        }
        return task.createdByUserUuid().trim();
    }

    private String requireIndexClaimToken(PendingIndexTask task) {
        String claimToken = indexClaimTokenOrNull(task);
        if (!StringUtils.hasText(claimToken)) {
            throw new IllegalStateException("Knowledge document index claim token is required");
        }
        return claimToken;
    }

    private String indexClaimTokenOrNull(PendingIndexTask task) {
        if (task == null || !StringUtils.hasText(task.claimToken())) {
            return null;
        }
        return task.claimToken().trim();
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
            throw new BizException(ErrorCode.NOT_FOUND, "Knowledge document content not found");
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
            Long knowledgeBaseId,
            Long documentId,
            String title,
            String status,
            int chunkCount
    ) {
        publishKnowledgeDocumentEvent(
                eventType,
                currentUserId(currentUser),
                trustedUserUuid(currentUser),
                knowledgeBaseId,
                documentId,
                title,
                status,
                chunkCount
        );
    }

    /** Records the aggregate event in the same transaction as the document state transition. */
    private void recordKnowledgeIndexRequested(
            Long knowledgeBaseId,
            Long documentId,
            Long fileObjectId,
            Long userId,
            String userUuid
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("documentId", documentId);
        attributes.put("fileObjectId", fileObjectId);
        attributes.put("userUuid", userUuid);
        platformEventPublisher.record(
                "AI_KNOWLEDGE_INDEX_REQUESTED",
                userId,
                "ai.knowledge-base",
                knowledgeBaseId,
                attributes
        );
    }

    private void publishKnowledgeDocumentEvent(
            String eventType,
            Long userId,
            String userUuid,
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
        attributes.put("userUuid", userUuid);
        platformEventPublisher.record(
                eventType,
                userId,
                AiEventTypes.AGGREGATE_KNOWLEDGE_DOCUMENT,
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
            Long knowledgeBaseId,
            Long fileId,
            String title,
            Long createdBy,
            String createdByUserUuid,
            Long simulatedRoleId,
            String createdByUsername,
            Integer retryCount,
            String claimToken
    ) {
        private PendingIndexTask withClaimToken(String claimToken) {
            return new PendingIndexTask(
                    documentId,
                    knowledgeBaseId,
                    fileId,
                    title,
                    createdBy,
                    createdByUserUuid,
                    simulatedRoleId,
                    createdByUsername,
                    retryCount,
                    claimToken
            );
        }
    }

    private record IndexOwnerContext(Long userId, String userUuid, String username, Long simulatedRoleId) {
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

    private record EmployeeBindingContext(
            Long id,
            String username,
            Boolean enabled
    ) {
    }

    private EmployeeBindingContext requireEmployeeBindingContext(Long employeeId) {
        EmployeeBindingContext employee = jdbcTemplate.queryForObject(
                """
                        select id, username, enabled
                        from ai_employee
                        where id = ? and is_deleted = 0
                        limit 1
                        """,
                (row, rowNum) -> new EmployeeBindingContext(
                        row.getLong("id"),
                        row.getString("username"),
                        row.getObject("enabled", Boolean.class)
                ),
                employeeId
        );
        if (employee == null || employee.id() == null || !StringUtils.hasText(employee.username())) {
            throw new BizException(ErrorCode.NOT_FOUND, "Employee not found");
        }
        return employee;
    }

    private AiVO.KnowledgeBaseVO requireKnowledgeBase(CurrentUser currentUser, Long id, KnowledgeAccess access) {
        requireLogin(currentUser);
        List<Object> args = new ArrayList<>();
        args.add(id);
        StringBuilder where = new StringBuilder(" where kb.id = ? and kb.is_deleted = 0");
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, "kb", access, null);
        AiVO.KnowledgeBaseVO result = jdbcTemplate.queryForObject(
                """
                        select kb.id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                               kb.owner_user_id, kb.owner_user_uuid, kb.created_by, kb.create_time, kb.update_time,
                               coalesce(kb.document_count, 0) as document_count,
                               coalesce(kb.chunk_count, 0) as chunk_count
                        from ai_knowledge_base kb
                        """ + sqlClause(where) + """
                        limit 1
                        """,
                this::mapKnowledgeBase,
                args.toArray()
        );
        if (result == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Knowledge base not found");
        }
        return result;
    }

    private AiVO.KnowledgeDocumentVO requireDocument(Long knowledgeBaseId, Long documentId) {
        AiVO.KnowledgeDocumentVO result = jdbcTemplate.queryForObject(
                """
                        select id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                               mime_type, file_size_bytes, status, parse_error, extracted_char_count, chunk_count,
                               created_by, created_by_uuid as created_by_user_uuid, create_time, update_time
                        from ai_knowledge_document
                        where knowledge_base_id = ? and id = ? and is_deleted = 0
                        limit 1
                        """,
                this::mapKnowledgeDocument,
                knowledgeBaseId,
                documentId
        );
        if (result == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Knowledge document not found");
        }
        return result;
    }

    private AiVO.KnowledgeBaseVO mapKnowledgeBase(SqlRow row, int rowNum) {
        AiVO.KnowledgeBaseVO vo = new AiVO.KnowledgeBaseVO();
        vo.setId(row.getLong("id"));
        vo.setKbCode(row.getString("kb_code"));
        vo.setName(row.getString("name"));
        vo.setDescription(row.getString("description"));
        vo.setStatus(row.getString("status"));
        vo.setVisibilityScope(row.getString("visibility_scope"));
        vo.setOwnerUserId(row.getObject("owner_user_id", Long.class));
        vo.setOwnerUserUuid(row.getString("owner_user_uuid"));
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
        vo.setCreatedByUuid(row.getString("created_by_user_uuid"));
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
        byte[] vectorBlob = row.getObject("embedding_vector_blob", byte[].class);
        Double vectorNorm = row.getObject("embedding_norm", Double.class);
        double[] vector = vectorService.parseBlob(vectorBlob);
        double score = vector.length > 0
                ? vectorService.score(queryVector, vector, vectorNorm, query, row.getString("content"), row.getString("document_title"), row.getString("knowledge_base_name"))
                : vectorService.score(queryVector, row.getString("embedding_vector_json"), query, row.getString("content"), row.getString("document_title"), row.getString("knowledge_base_name"));
        return new VectorSearchCandidate(reference, score);
    }

    private record VectorSearchCandidate(
            AiVO.KnowledgeReferenceVO reference,
            double score
    ) implements AiKnowledgeVectorService.ScoredCandidate {
    }

    private void requireKnowledgeBaseWrite(int updated) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Knowledge base changed, please retry", "Knowledge base changed, please retry");
        }
    }

    private void requireKnowledgeDocumentWrite(int updated, String message) {
        if (updated <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, message, message);
        }
    }

    private void validateKnowledgeName(Long ownerUserId, String ownerUserUuid, String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Knowledge base name is required", "Knowledge base name is required");
        }
        boolean exists = jdbcTemplate.exists(
                """
                        select 1
                        from ai_knowledge_base
                        where owner_user_id = ? and owner_user_uuid = ? and name = ? and is_deleted = 0 and (? is null or id <> ?)
                        limit 1
                        """,
                ownerUserId,
                ownerUserUuid,
                name.trim(),
                excludeId,
                excludeId
        );
        if (exists) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Knowledge base name already exists", "Knowledge base name already exists");
        }
    }

    private String visibleUploadMessage(BizException exception) {
        if (StringUtils.hasText(exception.getUserMessage())
                && !ErrorCode.SYSTEM_ERROR.getDefaultUserMessage().equals(exception.getUserMessage())
                && !ErrorCode.BIZ_ERROR.getDefaultUserMessage().equals(exception.getUserMessage())) {
            return exception.getUserMessage().trim();
        }
        if (exception.getErrorCode() == ErrorCode.SYSTEM_ERROR) {
            return "Please check the storage space configuration and try again later";
        }
        if (StringUtils.hasText(exception.getMessage())) {
            return exception.getMessage().trim();
        }
        return "Please retry later";
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
            where.append(" and ").append(alias).append(".owner_user_id = ? and ").append(alias).append(".owner_user_uuid = ?");
            args.add(currentUserId(currentUser));
            args.add(currentUser.getUserUuid());
            return;
        }
        if (isPlatformScope(normalizedScope)) {
            appendPlatformVisibilityFilter(where, args, alias);
            return;
        }
        if ("SHARED".equals(normalizedScope)) {
            where.append(" and not (").append(alias).append(".owner_user_id = ? and ").append(alias).append(".owner_user_uuid = ?)");
            args.add(currentUserId(currentUser));
            args.add(currentUser.getUserUuid());
            where.append(" and ").append(buildAclExistsClause(alias, currentUser, access, args));
            return;
        }

        where.append(" and (").append(alias).append(".owner_user_id = ? and ").append(alias).append(".owner_user_uuid = ?");
        args.add(currentUserId(currentUser));
        args.add(currentUser.getUserUuid());
        if (access != KnowledgeAccess.MANAGE) {
            where.append(" or ");
            appendPlatformVisibilityPredicate(where, args, alias);
        }
        where.append(" or ").append(buildAclExistsClause(alias, currentUser, access, args)).append(")");
    }

    private void appendScopeFilter(StringBuilder where, List<Object> args, CurrentUser currentUser, String alias, String scope) {
        if (!StringUtils.hasText(scope)) {
            return;
        }
        String normalizedScope = scope.trim().toUpperCase(Locale.ROOT);
        if ("OWNED".equals(normalizedScope)) {
            where.append(" and ").append(alias).append(".owner_user_id = ? and ").append(alias).append(".owner_user_uuid = ?");
            args.add(currentUserId(currentUser));
            args.add(currentUser.getUserUuid());
        } else if ("SHARED".equals(normalizedScope)) {
            where.append(" and not (").append(alias).append(".owner_user_id = ? and ").append(alias).append(".owner_user_uuid = ?)");
            args.add(currentUserId(currentUser));
            args.add(currentUser.getUserUuid());
            where.append(" and ").append(buildAclExistsClause(alias, currentUser, KnowledgeAccess.VIEW, args));
        } else if (isPlatformScope(normalizedScope)) {
            appendPlatformVisibilityFilter(where, args, alias);
        }
    }

    private void appendPlatformVisibilityFilter(StringBuilder where, List<Object> args, String alias) {
        where.append(" and ");
        appendPlatformVisibilityPredicate(where, args, alias);
    }

    private void appendPlatformVisibilityPredicate(StringBuilder where, List<Object> args, String alias) {
        where.append(alias).append(".visibility_scope in (?, ?)");
        args.add(SCOPE_PLATFORM);
    }

    private boolean isPlatformScope(String scope) {
        return SCOPE_PLATFORM.equals(scope);
    }

    private String buildAclExistsClause(String alias, CurrentUser currentUser, KnowledgeAccess access, List<Object> args) {
        List<String> permissions = switch (access) {
            case MANAGE -> List.of("MANAGE");
            case USE -> List.of("USE", "MANAGE");
            case VIEW -> List.of("VIEW", "USE", "MANAGE");
        };
        StringBuilder clause = new StringBuilder();
        clause.append("exists (select 1 from ai_knowledge_base_acl acl where acl.knowledge_base_id = ")
                .append(alias)
                .append(".id and acl.is_deleted = 0 and acl.permission in (")
                .append("?,".repeat(permissions.size()));
        clause.setLength(clause.length() - 1);
        clause.append(") and (");
        args.addAll(permissions);

        List<String> subjectClauses = new ArrayList<>();
        subjectClauses.add("(acl.subject_type = 'USER' and acl.subject_id = ?)");
        args.add(requireLogin(currentUser));
        Set<Long> roleIds = currentUser.getRoleIds() == null ? Set.of() : currentUser.getRoleIds();
        if (!roleIds.isEmpty()) {
            subjectClauses.add("(acl.subject_type = 'ROLE' and acl.subject_id in (" + "?,".repeat(roleIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(roleIds);
        }
        Set<Long> deptIds = new LinkedHashSet<>(currentUser.getDeptIds() == null ? Set.of() : currentUser.getDeptIds());
        if (currentUser.getPrimaryDeptId() != null) {
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

    private Long requireLogin(CurrentUser currentUser) {
        CurrentUser runtimeUser = requireTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(runtimeUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
        return runtimeUser.getUserId();
    }

    private Long requireKnowledgeViewPermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_VIEW);
    }

    private Long requireKnowledgeCreatePermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_CREATE);
    }

    private Long requireKnowledgeUpdatePermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_UPDATE);
    }

    private Long requireKnowledgeDeletePermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_DELETE);
    }

    private Long requireKnowledgeBindPermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_BIND);
    }

    private Long requireKnowledgeQueryPermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_QUERY);
    }

    private Long requireKnowledgeDocumentUploadPermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_DOCUMENT_UPLOAD);
    }

    private Long requireKnowledgeDocumentDeletePermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_DOCUMENT_DELETE);
    }

    private Long requireKnowledgeDocumentIndexPermission(CurrentUser currentUser) {
        return requirePermission(currentUser, PERMISSION_KNOWLEDGE_DOCUMENT_INDEX);
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        CurrentUser runtimeUser = requireTrustedCurrentUser(currentUser);
        Long userId = runtimeUser.getUserId();
        if (!hasPermission(runtimeUser, permissionKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return userId;
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*") || permissions.contains(permissionKey);
    }

    private Long currentUserId(CurrentUser currentUser) {
        return requireLogin(currentUser);
    }

    private String trustedUsername(CurrentUser currentUser) {
        return requireTrustedCurrentUser(currentUser).getUsername();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        return requireTrustedCurrentUser(currentUser).getUserUuid().trim();
    }

    private Long trustedSimulatedRoleId(CurrentUser currentUser) {
        return normalizeSimulatedRoleId(requireTrustedCurrentUser(currentUser).getSimulatedRoleId());
    }

    private String sqlClause(StringBuilder clause) {
        return clause + "\n";
    }

    private boolean hasAllPermission(CurrentUser currentUser) {
        return trustedPermissions(currentUser).contains("*");
    }

    private boolean canPublishPlatformKnowledge(CurrentUser currentUser) {
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*") || permissions.contains("ai:knowledge:share");
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        if (!isTrustedCurrentUser(currentUser)) {
            return Set.of();
        }
        CurrentUser runtimeUser = requireTrustedCurrentUser(currentUser);
        return runtimeUser.getPermissions() == null ? Set.of() : runtimeUser.getPermissions();
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private CurrentUser requireTrustedCurrentUser(CurrentUser currentUser) {
        CurrentUser runtimeUser = refreshTrustedCurrentUser(currentUser);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(runtimeUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Login required");
        }
        return runtimeUser;
    }

    private CurrentUser refreshTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return currentUser;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshedUser = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    )
            );
            copyTrustedCurrentUser(currentUser, refreshedUser);
            return currentUser;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user resolver is unavailable");
            }
            return currentUser;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
        }
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String currentUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(currentUserUuid)
                    || !normalizedUserUuid.equals(currentUserUuid)
                    || !STATUS_ENABLED.equalsIgnoreCase(userSnapshot.status())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            currentUser.setUserId(userId);
            currentUser.setUserUuid(currentUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
            normalizedUserUuid = currentUserUuid;
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        AiPermissionSnapshotResolver.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.FORBIDDEN, "Trusted user permission snapshot is unavailable");
            }
            return currentUser;
        }
        CurrentUser refreshed = new CurrentUser(
                userId,
                currentUser.getUsername(),
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                true,
                snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()),
                snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()),
                snapshot.getPrimaryDeptId(),
                snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()),
                snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()),
                snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes())
        );
        refreshed.setUserUuid(normalizedUserUuid);
        refreshed.setPermissionsVersion(snapshot.getVersion());
        refreshed.setDefaultHomePath(snapshot.getDefaultHomePath());
        refreshed.setRequiresPasswordChange(currentUser.getRequiresPasswordChange());
        refreshed.setSimulatedRoleId(simulatedRoleId);
        refreshed.setLoginType(currentUser.getLoginType());
        copyTrustedCurrentUser(currentUser, refreshed);
        return currentUser;
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(AiTrustedSessionResolver.AuthenticatedAccess authenticatedAccess) {
        CurrentUser refreshedUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(refreshedUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Trusted user identity is required");
        }
        return refreshedUser;
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions() == null ? Set.of() : Set.copyOf(source.getPermissions()));
        target.setRoleIds(source.getRoleIds() == null ? Set.of() : Set.copyOf(source.getRoleIds()));
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds() == null ? Set.of() : Set.copyOf(source.getDeptIds()));
        target.setDescendantDeptIds(source.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(source.getDescendantDeptIds()));
        target.setDataScopes(source.getDataScopes() == null ? List.of() : List.copyOf(source.getDataScopes()));
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
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

        if (!SCOPE_PERSONAL.equals(normalized) && !SCOPE_PLATFORM.equals(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Knowledge visibility scope is not supported");
        }
        if (SCOPE_PLATFORM.equals(normalized) && !canPublishPlatformKnowledge(currentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Publishing platform knowledge is not allowed");
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
            return "Untitled document";
        }
        String trimmed = filename.trim();
        int dot = trimmed.lastIndexOf('.');
        return dot > 0 ? trimmed.substring(0, dot) : trimmed;
    }
}
