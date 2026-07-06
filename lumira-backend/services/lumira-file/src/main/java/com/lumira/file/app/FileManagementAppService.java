package com.lumira.file.app;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.common.vo.PageResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.common.web.security.audit.SecurityAuditEvent;
import com.lumira.common.web.security.audit.SecurityAuditEventService;
import com.lumira.file.config.UploadProperties;
import com.lumira.file.dto.FileStorageSpaceRequest;
import com.lumira.file.domain.model.FileDomainModels.FileObjectAggregate;
import com.lumira.file.entity.FileObjectEntity;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.mapper.FileObjectMapper;
import com.lumira.file.mapper.FileStorageSpaceMapper;
import com.lumira.file.processing.FileProcessingTaskRequestService;
import com.lumira.file.security.SafeUrlValidator;
import com.lumira.file.vo.FileVO;
import com.lumira.file.upload.DocumentUploadService;
import com.lumira.file.upload.FileStorageMetrics;
import com.lumira.file.upload.ImageUploadService;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Lazy
@Service
public class FileManagementAppService {

    private static final Logger log = LoggerFactory.getLogger(FileManagementAppService.class);
    private static final String STORAGE_TEST_PUBLIC_ERROR = "存储空间不可访问或配置不正确";

    private static final long MAX_PAGE_SIZE = 100L;
    private static final long FILE_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final long STORAGE_SPACE_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final long MAX_IN_MEMORY_FILE_CONTENT_BYTES = 10L * 1024L * 1024L;
    private static final Duration FILE_LIST_CACHE_TTL = Duration.ofSeconds(30);
    private static final String RESOURCE_FILE_OBJECT = "file:object";
    private static final String DEFAULT_SORT_COLUMN = "created_at";
    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_SHARED = "shared";
    public static final String SCOPE_DOWNLOAD_CENTER = "download-center";
    private static final String STORAGE_KEY_LOCAL = "local";
    private static final String STORAGE_KEY_DOWNLOAD_CENTER = "download_center";
    private static final String LEGACY_STORAGE_KEY_SYSTEM_PUBLIC = "system_public";
    private static final Long SYSTEM_OPERATOR_ID = 1L;
    private static final String SYSTEM_OPERATOR_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String VISIBILITY_SCOPE_PERSONAL = "PERSONAL";
    private static final String VISIBILITY_SCOPE_DOWNLOAD_CENTER = "DOWNLOAD_CENTER";
    private static final String VISIBILITY_SCOPE_PUBLIC = "PUBLIC";
    private static final List<DefaultStorageSpace> DEFAULT_STORAGE_SPACES = List.of(
            new DefaultStorageSpace("用户上传文件", STORAGE_KEY_LOCAL, "storage/uploads/", "", true, 20, true),
            new DefaultStorageSpace("下载中心", STORAGE_KEY_DOWNLOAD_CENTER, "storage/uploads/download_center/", "", false, 100, true),
            new DefaultStorageSpace("AI 聊天附件", "ai_chat", "storage/uploads/ai_chat/", "", false, 20, false),
            new DefaultStorageSpace("头像文件", "avatar", "storage/uploads/avatar/", "", false, 10, true),
            new DefaultStorageSpace("Support feedback images", "support_feedback", "storage/uploads/support_feedback/", "", false, 20, true)
    );
    private static final Map<String, String> SORT_COLUMN_MAPPING = Map.ofEntries(
            Map.entry("createdAt", "created_at"),
            Map.entry("updatedAt", "updated_at"),
            Map.entry("originalFileName", "original_filename"),
            Map.entry("fileExtension", "file_extension"),
            Map.entry("fileSizeBytes", "file_size"),
            Map.entry("uploadedByName", "uploaded_by_name"),
            Map.entry("category", "category"),
            Map.entry("previewMode", "preview_mode")
    );

    private final FileObjectMapper fileObjectMapper;
    private final FileStorageSpaceMapper fileStorageSpaceMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UploadProperties uploadProperties;
    private final DocumentUploadService documentUploadService;
    private final ImageUploadService imageUploadService;
    private final DomainEventPublisher domainEventPublisher;
    private final FileProcessingTaskRequestService fileProcessingTaskRequestService;
    private final FieldCryptoService fieldCryptoService;
    private final FileStorageMetrics storageMetrics;
    private final SafeUrlValidator safeUrlValidator;
    private final SecurityAuditEventService securityAuditEventService;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private final Map<String, CachedFilePage> localFileListCache = new ConcurrentHashMap<>();

