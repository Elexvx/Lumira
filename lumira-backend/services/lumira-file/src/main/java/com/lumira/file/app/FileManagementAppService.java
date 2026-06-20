package com.lumira.file.app;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.domain.event.DomainEventPublisher;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.FieldCryptoService;
import com.lumira.common.security.data.DataPermissionDecision;
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
import com.lumira.file.processing.FileProcessingTaskService;
import com.lumira.file.security.SafeUrlValidator;
import com.lumira.file.vo.FileVO;
import com.lumira.file.upload.DocumentUploadService;
import com.lumira.file.upload.FileStorageMetrics;
import com.lumira.file.upload.ImageUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileManagementAppService {

    private static final Logger log = LoggerFactory.getLogger(FileManagementAppService.class);
    private static final String STORAGE_TEST_PUBLIC_ERROR = "存储空间不可访问或配置不正确";

    private static final long MAX_PAGE_SIZE = 100L;
    private static final long FILE_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final long STORAGE_SPACE_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final Duration FILE_LIST_CACHE_TTL = Duration.ofSeconds(30);
    private static final String RESOURCE_FILE_OBJECT = "file:object";
    private static final String DEFAULT_SORT_COLUMN = "created_at";
    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_TENANT = "tenant";
    public static final String SCOPE_DOWNLOAD_CENTER = "download-center";
    private static final String STORAGE_KEY_DOWNLOAD_CENTER = "download_center";
    private static final String VISIBILITY_SCOPE_PERSONAL = "PERSONAL";
    private static final String VISIBILITY_SCOPE_DOWNLOAD_CENTER = "DOWNLOAD_CENTER";
    private static final String VISIBILITY_SCOPE_PUBLIC = "PUBLIC";
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
    private final FileProcessingTaskService fileProcessingTaskService;
    private final FieldCryptoService fieldCryptoService;
    private final FileStorageMetrics storageMetrics;
    private final SafeUrlValidator safeUrlValidator;
    private final SecurityAuditEventService securityAuditEventService;
    private final Map<String, CachedFilePage> localFileListCache = new ConcurrentHashMap<>();

    public FileManagementAppService(
            FileObjectMapper fileObjectMapper,
            FileStorageSpaceMapper fileStorageSpaceMapper,
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService,
            @Qualifier("fileDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            FileProcessingTaskService fileProcessingTaskService,
            FieldCryptoService fieldCryptoService,
            FileStorageMetrics storageMetrics,
            SafeUrlValidator safeUrlValidator
    ) {
        this(fileObjectMapper, fileStorageSpaceMapper, jdbcTemplate, uploadProperties, documentUploadService,
                imageUploadService, domainEventPublisher, fileProcessingTaskService, fieldCryptoService,
                storageMetrics, safeUrlValidator, null);
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
            FileProcessingTaskService fileProcessingTaskService,
            FieldCryptoService fieldCryptoService,
            FileStorageMetrics storageMetrics,
            SafeUrlValidator safeUrlValidator,
            SecurityAuditEventService securityAuditEventService
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.fileStorageSpaceMapper = fileStorageSpaceMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.documentUploadService = documentUploadService;
        this.imageUploadService = imageUploadService;
        this.domainEventPublisher = domainEventPublisher;
        this.fileProcessingTaskService = fileProcessingTaskService;
        this.fieldCryptoService = fieldCryptoService;
        this.storageMetrics = storageMetrics;
        this.safeUrlValidator = safeUrlValidator;
        this.securityAuditEventService = securityAuditEventService;
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
        Long tenantId = currentTenantId(currentUser);
        boolean tenantScope = isTenantWideScope(scope);
        boolean downloadCenterScope = SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, currentUser, tenantScope, downloadCenterScope);
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
        String localCacheKey = localCacheable ? buildFileListCacheKey(tenantId, currentUser, safePageNo, safePageSize, sortColumn, ascending) : null;
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
            Long tenantId,
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            String sortColumn,
            boolean ascending
    ) {
        Long userId = currentUser == null ? null : currentUser.getUserId();
        String permissionVersion = currentUser == null ? null : currentUser.getPermissionsVersion();
        return String.join(":",
                "file:list",
                String.valueOf(tenantId),
                String.valueOf(userId),
                StringUtils.hasText(permissionVersion) ? permissionVersion : "v0",
                String.valueOf(pageNo),
                String.valueOf(pageSize),
                sortColumn,
                ascending ? "asc" : "desc");
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        return getFile(currentUser, fileId, tenantScope, false);
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean tenantScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope, downloadCenterScope);
        return enrich(file);
    }

    public FileObjectDTO getPreviewableFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        return getPreviewableFile(currentUser, fileId, tenantScope, false);
    }

    public FileObjectDTO getPreviewableFile(CurrentUser currentUser, Long fileId, boolean tenantScope, boolean downloadCenterScope) {
        FileObjectDTO file = getFile(currentUser, fileId, tenantScope, downloadCenterScope);
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
            boolean tenantScope,
            int limit
    ) {
        Long tenantId = currentTenantId(currentUser);
        long safeLimit = Math.max(1L, Math.min(limit, MAX_PAGE_SIZE));
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, currentUser, tenantScope, false);
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
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceUploadContext storageContext = resolveUploadContext(tenantId, bucket);
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
                tenantId,
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
        FileObjectDTO uploaded = getInsertedFile(tenantId, insertedId);
        localFileListCache.clear();
        publishFileUploaded(uploaded);
        fileProcessingTaskService.requestTasksForUpload(uploaded, currentUser.getUserId());
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

    private FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file, String category, String remark, String bucket, String visibilityScope) {
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceUploadContext storageContext = resolveUploadContext(tenantId, bucket);
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
                tenantId,
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
        FileObjectDTO uploaded = getInsertedFile(tenantId, insertedId);
        localFileListCache.clear();
        publishFileUploaded(uploaded);
        fileProcessingTaskService.requestTasksForUpload(uploaded, currentUser.getUserId());
        return uploaded;
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        deleteFile(currentUser, fileId, tenantScope, false);
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean tenantScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope, downloadCenterScope);
        if (!shouldRetainStoredFile(currentTenantId(currentUser), file.bucket())) {
            deleteStoredFile(file);
        }
        fileObjectMapper.update(
                null,
                new LambdaUpdateWrapper<FileObjectEntity>()
                        .set(FileObjectEntity::getDeleted, 1)
                        .set(FileObjectEntity::getUpdatedBy, currentUser.getUserId())
                        .set(FileObjectEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileObjectEntity::getId, fileId)
                        .eq(FileObjectEntity::getTenantId, currentTenantId(currentUser))
                        .eq(FileObjectEntity::getDeleted, 0)
        );
        localFileListCache.clear();
        publishFileDeleted(file);
    }

    private void publishFileUploaded(FileObjectDTO file) {
        if (file == null) {
            return;
        }
        FileObjectAggregate aggregate = new FileObjectAggregate(file.id(), file.tenantId(), safeFileSize(file.fileSizeBytes()));
        aggregate.recordUploaded(file.mimeType());
        domainEventPublisher.publishAll(aggregate.pullDomainEvents());
    }

    private void publishFileDeleted(FileObjectDTO file) {
        if (file == null) {
            return;
        }
        FileObjectAggregate aggregate = new FileObjectAggregate(file.id(), file.tenantId(), safeFileSize(file.fileSizeBytes()));
        aggregate.delete();
        domainEventPublisher.publishAll(aggregate.pullDomainEvents());
    }

    private long safeFileSize(Long fileSizeBytes) {
        return fileSizeBytes == null ? 0L : Math.max(0L, fileSizeBytes);
    }

    public PageResponse<StorageSpaceDTO> listStorageSpaces(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safeOffset = (safePageNo - 1L) * safePageSize;
        long totalLimit = calculateStorageSpaceListTotalCountLimit(safePageSize, safeOffset);
        QueryWrapper<FileStorageSpaceEntity> countQueryWrapper = new QueryWrapper<FileStorageSpaceEntity>()
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        Long total = countStorageSpaceCandidates(countQueryWrapper.clone(), totalLimit);
        long normalizedTotal = normalizeTotal(total, totalLimit);
        boolean totalCapped = isTotalCapped(total, totalLimit);
        List<StorageSpaceDTO> records = fileStorageSpaceMapper
                .listWithUsage(tenantId, safePageSize, safeOffset)
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
        return queryStorageSpace(currentTenantId(currentUser), normalizeStorageKey(storageKey));
    }

    public FileStorageSpaceRequest.TestResult testStorageSpace(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByIdWithUsage(tenantId, id);
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
            log.warn("Storage space test failed tenantId={} storageSpaceId={} provider={} reason={}",
                    tenantId, id, entity.getProvider(), ex.getMessage(), ex);
            recordStorageSpaceTestAudit(currentUser, tenantId, id, entity.getProvider(), ex);
            result.setStatus("DOWN");
            result.setMessage(STORAGE_TEST_PUBLIC_ERROR);
            return result;
        } finally {
            result.setResponseTimeMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }
    }

    @Transactional
    public StorageSpaceDTO createStorageSpace(CurrentUser currentUser, FileStorageSpaceRequest request) {
        Long tenantId = currentTenantId(currentUser);
        String provider = normalizeProvider(request.getProvider());
        String storageKey = normalizeStorageKey(StringUtils.hasText(request.getStorageKey()) ? request.getStorageKey() : provider.toLowerCase(Locale.ROOT) + "_" + shortId());
        StoragePayload payload = normalizeStoragePayload(request, provider, storageKey, null);
        if (payload.defaultStorage()) {
            clearDefaultStorage(tenantId);
        }
        try {
            fileStorageSpaceMapper.insert(buildStorageSpaceEntity(tenantId, payload, currentUser.getUserId()));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间标识已存在");
        }
        ensureOneDefaultStorage(tenantId);
        return queryStorageSpace(tenantId, storageKey);
    }

    @Transactional
    public StorageSpaceDTO updateStorageSpace(CurrentUser currentUser, Long id, FileStorageSpaceRequest request) {
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceDTO existing = queryStorageSpaceById(tenantId, id);
        StoragePayload payload = normalizeStoragePayload(request, existing.provider(), existing.storageKey(), existing);
        if (payload.defaultStorage()) {
            clearDefaultStorage(tenantId);
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
                        .set(FileStorageSpaceEntity::getUpdatedBy, currentUser.getUserId())
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getId, id)
                        .eq(FileStorageSpaceEntity::getTenantId, tenantId)
                        .eq(FileStorageSpaceEntity::getDeleted, 0)
        );
        ensureOneDefaultStorage(tenantId);
        return queryStorageSpaceById(tenantId, id);
    }

    @Transactional
    public void deleteStorageSpace(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceDTO existing = queryStorageSpaceById(tenantId, id);
        if (hasFileRecordsInBucket(tenantId, existing.storageKey())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间下仍有文件，不能删除");
        }
        if (Boolean.TRUE.equals(existing.defaultStorage())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "默认存储空间不能删除");
        }
        fileStorageSpaceMapper.update(
                null,
                new LambdaUpdateWrapper<FileStorageSpaceEntity>()
                        .set(FileStorageSpaceEntity::getDeleted, 1)
                        .set(FileStorageSpaceEntity::getUpdatedBy, currentUser.getUserId())
                        .set(FileStorageSpaceEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(FileStorageSpaceEntity::getId, id)
                        .eq(FileStorageSpaceEntity::getTenantId, tenantId)
                        .eq(FileStorageSpaceEntity::getDeleted, 0)
        );
    }

    private boolean hasFileRecordsInBucket(Long tenantId, String storageKey) {
        if (tenantId == null || !StringUtils.hasText(storageKey)) {
            return false;
        }
        QueryWrapper<FileObjectEntity> query = new QueryWrapper<FileObjectEntity>()
                .select("1")
                .eq("tenant_id", tenantId)
                .eq("bucket", storageKey)
                .eq("deleted", 0)
                .last("limit 1");
        return fileObjectMapper.selectOne(query) != null;
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        return resolveFilePath(currentUser, fileId, tenantScope, false);
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean tenantScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope, downloadCenterScope);
        Path target = resolveFilePath(file);
        if (target == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件路径无效");
        }
        return target;
    }

    public FileContentDTO readFileContent(CurrentUser currentUser, Long fileId, boolean tenantScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope, downloadCenterScope);
        Path target = resolveFilePath(file);
        if (target == null || !Files.exists(target) || !Files.isRegularFile(target)) {
            storageMetrics.recordMissing("read", file.storageType(), Duration.ZERO);
            throw new BizException(ErrorCode.NOT_FOUND, "文件内容不存在");
        }
        Instant readStartedAt = Instant.now();
        try {
            FileContentDTO content = new FileContentDTO(
                    file.id(),
                    file.tenantId(),
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
            boolean tenantScope,
            boolean downloadCenterScope
    ) {
        if (!StringUtils.hasText(artifactType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件处理产物类型不能为空");
        }
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope, downloadCenterScope);
        List<FileProcessingArtifactDTO> artifacts = jdbcTemplate.query(
                """
                        select id, tenant_id, file_id, task_type, artifact_type, artifact_path,
                               content_text, content_length, updated_at
                        from file_processing_artifact
                        where tenant_id = ? and file_id = ? and artifact_type = ? and deleted = 0
                        order by updated_at desc, id desc
                        limit 1
                        """,
                (rs, rowNum) -> new FileProcessingArtifactDTO(
                        rs.getLong("id"),
                        rs.getLong("tenant_id"),
                        rs.getLong("file_id"),
                        rs.getString("task_type"),
                        rs.getString("artifact_type"),
                        rs.getString("artifact_path"),
                        rs.getString("content_text"),
                        rs.getInt("content_length"),
                        rs.getObject("updated_at", LocalDateTime.class)
                ),
                file.tenantId(),
                file.id(),
                artifactType.trim().toUpperCase(Locale.ROOT)
        );
        if (artifacts.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件处理产物不存在");
        }
        return artifacts.getFirst();
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        return queryFile(currentUser, fileId, tenantScope, false);
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean tenantScope, boolean downloadCenterScope) {
        Long tenantId = currentTenantId(currentUser);
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("id", fileId)
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, currentUser, tenantScope, downloadCenterScope);
        FileObjectEntity entity = fileObjectMapper.selectOne(queryWrapper);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return mapFileObject(entity);
    }

    private FileObjectDTO enrich(FileObjectDTO file) {
        return new FileObjectDTO(
                file.id(),
                file.tenantId(),
                file.uploadedBy(),
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

    private void recordStorageSpaceTestAudit(CurrentUser currentUser, Long tenantId, Long storageSpaceId, String provider, Exception ex) {
        if (securityAuditEventService == null) {
            return;
        }
        securityAuditEventService.record(SecurityAuditEvent.builder("STORAGE_SPACE_TEST_FAILED", "WARN", "DENIED")
                .tenantId(tenantId)
                .userId(currentUser == null ? null : currentUser.getUserId())
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

    private Path resolveFilePath(FileObjectDTO file) {
        if (file == null || !StringUtils.hasText(file.storagePath())) {
            return null;
        }
        if (StringUtils.hasText(file.bucket())) {
            FileStorageSpaceEntity storageSpace = fileStorageSpaceMapper.findByStorageKey(file.tenantId(), file.bucket());
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
            Long tenantId,
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
        LocalDateTime now = LocalDateTime.now();
        FileObjectEntity entity = new FileObjectEntity();
        entity.setTenantId(tenantId);
        entity.setStorageType(storageType);
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setUploadedBy(currentUser.getUserId());
        entity.setUploadedByName(currentUser.getUsername());
        entity.setDepartmentId(currentUser.getPrimaryDeptId());
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
        entity.setCreatedBy(currentUser.getUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(currentUser.getUserId());
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        fileObjectMapper.insert(entity);
        if (entity.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录保存失败");
        }
        return entity.getId();
    }

    private FileObjectDTO getInsertedFile(Long tenantId, Long insertedId) {
        FileObjectEntity inserted = fileObjectMapper.selectById(insertedId);
        if (inserted == null || !tenantId.equals(inserted.getTenantId())) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录读取失败");
        }
        return enrich(mapFileObject(inserted));
    }

    private void applyFileDataPermission(QueryWrapper<FileObjectEntity> queryWrapper, CurrentUser currentUser, boolean tenantScopeRequested, boolean downloadCenterScope) {
        applyFileVisibilityScope(queryWrapper, downloadCenterScope);
        if (!tenantScopeRequested) {
            queryWrapper.eq("uploaded_by", currentUser.getUserId());
            return;
        }
        if (downloadCenterScope) {
            return;
        }
        DataPermissionDecision decision = DataPermissionResolver.resolve(
                RESOURCE_FILE_OBJECT,
                currentUser.getUserId(),
                currentUser.getDeptIds(),
                currentUser.getDescendantDeptIds(),
                currentUser.getDataScopes(),
                currentUser.getPermissions()
        );
        if (decision.scopeType() == DataScopeType.ALL || decision.scopeType() == DataScopeType.TENANT) {
            return;
        }
        Set<Long> deptIds = new java.util.LinkedHashSet<>(decision.deptIds());
        Set<Long> userIds = new java.util.LinkedHashSet<>(decision.userIds());
        if (decision.hasDeptRestriction() && currentUser.getUserId() != null) {
            userIds.add(currentUser.getUserId());
        }
        if (deptIds.isEmpty() && userIds.isEmpty()) {
            queryWrapper.eq("uploaded_by", currentUser.getUserId());
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

    private void applyFileVisibilityScope(QueryWrapper<FileObjectEntity> queryWrapper, boolean downloadCenterScope) {
        if (downloadCenterScope) {
            queryWrapper
                    .eq("visibility_scope", VISIBILITY_SCOPE_DOWNLOAD_CENTER)
                    .eq("bucket", STORAGE_KEY_DOWNLOAD_CENTER);
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

    private boolean isTenantWideScope(String scope) {
        return SCOPE_TENANT.equalsIgnoreCase(scope) || SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
    }

    private StorageSpaceDTO getDefaultStorageSpace(Long tenantId) {
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findDefault(tenantId);
        if (entity != null) {
            return mapStorageSpace(entity);
        }
        return new StorageSpaceDTO(null, tenantId, "Local storage", "local", "LOCAL", "storage/uploads/", null, null, null, null, false, "APPEND_RANDOM_ID", 20, "*", true, false, false, "ENABLED", 0L, 0L, "0B", null, null);
    }

    private StorageSpaceDTO queryStorageSpace(Long tenantId, String storageKey) {
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByStorageKey(tenantId, storageKey);
        if (entity == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        return mapStorageSpace(entity);
    }

    private StorageSpaceDTO queryStorageSpaceById(Long tenantId, Long id) {
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByIdWithUsage(tenantId, id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        return mapStorageSpace(entity);
    }

    private boolean shouldRetainStoredFile(Long tenantId, String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return false;
        }
        return Boolean.TRUE.equals(fileStorageSpaceMapper.shouldRetainStoredFile(tenantId, bucket));
    }

    private StorageSpaceUploadContext resolveUploadContext(Long tenantId, String bucket) {
        String normalizedBucket = StringUtils.hasText(bucket) ? normalizeStorageKey(bucket) : null;
        StorageSpaceDTO storageSpace = normalizedBucket != null
                ? queryStorageSpace(tenantId, normalizedBucket)
                : getDefaultStorageSpace(tenantId);
        if (!"LOCAL".equalsIgnoreCase(storageSpace.provider())) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "当前仅支持本地存储空间上传");
        }
        if (!"ENABLED".equalsIgnoreCase(storageSpace.status())) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "存储空间已禁用，无法上传文件");
        }
        Path storageRoot = resolveStorageRoot(storageSpace);
        String publicPath = resolvePublicPath(storageRoot);
        return new StorageSpaceUploadContext(storageSpace, normalizedBucket != null ? normalizedBucket : storageSpace.storageKey(), storageRoot, publicPath, maxFileSizeBytes(storageSpace.maxFileSizeMb()));
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

    private void clearDefaultStorage(Long tenantId) {
        fileStorageSpaceMapper.clearDefaultStorage(tenantId);
    }

    private void ensureOneDefaultStorage(Long tenantId) {
        Long count = fileStorageSpaceMapper.countDefaultStorage(tenantId);
        if (count != null && count > 0) {
            return;
        }
        fileStorageSpaceMapper.ensureFirstDefaultStorage(tenantId);
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

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser != null && currentUser.getCurrentTenantId() != null) {
            return currentUser.getCurrentTenantId();
        }
        throw visibleBizException(ErrorCode.FORBIDDEN, "Tenant context is required");
    }

    private BizException visibleBizException(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message, message);
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
            accessKeySecret = decryptSecret(fileStorageSpaceMapper.findAccessKeySecret(existing.tenantId(), existing.id()));
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
                entity.getTenantId(),
                entity.getUploadedBy(),
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

    private FileStorageSpaceEntity buildStorageSpaceEntity(Long tenantId, StoragePayload payload, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        FileStorageSpaceEntity entity = new FileStorageSpaceEntity();
        entity.setTenantId(tenantId);
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
        entity.setCreatedAt(now);
        entity.setUpdatedBy(operatorId);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        return entity;
    }

    private StorageSpaceDTO mapStorageSpace(FileStorageSpaceEntity entity) {
        Long totalSizeBytes = entity.getTotalSizeBytes() == null ? 0L : entity.getTotalSizeBytes();
        Long fileCount = entity.getFileCount() == null ? 0L : entity.getFileCount();
        return new StorageSpaceDTO(
                entity.getId(),
                entity.getTenantId(),
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

    private record StorageSpaceUploadContext(
            StorageSpaceDTO storageSpace,
            String storageBucket,
            Path storageRoot,
            String publicPath,
            long maxFileSizeBytes
    ) {
    }

    private record CachedFilePage(PageResponse<FileObjectDTO> page, Instant expireAt) {
    }

}