    public FileManagementAppService(
            FileObjectMapper fileObjectMapper,
            FileStorageSpaceMapper fileStorageSpaceMapper,
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService,
            @Qualifier("fileDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            FileProcessingTaskRequestService fileProcessingTaskRequestService,
            FieldCryptoService fieldCryptoService,
            FileStorageMetrics storageMetrics,
            SafeUrlValidator safeUrlValidator
    ) {
        this(fileObjectMapper, fileStorageSpaceMapper, jdbcTemplate, uploadProperties, documentUploadService,
                imageUploadService, domainEventPublisher, fileProcessingTaskRequestService, fieldCryptoService,
                storageMetrics, safeUrlValidator, null, null);
    }

    @Autowired
    public FileManagementAppService(
            FileObjectMapper fileObjectMapper,
            FileStorageSpaceMapper fileStorageSpaceMapper,
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService,
            @Qualifier("fileDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            FileProcessingTaskRequestService fileProcessingTaskRequestService,
            FieldCryptoService fieldCryptoService,
            FileStorageMetrics storageMetrics,
            SafeUrlValidator safeUrlValidator,
            SecurityAuditEventService securityAuditEventService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.fileStorageSpaceMapper = fileStorageSpaceMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.documentUploadService = documentUploadService;
        this.imageUploadService = imageUploadService;
        this.domainEventPublisher = domainEventPublisher;
        this.fileProcessingTaskRequestService = fileProcessingTaskRequestService;
        this.fieldCryptoService = fieldCryptoService;
        this.storageMetrics = storageMetrics;
        this.safeUrlValidator = safeUrlValidator;
        this.securityAuditEventService = securityAuditEventService;
        this.systemInternalApiProvider = systemInternalApiProvider;
    }

    public PageResponse<FileObjectDTO> listFiles(
            CurrentUser currentUser,
            String keyword,
            String category,
            String fileExtension,
            String previewMode,
            String bucket,
            String scope,
            long pageNo,
            long pageSize,
            String sortField,
            String sortOrder
    ) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        boolean sharedScope = isSharedScope(scope);
        boolean downloadCenterScope = SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, actor, sharedScope, downloadCenterScope);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like("original_filename", normalizedKeyword)
                    .or()
                    .like("category", normalizedKeyword)
                    .or()
                    .like("tags", normalizedKeyword)
                    .or()
                    .like("remark", normalizedKeyword));
        }
        if (StringUtils.hasText(category)) {
            queryWrapper.eq("category", category.trim());
        }
        if (StringUtils.hasText(fileExtension)) {
            queryWrapper.eq("file_extension", fileExtension.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(previewMode)) {
            queryWrapper.eq("preview_mode", previewMode.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(bucket)) {
            queryWrapper.eq("bucket", normalizeStorageKey(bucket));
        }

        String sortColumn = StringUtils.hasText(sortField)
                ? SORT_COLUMN_MAPPING.getOrDefault(sortField, DEFAULT_SORT_COLUMN)
                : DEFAULT_SORT_COLUMN;
        boolean ascending = "ascend".equalsIgnoreCase(sortOrder);
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        boolean localCacheable = isDefaultListCacheable(keyword, category, fileExtension, previewMode, bucket, scope, sortField, sortOrder);
        String localCacheKey = localCacheable ? buildFileListCacheKey(actor, safePageNo, safePageSize, sortColumn, ascending) : null;
        if (localCacheable) {
            CachedFilePage cached = localFileListCache.get(localCacheKey);
            Instant now = Instant.now();
            if (cached != null && cached.expireAt().isAfter(now)) {
                return cached.page();
            }
            if (cached != null) {
                localFileListCache.remove(localCacheKey);
            }
        }
        long safeOffset = (safePageNo - 1L) * safePageSize;
        long totalLimit = calculateFileListTotalCountLimit(safePageSize, safeOffset);
        Long total = countFileObjectCandidates(queryWrapper.clone(), totalLimit);
        long normalizedTotal = normalizeTotal(total, totalLimit);
        boolean totalCapped = isTotalCapped(total, totalLimit);
        List<FileObjectDTO> records = fileObjectMapper.selectList(queryWrapper
                        .orderBy(true, ascending, sortColumn)
                        .last("limit " + safePageSize + " offset " + safeOffset))
                .stream()
                .map(this::mapFileObject)
                .map(this::enrich)
                .toList();
        FileVO.FileObjectPageResponse response = new FileVO.FileObjectPageResponse();
        response.setRecords(records);
        response.setTotal(normalizedTotal);
        response.setHasMore(totalCapped);
        response.setTotalCapped(totalCapped);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        if (localCacheable) {
            localFileListCache.put(localCacheKey, new CachedFilePage(response, Instant.now().plus(FILE_LIST_CACHE_TTL)));
        }
        return response;
    }

    private boolean isDefaultListCacheable(
            String keyword,
            String category,
            String fileExtension,
            String previewMode,
            String bucket,
            String scope,
            String sortField,
            String sortOrder
    ) {
        return !StringUtils.hasText(keyword)
                && !StringUtils.hasText(category)
                && !StringUtils.hasText(fileExtension)
                && !StringUtils.hasText(previewMode)
                && !StringUtils.hasText(bucket)
                && !StringUtils.hasText(scope)
                && !StringUtils.hasText(sortField)
                && !StringUtils.hasText(sortOrder);
    }

    private String buildFileListCacheKey(
            TrustedCurrentUser actor,
            long pageNo,
            long pageSize,
            String sortColumn,
            boolean ascending
    ) {
        Long userId = actor.userId();
        String permissionVersion = actor.permissionsVersion();
        return String.join(":",
                "file:list",
                String.valueOf(userId),
                StringUtils.hasText(permissionVersion) ? permissionVersion : "v0",
                String.valueOf(pageNo),
                String.valueOf(pageSize),
                sortColumn,
                ascending ? "asc" : "desc");
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return getFile(currentUser, fileId, sharedScope, false);
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, sharedScope, downloadCenterScope);
        return enrich(file);
    }

    public FileObjectDTO getPreviewableFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return getPreviewableFile(currentUser, fileId, sharedScope, false);
    }

    public FileObjectDTO getPreviewableFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = getFile(currentUser, fileId, sharedScope, downloadCenterScope);
        if (!Boolean.TRUE.equals(file.previewable()) || "UNSUPPORTED".equalsIgnoreCase(file.previewMode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前文件不支持在线预览");
        }
        return file;
    }

    public List<FileObjectDTO> searchFilesForInternalTool(
            CurrentUser currentUser,
            String keyword,
            String contentType,
            String status,
            boolean sharedScope,
            int limit
    ) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Invalid internal file search limit");
        }
        long safeLimit = limit;
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, actor, sharedScope, false);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like("original_filename", normalizedKeyword)
                    .or()
                    .like("category", normalizedKeyword)
                    .or()
                    .like("tags", normalizedKeyword));
        }
        if (StringUtils.hasText(contentType)) {
            String normalizedContentType = contentType.trim();
            queryWrapper.likeRight("content_type", normalizedContentType.endsWith("%")
                    ? normalizedContentType.substring(0, normalizedContentType.length() - 1)
                    : normalizedContentType);
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("status", status.trim().toUpperCase(Locale.ROOT));
        }
        return fileObjectMapper.selectList(queryWrapper
                        .orderByDesc("id")
                        .last("limit " + safeLimit))
                .stream()
                .map(this::mapFileObject)
                .map(this::enrich)
                .toList();
    }

    @Transactional
    public FileObjectDTO uploadFile(CurrentUser currentUser, MultipartFile file, String category, String tags, String remark) {
        return uploadFile(currentUser, file, category, tags, remark, null);
    }

    @Transactional
    public FileObjectDTO uploadFile(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket
    ) {
        return uploadFile(currentUser, file, category, tags, remark, bucket, null);
    }

    @Transactional
    public FileObjectDTO uploadFile(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            String scope
    ) {
        if (file == null || file.isEmpty()) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "请先选择上传文件");
        }
        String visibilityScope = resolveVisibilityScope(scope);
        if (VISIBILITY_SCOPE_PUBLIC.equals(visibilityScope)) {
            requirePermission(currentUser, "system:file:publish");
        }
        String uploadBucket = resolveUploadBucket(bucket, scope);
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (ImageUploadService.supports(originalFilename, contentType)) {
            return uploadImage(currentUser, file, category, remark, uploadBucket, visibilityScope);
        }
        if (DocumentUploadService.supports(originalFilename, contentType)) {
            return uploadDocument(currentUser, file, category, tags, remark, uploadBucket, visibilityScope);
        }
        throw visibleBizException(ErrorCode.BAD_REQUEST, "仅允许上传图片、PDF、Word、Excel、PPT、Markdown、TXT 文件");
    }

    @Transactional
    public FileObjectDTO uploadDocument(CurrentUser currentUser, MultipartFile file, String category, String tags, String remark) {
        return uploadDocument(currentUser, file, category, tags, remark, null);
    }

    @Transactional
    public FileObjectDTO uploadDocument(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket
    ) {
        return uploadDocument(currentUser, file, category, tags, remark, bucket, VISIBILITY_SCOPE_PERSONAL);
    }

    private FileObjectDTO uploadDocument(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            String visibilityScope
    ) {
        requireCurrentUser(currentUser);
        Long actorUserId = trustedUserId(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        StorageSpaceUploadContext storageContext = resolveUploadContext(bucket);
        DocumentUploadService.StoredDocument storedDocument = documentUploadService.upload(
                file,
                storageContext.storageRoot(),
                storageContext.publicPath(),
                storageContext.maxFileSizeBytes(),
                storageContext.storageSpace().renameStrategy(),
                storageContext.storageSpace().allowedMimeTypes()
        );
        Long insertedId = insertFileObject(
                currentUser,
                storageContext.storageSpace().provider(),
                storageContext.storageBucket(),
                storedDocument.relativePath(),
                storedDocument.originalFileName(),
                storedDocument.fileExtension(),
                storedDocument.contentType(),
                storedDocument.fileSizeBytes(),
                storedDocument.publicUrl(),
                storedDocument.previewMode(),
                storedDocument.previewable(),
                visibilityScope,
                StringUtils.hasText(category) ? category : "我的文件",
                tags,
                remark
        );
        FileObjectDTO uploaded = getInsertedFile(insertedId);
        localFileListCache.clear();
        publishFileUploaded(uploaded, currentUser);
        fileProcessingTaskRequestService.requestTasksForUpload(uploaded, currentUser);
        return uploaded;
    }

    @Transactional
    public FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file, String category, String remark) {
        return uploadImage(currentUser, file, category, remark, null);
    }

    @Transactional
    public FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file, String category, String remark, String bucket) {
        return uploadImage(currentUser, file, category, remark, bucket, VISIBILITY_SCOPE_PERSONAL);
    }

    @Transactional
    public FileObjectDTO uploadPublicImage(CurrentUser currentUser, MultipartFile file, String category, String remark) {
        return uploadImage(currentUser, file, category, remark, null, VISIBILITY_SCOPE_PUBLIC);
    }

    @Transactional
    public FileObjectDTO uploadPublicImage(CurrentUser currentUser, MultipartFile file, String category, String remark, String bucket) {
        return uploadImage(currentUser, file, category, remark, bucket, VISIBILITY_SCOPE_PUBLIC);
    }

    private FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file, String category, String remark, String bucket, String visibilityScope) {
        requireCurrentUser(currentUser);
        Long actorUserId = trustedUserId(currentUser);
        StorageSpaceUploadContext storageContext = resolveUploadContext(bucket);
        ImageUploadService.StoredImage storedImage = imageUploadService.upload(
                file,
                storageContext.storageRoot(),
                storageContext.publicPath(),
                storageContext.maxFileSizeBytes(),
                storageContext.storageSpace().renameStrategy(),
                storageContext.storageSpace().allowedMimeTypes()
        );
        Long insertedId = insertFileObject(
                currentUser,
                storageContext.storageSpace().provider(),
                storageContext.storageBucket(),
                storedImage.relativePath(),
                storedImage.originalFileName(),
                normalizeText(storedImage.fileExtension().replaceFirst("^\\.", "")),
                storedImage.contentType(),
                storedImage.fileSizeBytes(),
                storedImage.publicUrl(),
                resolvePreviewMode(storedImage.fileExtension(), storedImage.contentType()),
                true,
                visibilityScope,
                StringUtils.hasText(category) ? category : "图片",
                null,
                remark
        );
        FileObjectDTO uploaded = getInsertedFile(insertedId);
        localFileListCache.clear();
        publishFileUploaded(uploaded, currentUser);
        fileProcessingTaskRequestService.requestTasksForUpload(uploaded, currentUser);
        return uploaded;
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        deleteFile(currentUser, fileId, sharedScope, false);
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        FileObjectDTO file = queryFile(currentUser, fileId, sharedScope, downloadCenterScope);
        if (!shouldRetainStoredFile(file.bucket())) {
            deleteStoredFile(file);
        }
        UpdateWrapper<FileObjectEntity> updateWrapper = new UpdateWrapper<FileObjectEntity>()
                .set("deleted", 1)
                .set("updated_by", actorUserId)
                .set("updated_by_uuid", actorUserUuid)
                .set("updated_at", LocalDateTime.now())
                .eq("id", fileId)
                .eq("deleted", 0);
        if (!sharedScope && !downloadCenterScope) {
            updateWrapper
                    .eq("uploaded_by", actorUserId)
                    .eq("uploaded_by_uuid", actorUserUuid);
        }
        fileObjectMapper.update(
                null,
                updateWrapper
        );
        localFileListCache.clear();
        publishFileDeleted(file, currentUser);
    }

    private void publishFileUploaded(FileObjectDTO file, CurrentUser currentUser) {
        if (file == null) {
            return;
        }
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        FileObjectAggregate aggregate = new FileObjectAggregate(file.id(), safeFileSize(file.fileSizeBytes()));
        aggregate.recordUploaded(file.mimeType(), actor.userId(), actor.userUuid());
        domainEventPublisher.publishAll(aggregate.pullDomainEvents());
    }

    private void publishFileDeleted(FileObjectDTO file, CurrentUser currentUser) {
        if (file == null) {
            return;
        }
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        FileObjectAggregate aggregate = new FileObjectAggregate(file.id(), safeFileSize(file.fileSizeBytes()));
        aggregate.delete(actor.userId(), actor.userUuid());
        domainEventPublisher.publishAll(aggregate.pullDomainEvents());
    }

    private long safeFileSize(Long fileSizeBytes) {
        return fileSizeBytes == null ? 0L : Math.max(0L, fileSizeBytes);
    }

    public PageResponse<StorageSpaceDTO> listStorageSpaces(CurrentUser currentUser, long pageNo, long pageSize) {
        requireCurrentUser(currentUser);
        ensureDefaultStorageSpaces();
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safeOffset = (safePageNo - 1L) * safePageSize;
        long totalLimit = calculateStorageSpaceListTotalCountLimit(safePageSize, safeOffset);
        QueryWrapper<FileStorageSpaceEntity> countQueryWrapper = new QueryWrapper<FileStorageSpaceEntity>()
                .eq("deleted", 0);
        Long total = countStorageSpaceCandidates(countQueryWrapper.clone(), totalLimit);
        long normalizedTotal = normalizeTotal(total, totalLimit);
        boolean totalCapped = isTotalCapped(total, totalLimit);
        List<StorageSpaceDTO> records = fileStorageSpaceMapper
                .listWithUsage(safePageSize, safeOffset)
                .stream()
                .map(this::mapStorageSpace)
                .toList();
        FileVO.StorageSpacePageResponse response = new FileVO.StorageSpacePageResponse();
        response.setRecords(records);
        response.setTotal(normalizedTotal);
        response.setHasMore(totalCapped);
        response.setTotalCapped(totalCapped);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long calculateFileListTotalCountLimit(long pageSize, long offset) {
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safeOffset = Math.max(0L, offset);
        long dynamicLimit = safeOffset + safePageSize + 1L;
        return Math.min(dynamicLimit, FILE_LIST_TOTAL_COUNT_CAP);
    }

    private long calculateStorageSpaceListTotalCountLimit(long pageSize, long offset) {
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safeOffset = Math.max(0L, offset);
        long dynamicLimit = safeOffset + safePageSize + 1L;
        return Math.min(dynamicLimit, STORAGE_SPACE_LIST_TOTAL_COUNT_CAP);
    }

    private long countFileObjectCandidates(QueryWrapper<FileObjectEntity> queryWrapper, long limit) {
        if (limit <= 0L) {
            return 0L;
        }
        QueryWrapper<FileObjectEntity> countQuery = queryWrapper.clone()
                .select("id")
                .last("limit " + limit);
        List<FileObjectEntity> candidates = fileObjectMapper.selectList(countQuery);
        return candidates == null ? 0L : candidates.size();
    }

    private long countStorageSpaceCandidates(QueryWrapper<FileStorageSpaceEntity> queryWrapper, long limit) {
        if (limit <= 0L) {
            return 0L;
        }
        QueryWrapper<FileStorageSpaceEntity> countQuery = queryWrapper.clone()
                .select("id")
                .last("limit " + limit);
        List<FileStorageSpaceEntity> candidates = fileStorageSpaceMapper.selectList(countQuery);
        return candidates == null ? 0L : candidates.size();
    }

    private long normalizeTotal(Long total, long limit) {
        if (total == null || total <= 0L) {
            return 0L;
        }
        if (limit <= 0L) {
            return Math.max(0L, total);
        }
        return Math.min(total, limit);
    }

    private boolean isTotalCapped(Long total, long limit) {
        if (limit <= 0L || total == null) {
            return false;
        }
        return total >= limit;
    }

    public StorageSpaceDTO getStorageSpace(CurrentUser currentUser, String storageKey) {
        requireCurrentUser(currentUser);
        return queryStorageSpace(normalizeStorageKey(storageKey));
    }

    public FileStorageSpaceRequest.TestResult testStorageSpace(CurrentUser currentUser, Long id) {
        requireCurrentUser(currentUser);
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByIdWithUsage(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        long startedAt = System.nanoTime();
        FileStorageSpaceRequest.TestResult result = new FileStorageSpaceRequest.TestResult();
        result.setProvider(entity.getProvider());
        try {
            if ("LOCAL".equalsIgnoreCase(entity.getProvider())) {
                Path root = resolveStorageRoot(entity);
                Files.createDirectories(root);
                if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                    throw new IOException("本地存储目录不可写: " + root);
                }
                result.setStatus("UP");
                result.setMessage("本地存储目录可写");
                return result;
            }
            validateRemoteStorage(entity);
            result.setStatus("UP");
            result.setMessage("对象存储配置完整，Endpoint 可访问");
            return result;
        } catch (Exception ex) {
            log.warn("Storage space test failed storageSpaceId={} provider={} reason={}",
                    id, entity.getProvider(), ex.getMessage(), ex);
            recordStorageSpaceTestAudit(currentUser, id, entity.getProvider(), ex);
            result.setStatus("DOWN");
            result.setMessage(STORAGE_TEST_PUBLIC_ERROR);
            return result;
        } finally {
            result.setResponseTimeMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }
    }

    @Transactional
    public StorageSpaceDTO createStorageSpace(CurrentUser currentUser, FileStorageSpaceRequest request) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        String provider = normalizeProvider(request.getProvider());
        String storageKey = normalizeStorageKey(StringUtils.hasText(request.getStorageKey()) ? request.getStorageKey() : provider.toLowerCase(Locale.ROOT) + "_" + shortId());
        StoragePayload payload = normalizeStoragePayload(request, provider, storageKey, null);
        if (payload.defaultStorage()) {
            clearDefaultStorage();
        }
        try {
            fileStorageSpaceMapper.insert(buildStorageSpaceEntity(payload, actorUserId, actorUserUuid));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间标识已存在");
        }
        ensureOneDefaultStorage();
        return queryStorageSpace(storageKey);
    }

    @Transactional
    public StorageSpaceDTO updateStorageSpace(CurrentUser currentUser, Long id, FileStorageSpaceRequest request) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        StorageSpaceDTO existing = queryStorageSpaceById(id);
        StoragePayload payload = normalizeStoragePayload(request, existing.provider(), existing.storageKey(), existing);
        if (payload.defaultStorage()) {
            clearDefaultStorage();
        }
        fileStorageSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                        .set(FileStorageSpaceEntity::getTitle, payload.title())
                        .set(FileStorageSpaceEntity::getRootPath, payload.rootPath())
                        .set(FileStorageSpaceEntity::getBucketName, payload.bucketName())
                        .set(FileStorageSpaceEntity::getEndpoint, payload.endpoint())
                        .set(FileStorageSpaceEntity::getRegion, payload.region())
                        .set(FileStorageSpaceEntity::getAccessKeyId, payload.accessKeyId())
                        .set(FileStorageSpaceEntity::getAccessKeySecret, encryptSecret(payload.accessKeySecret()))
                        .set(FileStorageSpaceEntity::getRenameStrategy, payload.renameStrategy())
                        .set(FileStorageSpaceEntity::getMaxFileSizeMb, payload.maxFileSizeMb())
                        .set(FileStorageSpaceEntity::getAllowedMimeTypes, payload.allowedMimeTypes())
                        .set(FileStorageSpaceEntity::getDefaultFlag, payload.defaultStorage() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getRetainFileOnRecordDelete, payload.retainFileOnRecordDelete() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getAnonymousAccessAllowed, payload.anonymousAccessAllowed() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getStatus, payload.status())
                        .set(FileStorageSpaceEntity::getUpdatedBy, actorUserId)
                        .set(FileStorageSpaceEntity::getUpdatedByUuid, actorUserUuid)
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getId, id)
                        .eq(FileStorageSpaceEntity::getStorageKey, existing.storageKey())
                        .eq(FileStorageSpaceEntity::getProvider, existing.provider())
                        .eq(FileStorageSpaceEntity::getStatus, existing.status())
                        .eq(FileStorageSpaceEntity::getDefaultFlag, Boolean.TRUE.equals(existing.defaultStorage()) ? 1 : 0)
                        .eq(FileStorageSpaceEntity::getDeleted, 0)
        );
        ensureOneDefaultStorage();
        return queryStorageSpaceById(id);
    }

    @Transactional
    public void deleteStorageSpace(CurrentUser currentUser, Long id) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        StorageSpaceDTO existing = queryStorageSpaceById(id);
        if (hasFileRecordsInBucket(existing.storageKey())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间下仍有文件，不能删除");
        }
        if (Boolean.TRUE.equals(existing.defaultStorage())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "默认存储空间不能删除");
        }
        fileStorageSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                        .set(FileStorageSpaceEntity::getDeleted, 1)
                        .set(FileStorageSpaceEntity::getUpdatedBy, actorUserId)
                        .set(FileStorageSpaceEntity::getUpdatedByUuid, actorUserUuid)
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getId, id)
                        .eq(FileStorageSpaceEntity::getStorageKey, existing.storageKey())
                        .eq(FileStorageSpaceEntity::getProvider, existing.provider())
                        .eq(FileStorageSpaceEntity::getStatus, existing.status())
                        .eq(FileStorageSpaceEntity::getDefaultFlag, Boolean.TRUE.equals(existing.defaultStorage()) ? 1 : 0)
                        .eq(FileStorageSpaceEntity::getDeleted, 0)
        );
    }

    private boolean hasFileRecordsInBucket(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return false;
        }
        QueryWrapper<FileObjectEntity> query = new QueryWrapper<FileObjectEntity>()
                .select("1")
                .eq("bucket", storageKey)
                .eq("deleted", 0)
                .last("limit 1");
        return fileObjectMapper.selectOne(query) != null;
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return resolveFilePath(currentUser, fileId, sharedScope, false);
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, sharedScope, downloadCenterScope);
        Path target = resolveFilePath(file);
        if (target == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件路径无效");
        }
        return target;
    }

    public FileContentDTO readFileContent(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, sharedScope, downloadCenterScope);
        Path target = resolveFilePath(file);
        if (target == null || !Files.exists(target) || !Files.isRegularFile(target)) {
            storageMetrics.recordMissing("read", file.storageType(), Duration.ZERO);
            throw new BizException(ErrorCode.NOT_FOUND, "文件内容不存在");
        }
        Instant readStartedAt = Instant.now();
        try {
            long size = Files.size(target);
            if (size > MAX_IN_MEMORY_FILE_CONTENT_BYTES) {
                storageMetrics.recordFailed("read", file.storageType(), Duration.between(readStartedAt, Instant.now()));
                throw new BizException(ErrorCode.BAD_REQUEST, "File is too large to read into memory");
            }
            FileContentDTO content = new FileContentDTO(
                    file.id(),
                    file.originalFileName(),
                    file.mimeType(),
                    file.fileExtension(),
                    Files.readAllBytes(target)
            );
            storageMetrics.recordSucceeded("read", file.storageType(), Duration.between(readStartedAt, Instant.now()));
            return content;
        } catch (IOException exception) {
            storageMetrics.recordFailed("read", file.storageType(), Duration.between(readStartedAt, Instant.now()));
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取文件内容失败");
        }
    }

    public FileProcessingArtifactDTO readProcessingArtifact(
            CurrentUser currentUser,
            Long fileId,
            String artifactType,
            boolean sharedScope,
            boolean downloadCenterScope
    ) {
        if (!StringUtils.hasText(artifactType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件处理产物类型不能为空");
        }
        FileObjectDTO file = queryFile(currentUser, fileId, sharedScope, downloadCenterScope);
        List<FileProcessingArtifactDTO> artifacts = jdbcTemplate.query(
                """
                        select id, file_id, task_type, artifact_type, artifact_path,
                               content_text, content_length, updated_at
                        from file_processing_artifact
                        where file_id = ? and artifact_type = ? and deleted = 0
                        order by updated_at desc, id desc
                        limit 1
                        """,
                (rs, rowNum) -> new FileProcessingArtifactDTO(
                        rs.getLong("id"),
                        rs.getLong("file_id"),
                        rs.getString("task_type"),
                        rs.getString("artifact_type"),
                        rs.getString("artifact_path"),
                        rs.getString("content_text"),
                        rs.getInt("content_length"),
                        rs.getObject("updated_at", LocalDateTime.class)
                ),
                file.id(),
                artifactType.trim().toUpperCase(Locale.ROOT)
        );
        if (artifacts.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件处理产物不存在");
        }
        return artifacts.getFirst();
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return queryFile(currentUser, fileId, sharedScope, false);
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        if (fileId == null || fileId <= 0) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Valid file id is required");
        }
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("id", fileId)
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, actor, sharedScope, downloadCenterScope);
        FileObjectEntity entity = fileObjectMapper.selectOne(queryWrapper);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return mapFileObject(entity);
    }

    private FileObjectDTO enrich(FileObjectDTO file) {
        return new FileObjectDTO(
                file.id(),
                file.uploadedBy(),
                file.uploadedByUuid(),
                file.uploadedByName(),
                file.originalFileName(),
                file.storedFileName(),
                file.storageType(),
                file.bucket(),
                file.fileExtension(),
                file.mimeType(),
                file.fileSizeBytes(),
                readableSize(file.fileSizeBytes()),
                file.storagePath(),
                file.publicUrl(),
                StringUtils.hasText(file.previewUrl()) ? file.previewUrl() : file.publicUrl(),
                StringUtils.hasText(file.downloadUrl()) ? file.downloadUrl() : file.publicUrl(),
                file.previewMode(),
                file.previewable(),
                file.category(),
                file.tags(),
                file.remark(),
                file.status(),
                file.createdAt(),
                file.updatedAt()
        );
    }

    private void deleteStoredFile(FileObjectDTO file) {
        Path target = resolveFilePath(file);
        if (target == null) {
            return;
        }
        Instant deleteStartedAt = Instant.now();
        try {
            Files.deleteIfExists(target);
            storageMetrics.recordSucceeded("delete", file.storageType(), Duration.between(deleteStartedAt, Instant.now()));
        } catch (Exception ignored) {
            storageMetrics.recordFailed("delete", file.storageType(), Duration.between(deleteStartedAt, Instant.now()));
            // Keep metadata cleanup resilient even when filesystem cleanup fails.
        }
    }

    private Path resolveFilePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        String normalizedRelativePath = relativePath.trim().replace('\\', '/');
        if (normalizedRelativePath.contains("%2e")
                || normalizedRelativePath.contains("%2E")
                || normalizedRelativePath.contains("%2f")
                || normalizedRelativePath.contains("%2F")
                || normalizedRelativePath.contains("..")
                || normalizedRelativePath.startsWith("/")
                || normalizedRelativePath.startsWith("~")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Invalid file path", "File path is invalid");
        }
        Path directPath = Path.of(normalizedRelativePath);
        if (directPath.isAbsolute()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Absolute file path is not allowed", "File path is invalid");
        }
        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        Path target = storageRoot.resolve(directPath).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "File path escapes storage root", "File path is invalid");
        }
        return target;
    }

    private void recordStorageSpaceTestAudit(CurrentUser currentUser, Long storageSpaceId, String provider, Exception ex) {
        if (securityAuditEventService == null) {
            return;
        }
        securityAuditEventService.record(SecurityAuditEvent.builder("STORAGE_SPACE_TEST_FAILED", "WARN", "DENIED")
                .userId(trustedUserIdOrNull(currentUser))
                .requestId(TraceContext.getRequestId())
                .traceId(TraceContext.getTraceId())
                .resourceCode("file_storage_space")
                .actionCode("test")
                .targetId(storageSpaceId == null ? null : String.valueOf(storageSpaceId))
                .reasonCode(ex == null ? "STORAGE_TEST_FAILED" : ex.getClass().getSimpleName())
                .message("Storage space test failed")
                .metadata(Map.of(
                        "provider", provider == null ? "" : provider,
                        "storageSpaceId", storageSpaceId == null ? "" : storageSpaceId
                ))
                .build());
    }

    private Long trustedUserIdOrNull(CurrentUser currentUser) {
        try {
            return resolveTrustedCurrentUser(currentUser).userId();
        } catch (BizException exception) {
            return null;
        }
    }

    private Path resolveFilePath(FileObjectDTO file) {
        if (file == null || !StringUtils.hasText(file.storagePath())) {
            return null;
        }
        if (StringUtils.hasText(file.bucket())) {
            FileStorageSpaceEntity storageSpace = fileStorageSpaceMapper.findByStorageKey(file.bucket());
            if (storageSpace != null) {
                Path storageRoot = resolveStorageRoot(storageSpace);
                Path target = storageRoot.resolve(validateObjectKey(file.storagePath())).normalize();
                if (target.startsWith(storageRoot)) {
                    return target;
                }
                return null;
            }
        }
        return resolveFilePath(file.storagePath());
    }

    private Path validateObjectKey(String objectKey) {
        Path target = resolveFilePath(objectKey);
        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        return storageRoot.relativize(target);
    }

    private Path resolveStorageRoot(FileStorageSpaceEntity entity) {
        return resolveStorageRoot(StringUtils.hasText(entity.getRootPath()) ? entity.getRootPath() : uploadProperties.getStorageRoot());
    }

    private void validateRemoteStorage(FileStorageSpaceEntity entity) throws IOException, InterruptedException {
        if (!StringUtils.hasText(entity.getBucketName())) {
            throw new IOException("Bucket is required");
        }
        if (!StringUtils.hasText(entity.getEndpoint())) {
            throw new IOException("Endpoint is required");
        }
        if (!StringUtils.hasText(entity.getAccessKeyId()) || !StringUtils.hasText(entity.getAccessKeySecret())) {
            throw new IOException("Access credentials are incomplete");
        }
        java.net.URI endpoint = safeUrlValidator.validateHttpUrl(entity.getEndpoint());
        java.net.http.HttpClient guardedClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response = guardedClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            throw new IOException("Remote storage redirect is not allowed");
        }
        if (response.statusCode() >= 500) {
            throw new IOException("Endpoint service unavailable");
        }
    }
    private Long insertFileObject(
            CurrentUser currentUser,
            String storageType,
            String bucket,
            String objectKey,
            String originalFilename,
            String fileExtension,
            String contentType,
            long fileSizeBytes,
            String publicUrl,
            String previewMode,
            boolean previewable,
            String visibilityScope,
            String category,
            String tags,
            String remark
    ) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        String actorUsername = actor.username();
        LocalDateTime now = LocalDateTime.now();
        FileObjectEntity entity = new FileObjectEntity();
        entity.setStorageType(storageType);
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setUploadedBy(actorUserId);
        entity.setUploadedByUuid(actorUserUuid);
        entity.setUploadedByName(actorUsername);
        entity.setDepartmentId(actor.primaryDeptId());
        entity.setVisibilityScope(resolveVisibilityScope(visibilityScope));
        entity.setOriginalFilename(originalFilename);
        entity.setFileExtension(fileExtension);
        entity.setContentType(contentType);
        entity.setFileSize(fileSizeBytes);
        entity.setPublicUrl(publicUrl);
        entity.setPreviewMode(previewMode);
        entity.setPreviewableFlag(previewable ? 1 : 0);
        entity.setCategory(normalizeText(category));
        entity.setTags(normalizeText(normalizeTags(tags)));
        entity.setRemark(normalizeText(remark));
        entity.setStatus("ENABLED");
        entity.setCreatedBy(actorUserId);
        entity.setCreatedByUuid(actorUserUuid);
        entity.setCreatedAt(now);
        entity.setUpdatedBy(actorUserId);
        entity.setUpdatedByUuid(actorUserUuid);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        fileObjectMapper.insert(entity);
        if (entity.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录保存失败");
        }
        return entity.getId();
    }

    private FileObjectDTO getInsertedFile(Long insertedId) {
        FileObjectEntity inserted = fileObjectMapper.selectById(insertedId);
        if (inserted == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录读取失败");
        }
        return enrich(mapFileObject(inserted));
    }

    private void applyFileDataPermission(QueryWrapper<FileObjectEntity> queryWrapper, TrustedCurrentUser actor, boolean sharedScopeRequested, boolean downloadCenterScope) {
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        applyFileVisibilityScope(queryWrapper, downloadCenterScope);
        if (!sharedScopeRequested) {
            queryWrapper.eq("uploaded_by", actorUserId);
            queryWrapper.eq("uploaded_by_uuid", actorUserUuid);
            return;
        }
        if (downloadCenterScope) {
            requirePermission(actor, "download:center:view");
            return;
        }
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                RESOURCE_FILE_OBJECT,
                actorUserId,
                actor.deptIds(),
                actor.descendantDeptIds(),
                actor.dataScopes(),
                actor.permissions()
        );
        if (decision.scopeType() == DataScopeType.ALL) {
            return;
        }
        Set<Long> deptIds = new java.util.LinkedHashSet<>(decision.deptIds());
        Set<Long> userIds = new java.util.LinkedHashSet<>(decision.userIds());
        if (decision.hasDeptRestriction()) {
            userIds.add(actorUserId);
        }
        if (deptIds.isEmpty() && userIds.isEmpty()) {
            queryWrapper.eq("uploaded_by", actorUserId);
            queryWrapper.eq("uploaded_by_uuid", actorUserUuid);
            return;
        }
        queryWrapper.and(nested -> {
            boolean hasDeptIds = !deptIds.isEmpty();
            if (hasDeptIds) {
                nested.in("department_id", deptIds);
            }
            if (!userIds.isEmpty()) {
                if (hasDeptIds) {
                    nested.or();
                }
                nested.in("uploaded_by", userIds);
            }
        });
    }

    private void requirePermission(CurrentUser currentUser, String permission) {
        requirePermission(resolveTrustedCurrentUser(currentUser), permission);
    }

    private void requirePermission(TrustedCurrentUser actor, String permission) {
        if (!hasPermission(actor, permission)) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "Permission denied");
        }
    }

    private Long trustedUserId(CurrentUser currentUser) {
        return resolveTrustedCurrentUser(currentUser).userId();
    }

    private String trustedUsername(CurrentUser currentUser) {
        return resolveTrustedCurrentUser(currentUser).username();
    }

    private String trustedUserUuid(CurrentUser currentUser) {
        return resolveTrustedCurrentUser(currentUser).userUuid();
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        return resolveTrustedCurrentUser(currentUser).permissions();
    }

    private boolean hasPermission(TrustedCurrentUser actor, String permission) {
        return actor.permissions().contains("*") || actor.permissions().contains(permission);
    }

    private void applyFileVisibilityScope(QueryWrapper<FileObjectEntity> queryWrapper, boolean downloadCenterScope) {
        if (downloadCenterScope) {
            queryWrapper.eq("visibility_scope", VISIBILITY_SCOPE_DOWNLOAD_CENTER);
            return;
        }
        queryWrapper
                .and(wrapper -> wrapper
                        .isNull("visibility_scope")
                        .or()
                        .ne("visibility_scope", VISIBILITY_SCOPE_DOWNLOAD_CENTER))
                .ne("bucket", STORAGE_KEY_DOWNLOAD_CENTER);
    }

    private String resolveVisibilityScope(String scope) {
        if (SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope) || VISIBILITY_SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope)) {
            return VISIBILITY_SCOPE_DOWNLOAD_CENTER;
        }
        if (VISIBILITY_SCOPE_PUBLIC.equalsIgnoreCase(scope)) {
            return VISIBILITY_SCOPE_PUBLIC;
        }
        return VISIBILITY_SCOPE_PERSONAL;
    }

    private String resolveUploadBucket(String bucket, String scope) {
        if (isDownloadCenterScope(scope)) {
            return STORAGE_KEY_DOWNLOAD_CENTER;
        }
        if (StringUtils.hasText(bucket) && STORAGE_KEY_DOWNLOAD_CENTER.equals(normalizeStorageKey(bucket))) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "普通文件不能写入下载中心存储空间");
        }
        return bucket;
    }

    private boolean isDownloadCenterScope(String scope) {
        return SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope) || VISIBILITY_SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
    }

    private boolean isSharedScope(String scope) {
        return SCOPE_SHARED.equalsIgnoreCase(scope) || SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
    }

    private StorageSpaceDTO getDefaultStorageSpace() {
        ensureDefaultStorageSpaces();
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findDefault();
        if (entity != null) {
            return mapStorageSpace(entity);
        }
        return new StorageSpaceDTO(null, "Local storage", "local", "LOCAL", "storage/uploads/", null, null, null, null, false, "APPEND_RANDOM_ID", 20, "*", true, false, true, "ENABLED", 0L, 0L, "0B", null, null);
    }

    private StorageSpaceDTO queryStorageSpace(String storageKey) {
        ensureDefaultStorageSpaces();
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByStorageKey(storageKey);
        if (entity == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "Storage space does not exist");
        }
        return mapStorageSpace(entity);
    }

    private StorageSpaceDTO queryStorageSpaceById(Long id) {
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByIdWithUsage(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Storage space does not exist");
        }
        return mapStorageSpace(entity);
    }

    private boolean shouldRetainStoredFile(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return false;
        }
        return Boolean.TRUE.equals(fileStorageSpaceMapper.shouldRetainStoredFile(bucket));
    }

    private StorageSpaceUploadContext resolveUploadContext(String bucket) {
        String normalizedBucket = StringUtils.hasText(bucket) ? normalizeStorageKey(bucket) : null;
        StorageSpaceDTO storageSpace = normalizedBucket != null
                ? findUploadStorageSpaceOrDefault(normalizedBucket)
                : getDefaultStorageSpace();
        if (!"LOCAL".equalsIgnoreCase(storageSpace.provider())) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Only local storage spaces are supported for uploads");
        }
        if (!"ENABLED".equalsIgnoreCase(storageSpace.status())) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Storage space is disabled");
        }
        Path storageRoot = resolveStorageRoot(storageSpace);
        String publicPath = resolvePublicPath(storageRoot);
        return new StorageSpaceUploadContext(storageSpace, storageSpace.storageKey(), storageRoot, publicPath, maxFileSizeBytes(storageSpace.maxFileSizeMb()));
    }

    private StorageSpaceDTO findUploadStorageSpaceOrDefault(String storageKey) {
        ensureDefaultStorageSpaces();
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByStorageKey(storageKey);
        if (entity != null) {
            return mapStorageSpace(entity);
        }
        log.warn("Upload storage space '{}' is missing, falling back to default storage space", storageKey);
        return getDefaultStorageSpace();
    }
    private long maxFileSizeBytes(Integer maxFileSizeMb) {
        int safeMaxFileSizeMb = maxFileSizeMb == null || maxFileSizeMb <= 0 ? 20 : maxFileSizeMb;
        return safeMaxFileSizeMb * 1024L * 1024L;
    }

    private Path resolveStorageRoot(StorageSpaceDTO storageSpace) {
        return resolveStorageRoot(StringUtils.hasText(storageSpace.rootPath()) ? storageSpace.rootPath() : uploadProperties.getStorageRoot());
    }

    private Path resolveStorageRoot(String rootPath) {
        Path uploadRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        if (!StringUtils.hasText(rootPath)) {
            return uploadRoot;
        }
        Path root = Path.of(rootPath);
        if (root.isAbsolute()) {
            return requireStorageRootWithinUploadRoot(root.normalize(), uploadRoot);
        }
        String normalizedRootPath = rootPath.trim().replace('\\', '/');
        while (normalizedRootPath.endsWith("/")) {
            normalizedRootPath = normalizedRootPath.substring(0, normalizedRootPath.length() - 1);
        }
        if ("storage/uploads".equals(normalizedRootPath)) {
            return uploadRoot;
        }
        if (normalizedRootPath.startsWith("storage/uploads/")) {
            return requireStorageRootWithinUploadRoot(uploadRoot.resolve(normalizedRootPath.substring("storage/uploads/".length())).normalize(), uploadRoot);
        }
        return requireStorageRootWithinUploadRoot(uploadRoot.resolve(normalizedRootPath).normalize(), uploadRoot);
    }

    private Path requireStorageRootWithinUploadRoot(Path storageRoot, Path uploadRoot) {
        Path normalizedStorageRoot = storageRoot.toAbsolutePath().normalize();
        if (!normalizedStorageRoot.startsWith(uploadRoot)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "storage root must stay under upload root");
        }
        return normalizedStorageRoot;
    }

    private String resolvePublicPath(Path storageRoot) {
        String publicPath = normalizePublicPath(uploadProperties.getPublicPath());
        Path uploadRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        Path normalizedStorageRoot = storageRoot.toAbsolutePath().normalize();
        if (!normalizedStorageRoot.equals(uploadRoot) && normalizedStorageRoot.startsWith(uploadRoot)) {
            Path relativePath = uploadRoot.relativize(normalizedStorageRoot);
            String suffix = relativePath.toString().replace('\\', '/');
            if (StringUtils.hasText(suffix)) {
                return publicPath + "/" + suffix;
            }
        }
        return publicPath;
    }

    private String normalizePublicPath(String publicPath) {
        if (!StringUtils.hasText(publicPath)) {
            return "/api/uploads";
        }
        String normalized = publicPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void clearDefaultStorage() {
        fileStorageSpaceMapper.clearDefaultStorage();
    }

    private void ensureOneDefaultStorage() {
        Long count = fileStorageSpaceMapper.countDefaultStorage();
        if (count != null && count > 0) {
            return;
        }
        fileStorageSpaceMapper.ensureFirstDefaultStorage();
    }

    private void ensureDefaultStorageSpaces() {
        mergeLegacySystemPublicStorageSpace();
        for (DefaultStorageSpace storageSpace : DEFAULT_STORAGE_SPACES) {
            StoragePayload payload = new StoragePayload(
                    storageSpace.title(),
                    storageSpace.storageKey(),
                    "LOCAL",
                    storageSpace.rootPath(),
                    storageSpace.bucketName(),
                    "",
                    "",
                    "",
                    null,
                    "APPEND_RANDOM_ID",
                    storageSpace.maxFileSizeMb(),
                    "*",
                    storageSpace.defaultStorage(),
                    false,
                    storageSpace.anonymousAccessAllowed(),
                    "ENABLED"
            );
            FileStorageSpaceEntity existing = fileStorageSpaceMapper.findByStorageKey(storageSpace.storageKey());
            if (existing != null) {
                if (storageSpace.defaultStorage()
                        && (existing.getDefaultFlag() == null || existing.getDefaultFlag() == 0)) {
                    clearDefaultStorage();
                    enableDefaultStorageSpace(storageSpace.storageKey(), storageSpace.anonymousAccessAllowed());
                } else if (storageSpace.anonymousAccessAllowed()
                        && (existing.getAnonymousAccessAllowed() == null || existing.getAnonymousAccessAllowed() == 0)) {
                    enableDefaultStorageSpaceAccess(storageSpace.storageKey());
                }
                continue;
            }
            if (payload.defaultStorage()) {
                clearDefaultStorage();
            }
            FileStorageSpaceEntity entity = buildStorageSpaceEntity(payload, SYSTEM_OPERATOR_ID, SYSTEM_OPERATOR_UUID);
            try {
                fileStorageSpaceMapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                restoreDefaultStorageSpace(payload);
            }
        }
        ensureOneDefaultStorage();
    }

    private void mergeLegacySystemPublicStorageSpace() {
        FileStorageSpaceEntity legacyStorageSpace = fileStorageSpaceMapper.findByStorageKey(LEGACY_STORAGE_KEY_SYSTEM_PUBLIC);
        if (legacyStorageSpace == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        fileObjectMapper.update(
                null,
                new UpdateWrapper<FileObjectEntity>()
                        .set("bucket", STORAGE_KEY_LOCAL)
                        .set("updated_by", SYSTEM_OPERATOR_ID)
                        .set("updated_by_uuid", SYSTEM_OPERATOR_UUID)
                        .set("updated_at", now)
                        .eq("bucket", LEGACY_STORAGE_KEY_SYSTEM_PUBLIC)
                        .eq("deleted", 0)
        );
        fileStorageSpaceMapper.update(
                null,
                new UpdateWrapper<FileStorageSpaceEntity>()
                        .set("deleted", 1)
                        .set("updated_by", SYSTEM_OPERATOR_ID)
                        .set("updated_by_uuid", SYSTEM_OPERATOR_UUID)
                        .set("updated_at", now)
                        .eq("storage_key", LEGACY_STORAGE_KEY_SYSTEM_PUBLIC)
        );
    }

    private void enableDefaultStorageSpace(String storageKey, boolean anonymousAccessAllowed) {
        fileStorageSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                        .set(FileStorageSpaceEntity::getDefaultFlag, 1)
                        .set(FileStorageSpaceEntity::getAnonymousAccessAllowed, anonymousAccessAllowed ? 1 : 0)
                        .set(FileStorageSpaceEntity::getUpdatedBy, SYSTEM_OPERATOR_ID)
                        .set(FileStorageSpaceEntity::getUpdatedByUuid, SYSTEM_OPERATOR_UUID)
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getStorageKey, storageKey)
                        .eq(FileStorageSpaceEntity::getDeleted, 0)
        );
    }

    private void enableDefaultStorageSpaceAccess(String storageKey) {
        fileStorageSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                        .set(FileStorageSpaceEntity::getAnonymousAccessAllowed, 1)
                        .set(FileStorageSpaceEntity::getUpdatedBy, SYSTEM_OPERATOR_ID)
                        .set(FileStorageSpaceEntity::getUpdatedByUuid, SYSTEM_OPERATOR_UUID)
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getStorageKey, storageKey)
                        .eq(FileStorageSpaceEntity::getDeleted, 0)
        );
    }

    private void restoreDefaultStorageSpace(StoragePayload payload) {
        fileStorageSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                        .set(FileStorageSpaceEntity::getTitle, payload.title())
                        .set(FileStorageSpaceEntity::getProvider, payload.provider())
                        .set(FileStorageSpaceEntity::getRootPath, payload.rootPath())
                        .set(FileStorageSpaceEntity::getBucketName, payload.bucketName())
                        .set(FileStorageSpaceEntity::getRenameStrategy, payload.renameStrategy())
                        .set(FileStorageSpaceEntity::getMaxFileSizeMb, payload.maxFileSizeMb())
                        .set(FileStorageSpaceEntity::getAllowedMimeTypes, payload.allowedMimeTypes())
                        .set(FileStorageSpaceEntity::getDefaultFlag, payload.defaultStorage() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getRetainFileOnRecordDelete, payload.retainFileOnRecordDelete() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getAnonymousAccessAllowed, payload.anonymousAccessAllowed() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getStatus, payload.status())
                        .set(FileStorageSpaceEntity::getDeleted, 0)
                        .set(FileStorageSpaceEntity::getUpdatedBy, SYSTEM_OPERATOR_ID)
                        .set(FileStorageSpaceEntity::getUpdatedByUuid, SYSTEM_OPERATOR_UUID)
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getStorageKey, payload.storageKey())
        );
    }
    private String relativePathFromPublicUrl(String publicUrl) {
        return publicUrl;
    }

    private String resolvePreviewMode(String extension, String contentType) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (List.of("png", "jpg", "jpeg", "gif", "bmp", "ico").contains(normalizedExtension)
                || normalizedContentType.startsWith("image/")) {
            return "IMAGE";
        }
        if ("pdf".equals(normalizedExtension) || "application/pdf".equals(normalizedContentType)) {
            return "PDF";
        }
        if (List.of("txt", "md", "csv", "json", "xml").contains(normalizedExtension) || normalizedContentType.startsWith("text/")) {
            return "TEXT";
        }
        return "UNSUPPORTED";
    }

    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        return tags.replace('，', ',').replace(';', ',').trim();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireCurrentUser(CurrentUser currentUser) {
        resolveTrustedCurrentUser(currentUser);
    }

    private BizException visibleBizException(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message, message);
    }

    private TrustedCurrentUser resolveTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "Login required");
        }
        if (systemInternalApiProvider == null) {
            return fallbackTrustedCurrentUser(currentUser);
        }
        SystemInternalApi internalApi = systemInternalApiProvider.getIfAvailable();
        if (internalApi == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user resolver is unavailable");
        }
        Long userId = currentUser.getUserId();
        String userUuid = currentUser.getUserUuid() == null ? null : currentUser.getUserUuid().trim();
        if (userId == null || userId <= 0 || !StringUtils.hasText(userUuid)) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "Login required");
        }
        SystemUserSnapshotDTO userSnapshot = internalApi.findUserIdentityById(userId);
        if (userSnapshot == null || userSnapshot.userId() == null || !userSnapshot.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user does not exist");
        }
        if (!StringUtils.hasText(userSnapshot.userUuid()) || !userSnapshot.userUuid().trim().equals(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user identity mismatch");
        }
        if (!StringUtils.hasText(userSnapshot.status()) || !"ENABLED".equalsIgnoreCase(userSnapshot.status().trim())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user is disabled");
        }
        if (!StringUtils.hasText(userSnapshot.username())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user username is unavailable");
        }
        PermissionSnapshotDTO permissionSnapshot = internalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim());
        if (permissionSnapshot == null || !StringUtils.hasText(permissionSnapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Acting user permissions are unavailable");
        }
        return new TrustedCurrentUser(
                userSnapshot.userId(),
                userSnapshot.userUuid().trim(),
                userSnapshot.username().trim(),
                permissionSnapshot.version().trim(),
                trustedStringSet(permissionSnapshot.permissions()),
                permissionSnapshot.primaryDeptId(),
                trustedLongSet(permissionSnapshot.deptIds()),
                trustedLongSet(permissionSnapshot.descendantDeptIds()),
                trustedDataScopes(permissionSnapshot)
        );
    }

    private TrustedCurrentUser fallbackTrustedCurrentUser(CurrentUser currentUser) {
        return new TrustedCurrentUser(
                currentUser.getUserId(),
                currentUser.getUserUuid().trim(),
                currentUser.getUsername().trim(),
                currentUser.getPermissionsVersion().trim(),
                trustedStringSet(currentUser.getPermissions()),
                currentUser.getPrimaryDeptId(),
                trustedLongSet(currentUser.getDeptIds()),
                trustedLongSet(currentUser.getDescendantDeptIds()),
                currentUser.getDataScopes() == null ? List.of() : List.copyOf(currentUser.getDataScopes())
        );
    }

    private Set<String> trustedStringSet(Iterable<String> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private Set<Long> trustedLongSet(Iterable<Long> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null && value > 0) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private List<DataPermissionRule> trustedDataScopes(PermissionSnapshotDTO snapshot) {
        return snapshot == null || snapshot.dataScopes() == null
                ? List.of()
                : List.copyOf(snapshot.dataScopes());
    }

    private StoragePayload normalizeStoragePayload(FileStorageSpaceRequest request, String providerFallback, String storageKeyFallback, StorageSpaceDTO existing) {
        String provider = normalizeProvider(StringUtils.hasText(request.getProvider()) ? request.getProvider() : providerFallback);
        String storageKey = normalizeStorageKey(StringUtils.hasText(request.getStorageKey()) ? request.getStorageKey() : storageKeyFallback);
        String title = defaultIfBlank(request.getTitle(), existing == null ? providerLabel(provider) : existing.title());
        String rootPath = defaultIfBlank(request.getRootPath(), existing == null ? "storage/uploads/" : existing.rootPath());
        String bucketName = defaultIfBlank(request.getBucketName(), existing == null ? "" : existing.bucketName());
        String endpoint = defaultIfBlank(request.getEndpoint(), existing == null ? "" : existing.endpoint());
        String region = defaultIfBlank(request.getRegion(), existing == null ? "" : existing.region());
        String accessKeyId = defaultIfBlank(request.getAccessKeyId(), existing == null ? "" : existing.accessKeyId());
        String accessKeySecret = StringUtils.hasText(request.getAccessKeySecret()) ? request.getAccessKeySecret().trim() : null;
        if (existing != null && !StringUtils.hasText(accessKeySecret)) {
            accessKeySecret = decryptSecret(fileStorageSpaceMapper.findAccessKeySecret(existing.id()));
        }
        String renameStrategy = normalizeRenameStrategy(defaultIfBlank(request.getRenameStrategy(), existing == null ? "APPEND_RANDOM_ID" : existing.renameStrategy()));
        Integer maxFileSizeMb = request.getMaxFileSizeMb() == null ? (existing == null ? 20 : existing.maxFileSizeMb()) : request.getMaxFileSizeMb();
        String allowedMimeTypes = defaultIfBlank(request.getAllowedMimeTypes(), existing == null ? "*" : existing.allowedMimeTypes());
        boolean defaultStorage = request.getDefaultStorage() == null ? existing == null || Boolean.TRUE.equals(existing.defaultStorage()) : request.getDefaultStorage();
        boolean retain = request.getRetainFileOnRecordDelete() == null ? existing != null && Boolean.TRUE.equals(existing.retainFileOnRecordDelete()) : request.getRetainFileOnRecordDelete();
        boolean anonymousAccessAllowed = request.getAnonymousAccessAllowed() == null ? existing != null && Boolean.TRUE.equals(existing.anonymousAccessAllowed()) : request.getAnonymousAccessAllowed();
        String status = "DISABLED".equalsIgnoreCase(request.getStatus()) ? "DISABLED" : "ENABLED";
        if (maxFileSizeMb == null || maxFileSizeMb < 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "文件大小限制最小为 1MB");
        }
        return new StoragePayload(title, storageKey, provider, rootPath, bucketName, endpoint, region, accessKeyId, accessKeySecret, renameStrategy, maxFileSizeMb, allowedMimeTypes, defaultStorage, retain, anonymousAccessAllowed, status);
    }

    private String normalizeProvider(String provider) {
        String normalized = defaultIfBlank(provider, "LOCAL").toUpperCase(Locale.ROOT);
        if (!Set.of("LOCAL", "ALIYUN_OSS", "TENCENT_COS").contains(normalized)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "不支持的存储类型");
        }
        return normalized;
    }

    private String normalizeRenameStrategy(String value) {
        String normalized = defaultIfBlank(value, "APPEND_RANDOM_ID").toUpperCase(Locale.ROOT);
        if (!Set.of("APPEND_RANDOM_ID", "RANDOM_STRING", "KEEP_ORIGINAL").contains(normalized)) {
            return "APPEND_RANDOM_ID";
        }
        return normalized;
    }

    private String normalizeStorageKey(String value) {
        String normalized = defaultIfBlank(value, "local").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (!normalized.matches("^[a-z][a-z0-9_]*$")) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间标识必须以英文字母开头，且只包含英文、数字和下划线");
        }
        return normalized;
    }

    private String providerLabel(String provider) {
        return switch (provider) {
            case "ALIYUN_OSS" -> "阿里云 OSS";
            case "TENCENT_COS" -> "腾讯云 COS";
            default -> "Local storage";
        };
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String encryptSecret(String secret) {
        return fieldCryptoService.encrypt(secret);
    }

    private String decryptSecret(String secret) {
        return fieldCryptoService.decrypt(secret);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String readableSize(Long bytes) {
        long value = bytes == null ? 0L : bytes;
        if (value >= 1024L * 1024L) {
            return (value / (1024L * 1024L)) + "MB";
        }
        if (value >= 1024L) {
            return (value / 1024L) + "KB";
        }
        return value + "B";
    }

    private FileObjectDTO mapFileObject(FileObjectEntity entity) {
        return new FileObjectDTO(
                entity.getId(),
                entity.getUploadedBy(),
                entity.getUploadedByUuid(),
                entity.getUploadedByName(),
                entity.getOriginalFilename(),
                entity.getObjectKey(),
                entity.getStorageType(),
                entity.getBucket(),
                entity.getFileExtension(),
                entity.getContentType(),
                entity.getFileSize(),
                null,
                entity.getObjectKey(),
                entity.getPublicUrl(),
                entity.getPublicUrl(),
                entity.getPublicUrl(),
                entity.getPreviewMode(),
                entity.getPreviewableFlag() != null && entity.getPreviewableFlag() == 1,
                entity.getCategory(),
                entity.getTags(),
                entity.getRemark(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private FileStorageSpaceEntity buildStorageSpaceEntity(StoragePayload payload, Long operatorId, String operatorUuid) {
        LocalDateTime now = LocalDateTime.now();
        FileStorageSpaceEntity entity = new FileStorageSpaceEntity();
        entity.setTitle(payload.title());
        entity.setStorageKey(payload.storageKey());
        entity.setProvider(payload.provider());
        entity.setRootPath(payload.rootPath());
        entity.setBucketName(payload.bucketName());
        entity.setEndpoint(payload.endpoint());
        entity.setRegion(payload.region());
        entity.setAccessKeyId(payload.accessKeyId());
        entity.setAccessKeySecret(encryptSecret(payload.accessKeySecret()));
        entity.setRenameStrategy(payload.renameStrategy());
        entity.setMaxFileSizeMb(payload.maxFileSizeMb());
        entity.setAllowedMimeTypes(payload.allowedMimeTypes());
        entity.setDefaultFlag(payload.defaultStorage() ? 1 : 0);
        entity.setRetainFileOnRecordDelete(payload.retainFileOnRecordDelete() ? 1 : 0);
        entity.setAnonymousAccessAllowed(payload.anonymousAccessAllowed() ? 1 : 0);
        entity.setStatus(payload.status());
        entity.setCreatedBy(operatorId);
        entity.setCreatedByUuid(operatorUuid);
        entity.setCreatedAt(now);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedByUuid(operatorUuid);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        return entity;
    }

    private StorageSpaceDTO mapStorageSpace(FileStorageSpaceEntity entity) {
        Long totalSizeBytes = entity.getTotalSizeBytes() == null ? 0L : entity.getTotalSizeBytes();
        Long fileCount = entity.getFileCount() == null ? 0L : entity.getFileCount();
        return new StorageSpaceDTO(
                entity.getId(),
                entity.getTitle(),
                entity.getStorageKey(),
                entity.getProvider(),
                entity.getRootPath(),
                entity.getBucketName(),
                entity.getEndpoint(),
                entity.getRegion(),
                entity.getAccessKeyId(),
                StringUtils.hasText(entity.getAccessKeySecret()),
                entity.getRenameStrategy(),
                entity.getMaxFileSizeMb(),
                entity.getAllowedMimeTypes(),
                entity.getDefaultFlag() != null && entity.getDefaultFlag() == 1,
                entity.getRetainFileOnRecordDelete() != null && entity.getRetainFileOnRecordDelete() == 1,
                entity.getAnonymousAccessAllowed() != null && entity.getAnonymousAccessAllowed() == 1,
                entity.getStatus(),
                fileCount,
                totalSizeBytes,
                readableSize(totalSizeBytes),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private record StoragePayload(
            String title,
            String storageKey,
            String provider,
            String rootPath,
            String bucketName,
            String endpoint,
            String region,
            String accessKeyId,
            String accessKeySecret,
            String renameStrategy,
            Integer maxFileSizeMb,
            String allowedMimeTypes,
            boolean defaultStorage,
            boolean retainFileOnRecordDelete,
            boolean anonymousAccessAllowed,
            String status
    ) {
    }

    private record DefaultStorageSpace(
            String title,
            String storageKey,
            String rootPath,
            String bucketName,
            boolean defaultStorage,
            Integer maxFileSizeMb,
            boolean anonymousAccessAllowed
    ) {
    }

    private record StorageSpaceUploadContext(
            StorageSpaceDTO storageSpace,
            String storageBucket,
            Path storageRoot,
            String publicPath,
            long maxFileSizeBytes
    ) {
    }

    private record TrustedCurrentUser(
            Long userId,
            String userUuid,
            String username,
            String permissionsVersion,
            Set<String> permissions,
            Long primaryDeptId,
            Set<Long> deptIds,
            Set<Long> descendantDeptIds,
            List<DataPermissionRule> dataScopes
    ) {
    }

    private record CachedFilePage(PageResponse<FileObjectDTO> page, Instant expireAt) {
    }

}

