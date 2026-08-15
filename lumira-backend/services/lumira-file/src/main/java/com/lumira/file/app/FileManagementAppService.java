package com.lumira.file.app;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.file.CompetitionStorageSpace;
import com.lumira.api.file.CompetitionStorageSpaceRequest;
import com.lumira.api.file.FileContentDTO;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.api.file.FileProcessingArtifactDTO;
import com.lumira.api.file.StorageSpaceDTO;
import com.lumira.api.file.StorageSpaceOptionDTO;
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
import com.lumira.file.domain.FileObjectSecurityStatus;
import com.lumira.file.entity.FileStorageSpaceEntity;
import com.lumira.file.processing.FileProcessingTaskRequestService;
import com.lumira.file.processing.FileSecurityScanProcessor;
import com.lumira.file.repository.FileProcessingArtifactRepository;
import com.lumira.file.repository.FileBusinessPolicyRepository;
import com.lumira.file.repository.FileObjectRepository;
import com.lumira.file.repository.FileStorageSpaceRepository;
import com.lumira.file.security.SafeUrlValidator;
import com.lumira.file.vo.FileVO;
import com.lumira.file.upload.DocumentUploadService;
import com.lumira.file.upload.FileStorageMetrics;
import com.lumira.file.upload.FileUploadDirectory;
import com.lumira.file.upload.ImageUploadService;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
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

@Lazy
@Service
public class FileManagementAppService {

    private static final Logger log = LoggerFactory.getLogger(FileManagementAppService.class);
    private static final String STORAGE_TEST_PUBLIC_ERROR = "存储空间不可访问或配置不正确";

    private static final long MAX_PAGE_SIZE = 100L;
    private static final long FILE_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final long STORAGE_SPACE_LIST_TOTAL_COUNT_CAP = 1000L;
    private static final long MAX_IN_MEMORY_FILE_CONTENT_BYTES = 10L * 1024L * 1024L;
    private static final int COMPETITION_STORAGE_MAX_FILE_SIZE_MB = 1024;
    private static final String RESOURCE_FILE_OBJECT = "file:object";
    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_SHARED = "shared";
    public static final String SCOPE_DOWNLOAD_CENTER = "download-center";
    private static final String STORAGE_KEY_LOCAL = "local";
    private static final String STORAGE_KEY_DOWNLOAD_CENTER = "download_center";
    private static final String VISIBILITY_SCOPE_PERSONAL = "PERSONAL";
    private static final String VISIBILITY_SCOPE_DOWNLOAD_CENTER = "DOWNLOAD_CENTER";
    private static final String VISIBILITY_SCOPE_PUBLIC = "PUBLIC";
    private static final String DICT_STORAGE_PROVIDER = "file_storage_provider";
    private static final String DICT_RENAME_STRATEGY = "file_rename_strategy";
    private static final String DICT_STORAGE_STATUS = "file_storage_status";
    private static final String DICT_PREVIEW_EXTENSION = "file_preview_extension";
    private static final String DICT_PREVIEW_CONTENT_TYPE = "file_preview_content_type";
    private static final String DICT_RUNTIME_DEFAULT = "file_runtime_default";
    private final FileObjectRepository fileObjectRepository;
    private final FileBusinessPolicyRepository businessPolicyRepository;
    private final FileStorageSpaceRepository storageSpaceRepository;
    private final FileProcessingArtifactRepository artifactRepository;
    private final UploadProperties uploadProperties;
    private final DocumentUploadService documentUploadService;
    private final ImageUploadService imageUploadService;
    private final DomainEventPublisher domainEventPublisher;
    private final FileProcessingTaskRequestService fileProcessingTaskRequestService;
    private final ObjectProvider<FileSecurityScanProcessor> securityScanProcessorProvider;
    private final FieldCryptoService fieldCryptoService;
    private final FileStorageMetrics storageMetrics;
    private final SafeUrlValidator safeUrlValidator;
    private final SecurityAuditEventService securityAuditEventService;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;

    public FileManagementAppService(
            FileObjectRepository fileObjectRepository,
            FileBusinessPolicyRepository businessPolicyRepository,
            FileStorageSpaceRepository storageSpaceRepository,
            FileProcessingArtifactRepository artifactRepository,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService,
            @Qualifier("fileDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            FileProcessingTaskRequestService fileProcessingTaskRequestService,
            FieldCryptoService fieldCryptoService,
            FileStorageMetrics storageMetrics,
            SafeUrlValidator safeUrlValidator
    ) {
        this(fileObjectRepository, businessPolicyRepository, storageSpaceRepository, artifactRepository, uploadProperties, documentUploadService,
                imageUploadService, domainEventPublisher, fileProcessingTaskRequestService, fieldCryptoService,
                storageMetrics, safeUrlValidator, null, null, null);
    }

    public FileManagementAppService(
            FileObjectRepository fileObjectRepository,
            FileBusinessPolicyRepository businessPolicyRepository,
            FileStorageSpaceRepository storageSpaceRepository,
            FileProcessingArtifactRepository artifactRepository,
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
        this(fileObjectRepository, businessPolicyRepository, storageSpaceRepository, artifactRepository, uploadProperties, documentUploadService,
                imageUploadService, domainEventPublisher, fileProcessingTaskRequestService, fieldCryptoService,
                storageMetrics, safeUrlValidator, securityAuditEventService, systemInternalApiProvider, null);
    }

    @Autowired
    public FileManagementAppService(
            FileObjectRepository fileObjectRepository,
            FileBusinessPolicyRepository businessPolicyRepository,
            FileStorageSpaceRepository storageSpaceRepository,
            FileProcessingArtifactRepository artifactRepository,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService,
            @Qualifier("fileDomainEventPublisher") DomainEventPublisher domainEventPublisher,
            FileProcessingTaskRequestService fileProcessingTaskRequestService,
            FieldCryptoService fieldCryptoService,
            FileStorageMetrics storageMetrics,
            SafeUrlValidator safeUrlValidator,
            SecurityAuditEventService securityAuditEventService,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            ObjectProvider<FileSecurityScanProcessor> securityScanProcessorProvider
    ) {
        this.fileObjectRepository = fileObjectRepository;
        this.businessPolicyRepository = businessPolicyRepository;
        this.storageSpaceRepository = storageSpaceRepository;
        this.artifactRepository = artifactRepository;
        this.uploadProperties = uploadProperties;
        this.documentUploadService = documentUploadService;
        this.imageUploadService = imageUploadService;
        this.domainEventPublisher = domainEventPublisher;
        this.fileProcessingTaskRequestService = fileProcessingTaskRequestService;
        this.securityScanProcessorProvider = securityScanProcessorProvider;
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
        boolean sharedScope = isSharedScope(scope);
        boolean downloadCenterScope = SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(scope);
        TrustedCurrentUser actor = requirePermission(currentUser, resolveReadPermission(sharedScope, downloadCenterScope));
        boolean ascending = "ascend".equalsIgnoreCase(sortOrder);
        FileObjectRepository.Query query = new FileObjectRepository.Query(
                trimToNull(keyword), true, trimToNull(category), normalizeLower(fileExtension),
                normalizeUpper(previewMode), StringUtils.hasText(bucket) ? normalizeStorageKey(bucket) : null,
                null, null, sortField, ascending);
        FileObjectRepository.Access access = resolveFileAccess(actor, sharedScope, downloadCenterScope);
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safeOffset = (safePageNo - 1L) * safePageSize;
        long totalLimit = calculateFileListTotalCountLimit();
        Long total = fileObjectRepository.countCandidates(query, access, totalLimit);
        long normalizedTotal = normalizeTotal(total, FILE_LIST_TOTAL_COUNT_CAP);
        boolean totalCapped = isTotalCapped(total, FILE_LIST_TOTAL_COUNT_CAP);
        List<FileObjectDTO> records = fileObjectRepository.search(query, access, safeOffset, safePageSize)
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
        return response;
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return getFile(currentUser, fileId, sharedScope, false);
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(
                requirePermission(currentUser, resolveReadPermission(sharedScope, downloadCenterScope)),
                fileId,
                sharedScope,
                downloadCenterScope
        );
        return enrich(file);
    }

    public FileObjectDTO getPreviewableFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return getPreviewableFile(currentUser, fileId, sharedScope, false);
    }

    public FileObjectDTO getPreviewableFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = getFile(currentUser, fileId, sharedScope, downloadCenterScope);
        file = ensureDownloadCenterContentReady(currentUser, file, sharedScope, downloadCenterScope);
        requireContentAccessible(file);
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
        TrustedCurrentUser actor = requirePermission(currentUser, resolveReadPermission(sharedScope, false));
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Invalid internal file search limit");
        }
        long safeLimit = limit;
        String normalizedContentType = trimToNull(contentType);
        if (normalizedContentType != null && normalizedContentType.endsWith("%")) {
            normalizedContentType = normalizedContentType.substring(0, normalizedContentType.length() - 1);
        }
        FileObjectRepository.Query query = new FileObjectRepository.Query(trimToNull(keyword), false, null, null,
                null, null, normalizedContentType, normalizeUpper(status), "id", false);
        return fileObjectRepository.search(query, resolveFileAccess(actor, sharedScope, false), 0L, safeLimit)
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
        return uploadFile(currentUser, file, category, tags, remark, bucket, scope, null);
    }

    @Transactional
    public FileObjectDTO uploadFile(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            String scope,
            String directory
    ) {
        if (file == null || file.isEmpty()) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "请先选择上传文件");
        }
        String visibilityScope = resolveVisibilityScope(scope);
        requirePermission(currentUser, resolveUploadPermission(visibilityScope));
        String uploadBucket = resolveUploadBucket(bucket, scope);
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (ImageUploadService.supports(originalFilename, contentType)) {
            return uploadImage(currentUser, file, category, tags, remark, uploadBucket, visibilityScope, directory);
        }
        if (DocumentUploadService.supports(originalFilename, contentType)) {
            return uploadDocument(currentUser, file, category, tags, remark, uploadBucket, visibilityScope, directory);
        }
        throw visibleBizException(ErrorCode.BAD_REQUEST, "仅允许上传图片、PDF、Word、Excel、PPT、Markdown、TXT 或压缩包文件");
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
        return uploadDocument(currentUser, file, category, tags, remark, bucket, VISIBILITY_SCOPE_PERSONAL, null);
    }

    private FileObjectDTO uploadDocument(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            String visibilityScope,
            String directory
    ) {
        requireCurrentUser(currentUser);
        Long actorUserId = trustedUserId(currentUser);
        String actorUserUuid = trustedUserUuid(currentUser);
        StorageSpaceUploadContext storageContext = resolveUploadContext(bucket);
        FileUploadDirectory.Scope uploadDirectory = FileUploadDirectory.resolve(
                storageContext.storageRoot(),
                storageContext.publicPath(),
                directory
        );
        DocumentUploadService.StoredDocument storedDocument = documentUploadService.upload(
                file,
                uploadDirectory.storageRoot(),
                uploadDirectory.publicPath(),
                storageContext.maxFileSizeBytes(),
                storageContext.storageSpace().renameStrategy(),
                storageContext.storageSpace().allowedMimeTypes()
        );
        Long insertedId = insertFileObject(
                currentUser,
                storageContext.storageSpace().provider(),
                storageContext.storageBucket(),
                FileUploadDirectory.qualifyObjectKey(uploadDirectory.directory(), storedDocument.relativePath()),
                storedDocument.originalFileName(),
                storedDocument.fileExtension(),
                storedDocument.contentType(),
                storedDocument.fileSizeBytes(),
                storedDocument.publicUrl(),
                storedDocument.previewMode(),
                storedDocument.previewable(),
                visibilityScope,
                StringUtils.hasText(category) ? category : requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "DOCUMENT_CATEGORY"),
                tags,
                remark
        );
        FileObjectDTO uploaded = getInsertedFile(insertedId);
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
        return uploadImage(currentUser, file, category, null, remark, bucket, VISIBILITY_SCOPE_PERSONAL, null);
    }

    @Transactional
    public FileObjectDTO uploadPublicImage(CurrentUser currentUser, MultipartFile file, String category, String remark) {
        return uploadImage(currentUser, file, category, null, remark, null, VISIBILITY_SCOPE_PUBLIC, null);
    }

    @Transactional
    public FileObjectDTO uploadPublicImage(CurrentUser currentUser, MultipartFile file, String category, String remark, String bucket) {
        return uploadImage(currentUser, file, category, null, remark, bucket, VISIBILITY_SCOPE_PUBLIC, null);
    }

    private FileObjectDTO uploadImage(
            CurrentUser currentUser,
            MultipartFile file,
            String category,
            String tags,
            String remark,
            String bucket,
            String visibilityScope,
            String directory
    ) {
        requireCurrentUser(currentUser);
        Long actorUserId = trustedUserId(currentUser);
        StorageSpaceUploadContext storageContext = resolveUploadContext(bucket);
        FileUploadDirectory.Scope uploadDirectory = FileUploadDirectory.resolve(
                storageContext.storageRoot(),
                storageContext.publicPath(),
                directory
        );
        ImageUploadService.StoredImage storedImage = imageUploadService.upload(
                file,
                uploadDirectory.storageRoot(),
                uploadDirectory.publicPath(),
                storageContext.maxFileSizeBytes(),
                storageContext.storageSpace().renameStrategy(),
                storageContext.storageSpace().allowedMimeTypes()
        );
        Long insertedId = insertFileObject(
                currentUser,
                storageContext.storageSpace().provider(),
                storageContext.storageBucket(),
                FileUploadDirectory.qualifyObjectKey(uploadDirectory.directory(), storedImage.relativePath()),
                storedImage.originalFileName(),
                normalizeText(storedImage.fileExtension().replaceFirst("^\\.", "")),
                storedImage.contentType(),
                storedImage.fileSizeBytes(),
                storedImage.publicUrl(),
                resolvePreviewMode(storedImage.fileExtension(), storedImage.contentType()),
                true,
                visibilityScope,
                StringUtils.hasText(category) ? category : requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "IMAGE_CATEGORY"),
                tags,
                remark
        );
        FileObjectDTO uploaded = getInsertedFile(insertedId);
        if (VISIBILITY_SCOPE_PUBLIC.equals(visibilityScope)) {
            uploaded = synchronouslyScanPublicImage(uploaded, currentUser);
        }
        publishFileUploaded(uploaded, currentUser);
        fileProcessingTaskRequestService.requestTasksForUpload(uploaded, currentUser);
        return uploaded;
    }

    private FileObjectDTO synchronouslyScanPublicImage(FileObjectDTO uploaded, CurrentUser currentUser) {
        FileSecurityScanProcessor processor = securityScanProcessorProvider == null
                ? null
                : securityScanProcessorProvider.getIfAvailable();
        if (processor == null) {
            throw visibleBizException(ErrorCode.SYSTEM_ERROR, "Public image security scanner is unavailable");
        }
        try {
            processor.scan(uploaded.id(), trustedUserId(currentUser), trustedUserUuid(currentUser));
        } catch (RuntimeException exception) {
            log.warn("Synchronous public image security scan failed fileId={}", uploaded.id(), exception);
            throw visibleBizException(ErrorCode.FORBIDDEN, "Public image failed its security scan");
        }
        FileObjectDTO scanned = getInsertedFile(uploaded.id());
        if (!FileObjectSecurityStatus.CLEAN.equalsIgnoreCase(scanned.status())) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "Public image did not pass its security scan");
        }
        return scanned;
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        deleteFile(currentUser, fileId, sharedScope, false);
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        TrustedCurrentUser actor = requirePermission(currentUser, resolveDeletePermission(sharedScope, downloadCenterScope));
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        FileObjectDTO file = queryFile(actor, fileId, sharedScope, downloadCenterScope);
        if (!shouldRetainStoredFile(file.bucket())) {
            deleteStoredFile(file);
        }
        fileObjectRepository.softDelete(fileId, actorUserId, actorUserUuid, !sharedScope && !downloadCenterScope);
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
        requirePermission(currentUser, "system:file:manage");
        ensureDefaultStorageSpaces();
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long safeOffset = (safePageNo - 1L) * safePageSize;
        long totalLimit = calculateStorageSpaceListTotalCountLimit();
        Long total = storageSpaceRepository.countCandidates(totalLimit);
        long normalizedTotal = normalizeTotal(total, STORAGE_SPACE_LIST_TOTAL_COUNT_CAP);
        boolean totalCapped = isTotalCapped(total, STORAGE_SPACE_LIST_TOTAL_COUNT_CAP);
        List<StorageSpaceDTO> records = storageSpaceRepository
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

    public List<StorageSpaceOptionDTO> listStorageSpaceOptions(CurrentUser currentUser) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        if (!hasPermission(actor, "system:file:manage")
                && !hasPermission(actor, "aiadc:competition:update")) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "Permission denied");
        }
        ensureDefaultStorageSpaces();
        return storageSpaceRepository
                .listWithUsage(MAX_PAGE_SIZE, 0L)
                .stream()
                .filter(item -> "ENABLED".equalsIgnoreCase(item.getStatus()))
                .filter(item -> !CompetitionStorageSpace.isCompetitionStorageKey(item.getStorageKey()))
                .map(this::mapStorageSpace)
                .map(item -> new StorageSpaceOptionDTO(
                        item.title(),
                        item.storageKey(),
                        item.defaultStorage()
                ))
                .toList();
    }

    /**
     * Creates the system-managed local storage space owned by one competition.
     * The deterministic key makes retries and concurrent provisioning idempotent.
     */
    @Transactional
    public void ensureCompetitionStorageSpace(CompetitionStorageSpaceRequest request) {
        if (request == null) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Competition storage request is required");
        }
        if (request.competitionId() == null || request.competitionId() <= 0L) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Competition id is required");
        }
        if (request.operatorUserId() == null || request.operatorUserId() <= 0L
                || !StringUtils.hasText(request.operatorUserUuid())
                || request.operatorUserUuid().trim().length() > 36) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Competition storage operator is invalid");
        }

        String storageKey;
        String rootPath;
        try {
            storageKey = CompetitionStorageSpace.storageKey(request.competitionUuid());
            rootPath = CompetitionStorageSpace.rootPath(request.competitionUuid());
        } catch (IllegalArgumentException exception) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Competition uuid is invalid");
        }

        FileStorageSpaceEntity existing = storageSpaceRepository.findByStorageKey(storageKey);
        if (existing != null) {
            requireMatchingCompetitionStorage(existing, rootPath);
            createCompetitionStorageDirectory(existing);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        FileStorageSpaceEntity entity = new FileStorageSpaceEntity();
        entity.setTitle(CompetitionStorageSpace.title(request.competitionId(), request.competitionTitle()));
        entity.setStorageKey(storageKey);
        entity.setProvider("LOCAL");
        entity.setRootPath(rootPath);
        entity.setBucketName(storageKey);
        entity.setEndpoint("");
        entity.setRegion("");
        entity.setAccessKeyId("");
        entity.setAccessKeySecret(null);
        entity.setRenameStrategy("APPEND_RANDOM_ID");
        entity.setMaxFileSizeMb(COMPETITION_STORAGE_MAX_FILE_SIZE_MB);
        entity.setAllowedMimeTypes("*");
        entity.setDefaultFlag(0);
        entity.setRetainFileOnRecordDelete(0);
        entity.setAnonymousAccessAllowed(0);
        entity.setStatus("ENABLED");
        entity.setCreatedBy(request.operatorUserId());
        entity.setCreatedByUuid(request.operatorUserUuid().trim());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(request.operatorUserId());
        entity.setUpdatedByUuid(request.operatorUserUuid().trim());
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        try {
            storageSpaceRepository.insert(entity);
        } catch (DuplicateKeyException exception) {
            FileStorageSpaceEntity concurrent = storageSpaceRepository.findByStorageKey(storageKey);
            if (concurrent == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "Competition storage space could not be created");
            }
            requireMatchingCompetitionStorage(concurrent, rootPath);
            createCompetitionStorageDirectory(concurrent);
            return;
        }
        createCompetitionStorageDirectory(entity);
    }

    private void requireMatchingCompetitionStorage(FileStorageSpaceEntity storageSpace, String expectedRootPath) {
        if (!"LOCAL".equalsIgnoreCase(storageSpace.getProvider())
                || !resolveStorageRoot(storageSpace).equals(resolveStorageRoot(expectedRootPath))) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Competition storage space conflicts with existing configuration");
        }
    }

    private void createCompetitionStorageDirectory(FileStorageSpaceEntity storageSpace) {
        try {
            Files.createDirectories(resolveStorageRoot(storageSpace));
        } catch (IOException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Competition storage directory could not be created");
        }
    }

    private long calculateFileListTotalCountLimit() {
        return FILE_LIST_TOTAL_COUNT_CAP + 1L;
    }

    private long calculateStorageSpaceListTotalCountLimit() {
        return STORAGE_SPACE_LIST_TOTAL_COUNT_CAP + 1L;
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
        return total > limit;
    }

    public StorageSpaceDTO getStorageSpace(CurrentUser currentUser, String storageKey) {
        requirePermission(currentUser, "system:file:manage");
        return queryStorageSpace(normalizeStorageKey(storageKey));
    }

    public FileStorageSpaceRequest.TestResult testStorageSpace(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, "system:file:manage");
        FileStorageSpaceEntity entity = storageSpaceRepository.findByIdWithUsage(id);
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
        TrustedCurrentUser actor = requirePermission(currentUser, "system:file:manage");
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        String provider = normalizeProvider(request.getProvider());
        String storageKey = normalizeStorageKey(StringUtils.hasText(request.getStorageKey()) ? request.getStorageKey() : provider.toLowerCase(Locale.ROOT) + "_" + shortId());
        requireUserManagedStorageKey(storageKey);
        StoragePayload payload = normalizeStoragePayload(request, provider, storageKey, null);
        if (payload.defaultStorage()) {
            clearDefaultStorage();
        }
        try {
            storageSpaceRepository.insert(buildStorageSpaceEntity(payload, actorUserId, actorUserUuid));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间标识已存在");
        }
        ensureOneDefaultStorage();
        return queryStorageSpace(storageKey);
    }

    @Transactional
    public StorageSpaceDTO updateStorageSpace(CurrentUser currentUser, Long id, FileStorageSpaceRequest request) {
        TrustedCurrentUser actor = requirePermission(currentUser, "system:file:manage");
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        StorageSpaceDTO existing = queryStorageSpaceById(id);
        requireUserManagedStorageKey(existing.storageKey());
        StoragePayload payload = normalizeStoragePayload(request, existing.provider(), existing.storageKey(), existing);
        if (payload.defaultStorage()) {
            clearDefaultStorage();
        }
        storageSpaceRepository.update(id, existing, buildStorageSpaceEntity(payload, actorUserId, actorUserUuid));
        ensureOneDefaultStorage();
        return queryStorageSpaceById(id);
    }

    @Transactional
    public void deleteStorageSpace(CurrentUser currentUser, Long id) {
        TrustedCurrentUser actor = requirePermission(currentUser, "system:file:manage:delete");
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        StorageSpaceDTO existing = queryStorageSpaceById(id);
        requireUserManagedStorageKey(existing.storageKey());
        if (hasFileRecordsInBucket(existing.storageKey())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间下仍有文件，不能删除");
        }
        if (Boolean.TRUE.equals(existing.defaultStorage())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "默认存储空间不能删除");
        }
        storageSpaceRepository.delete(id, existing, actorUserId, actorUserUuid);
    }

    private boolean hasFileRecordsInBucket(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return false;
        }
        return fileObjectRepository.existsInBucket(storageKey);
    }

    private void requireUserManagedStorageKey(String storageKey) {
        if (CompetitionStorageSpace.isCompetitionStorageKey(storageKey)) {
            throw new BizException(ErrorCode.FORBIDDEN, "比赛专属存储由系统自动管理");
        }
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return resolveFilePath(currentUser, fileId, sharedScope, false);
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(
                requirePermission(currentUser, resolveReadPermission(sharedScope, downloadCenterScope)),
                fileId,
                sharedScope,
                downloadCenterScope
        );
        file = ensureDownloadCenterContentReady(currentUser, file, sharedScope, downloadCenterScope);
        requireContentAccessible(file);
        Path target = resolveFilePath(file);
        if (target == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件路径无效");
        }
        return target;
    }

    public FileContentDTO readFileContent(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        FileObjectDTO file = queryFile(
                requirePermission(currentUser, resolveReadPermission(sharedScope, downloadCenterScope)),
                fileId,
                sharedScope,
                downloadCenterScope
        );
        requireContentAccessible(file);
        return readFileContent(file);
    }

    /**
     * Makes a trusted generated file readable before an async export task is completed.
     * The same security scanner used by the background file processor is used here; no
     * file is made readable by changing its status without a scan result.
     */
    public FileObjectDTO ensureFileContentReady(CurrentUser currentUser, Long fileId) {
        TrustedCurrentUser actor = requirePermission(currentUser, "system:file:upload");
        if (fileId == null || fileId <= 0) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "fileId must be a positive number");
        }
        FileObjectEntity entity = fileObjectRepository.findById(fileId);
        if (entity == null
                || Integer.valueOf(1).equals(entity.getDeleted())
                || !actor.userId().equals(entity.getUploadedBy())
                || !actor.userUuid().equals(entity.getUploadedByUuid())) {
            throw new BizException(ErrorCode.NOT_FOUND, "File not found");
        }
        FileObjectDTO file = mapFileObject(entity);
        if (FileObjectSecurityStatus.isContentAccessible(file.status())) {
            return enrich(file);
        }
        FileSecurityScanProcessor processor = securityScanProcessorProvider == null
                ? null
                : securityScanProcessorProvider.getIfAvailable();
        if (processor == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "File security scanner is unavailable");
        }
        FileSecurityScanProcessor.SecurityScanResult result = processor.scan(
                fileId,
                actor.userId(),
                actor.userUuid()
        );
        if (!FileSecurityScanProcessor.VERDICT_CLEAN.equalsIgnoreCase(result.verdict())) {
            throw new BizException(ErrorCode.FORBIDDEN, "File failed its security scan");
        }
        return getInsertedFile(fileId);
    }

    public FileContentDTO readAuthorizedBusinessFileContent(
            CurrentUser currentUser,
            Long fileId,
            String referenceType,
            Long referenceId
    ) {
        resolveTrustedCurrentUser(currentUser);
        if (!"competition.registration.material".equals(referenceType)
                || referenceId == null
                || referenceId <= 0
                || fileId == null
                || fileId <= 0) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Valid business file reference is required");
        }
        FileObjectEntity entity = fileObjectRepository.findById(fileId);
        if (entity == null
                || Integer.valueOf(1).equals(entity.getDeleted())
                || !FileObjectSecurityStatus.isContentAccessible(entity.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "File not found");
        }
        return readFileContent(mapFileObject(entity));
    }

    private FileContentDTO readFileContent(FileObjectDTO file) {
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
        FileObjectDTO file = queryFile(
                requirePermission(currentUser, resolveReadPermission(sharedScope, downloadCenterScope)),
                fileId,
                sharedScope,
                downloadCenterScope
        );
        FileProcessingArtifactDTO artifact = artifactRepository.findLatest(
                file.id(), artifactType.trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (artifact == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件处理产物不存在");
        }
        return artifact;
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean sharedScope) {
        return queryFile(currentUser, fileId, sharedScope, false);
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        return queryFile(
                requirePermission(currentUser, resolveReadPermission(sharedScope, downloadCenterScope)),
                fileId,
                sharedScope,
                downloadCenterScope
        );
    }

    private FileObjectDTO queryFile(TrustedCurrentUser actor, Long fileId, boolean sharedScope, boolean downloadCenterScope) {
        if (fileId == null || fileId <= 0) {
            throw visibleBizException(ErrorCode.BAD_REQUEST, "Valid file id is required");
        }
        FileObjectEntity entity = fileObjectRepository.findVisibleById(
                fileId, resolveFileAccess(actor, sharedScope, downloadCenterScope));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return mapFileObject(entity);
    }

    private FileObjectDTO enrich(FileObjectDTO file) {
        boolean contentAccessible = FileObjectSecurityStatus.isContentAccessible(file.status());
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
                contentAccessible ? file.publicUrl() : null,
                contentAccessible ? (StringUtils.hasText(file.previewUrl()) ? file.previewUrl() : file.publicUrl()) : null,
                contentAccessible ? (StringUtils.hasText(file.downloadUrl()) ? file.downloadUrl() : file.publicUrl()) : null,
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
            FileStorageSpaceEntity storageSpace = storageSpaceRepository.findByStorageKey(file.bucket());
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
        requirePermission(actor, resolveUploadPermission(visibilityScope));
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
        entity.setStatus(FileObjectSecurityStatus.PENDING_SCAN);
        entity.setCreatedBy(actorUserId);
        entity.setCreatedByUuid(actorUserUuid);
        entity.setCreatedAt(now);
        entity.setUpdatedBy(actorUserId);
        entity.setUpdatedByUuid(actorUserUuid);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        fileObjectRepository.insert(entity);
        if (entity.getId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录保存失败");
        }
        return entity.getId();
    }

    private FileObjectDTO getInsertedFile(Long insertedId) {
        FileObjectEntity inserted = fileObjectRepository.findById(insertedId);
        if (inserted == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录读取失败");
        }
        return enrich(mapFileObject(inserted));
    }

    private void requireContentAccessible(FileObjectDTO file) {
        if (file == null || !FileObjectSecurityStatus.isContentAccessible(file.status())) {
            throw visibleBizException(ErrorCode.FORBIDDEN, "File content is unavailable until its security scan passes");
        }
    }

    /**
     * The native monolith intentionally runs without the asynchronous worker by default. Download-center
     * files uploaded in that mode must still be scanned when the user first requests their content; otherwise
     * a file can remain in PENDING_SCAN forever even though the configured scanner is available.
     *
     * This method never makes content readable without a scanner verdict. Failed or rejected scans continue
     * through requireContentAccessible and remain blocked.
     */
    private FileObjectDTO ensureDownloadCenterContentReady(
            CurrentUser currentUser,
            FileObjectDTO file,
            boolean sharedScope,
            boolean downloadCenterScope
    ) {
        if (!downloadCenterScope
                || file == null
                || FileObjectSecurityStatus.isContentAccessible(file.status())
                || !isRetryableSecurityScanStatus(file.status())
                || file.id() == null
                || file.uploadedBy() == null
                || file.uploadedBy() <= 0
                || !StringUtils.hasText(file.uploadedByUuid())) {
            return file;
        }

        FileSecurityScanProcessor processor = securityScanProcessorProvider == null
                ? null
                : securityScanProcessorProvider.getIfAvailable();
        if (processor == null) {
            return file;
        }

        try {
            processor.scan(file.id(), file.uploadedBy(), file.uploadedByUuid());
            return getFile(currentUser, file.id(), sharedScope, true);
        } catch (RuntimeException exception) {
            log.warn("Download-center file content scan failed fileId={}", file.id(), exception);
            return file;
        }
    }

    private boolean isRetryableSecurityScanStatus(String status) {
        return FileObjectSecurityStatus.PENDING_SCAN.equalsIgnoreCase(status)
                || FileObjectSecurityStatus.FAILED.equalsIgnoreCase(status);
    }

    private FileObjectRepository.Access resolveFileAccess(TrustedCurrentUser actor, boolean sharedScopeRequested, boolean downloadCenterScope) {
        Long actorUserId = actor.userId();
        String actorUserUuid = actor.userUuid();
        if (!sharedScopeRequested) {
            return new FileObjectRepository.Access(downloadCenterScope, false, actorUserId, actorUserUuid, Set.of(), Set.of());
        }
        if (downloadCenterScope) {
            requirePermission(actor, "download:center:view");
            return new FileObjectRepository.Access(true, true, null, null, Set.of(), Set.of());
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
            return new FileObjectRepository.Access(false, true, null, null, Set.of(), Set.of());
        }
        Set<Long> deptIds = new java.util.LinkedHashSet<>(decision.deptIds());
        Set<Long> userIds = new java.util.LinkedHashSet<>(decision.userIds());
        if (decision.hasDeptRestriction()) {
            userIds.add(actorUserId);
        }
        if (deptIds.isEmpty() && userIds.isEmpty()) {
            return new FileObjectRepository.Access(false, false, actorUserId, actorUserUuid, Set.of(), Set.of());
        }
        return new FileObjectRepository.Access(false, false, null, null, Set.copyOf(deptIds), Set.copyOf(userIds));
    }

    private TrustedCurrentUser requirePermission(CurrentUser currentUser, String permission) {
        TrustedCurrentUser actor = resolveTrustedCurrentUser(currentUser);
        requirePermission(actor, permission);
        return actor;
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

    private String resolveReadPermission(boolean sharedScope, boolean downloadCenterScope) {
        if (downloadCenterScope) {
            return "download:center:view";
        }
        if (sharedScope) {
            return "system:file:manage";
        }
        return "system:file:view";
    }

    private String resolveUploadPermission(String visibilityScope) {
        String normalizedScope = resolveVisibilityScope(visibilityScope);
        if (VISIBILITY_SCOPE_DOWNLOAD_CENTER.equalsIgnoreCase(normalizedScope)) {
            return "download:center:create";
        }
        if (VISIBILITY_SCOPE_PUBLIC.equalsIgnoreCase(normalizedScope)) {
            return "system:file:publish";
        }
        return "system:file:upload";
    }

    private String resolveDeletePermission(boolean sharedScope, boolean downloadCenterScope) {
        if (downloadCenterScope) {
            return "download:center:delete";
        }
        if (sharedScope) {
            return "system:file:manage:delete";
        }
        return "system:file:delete";
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
        FileStorageSpaceEntity entity = storageSpaceRepository.findDefault();
        if (entity != null) {
            return mapStorageSpace(entity);
        }
        throw visibleBizException(ErrorCode.SYSTEM_ERROR, "Default storage space is not configured in database");
    }

    private StorageSpaceDTO queryStorageSpace(String storageKey) {
        ensureDefaultStorageSpaces();
        FileStorageSpaceEntity entity = storageSpaceRepository.findByStorageKey(storageKey);
        if (entity == null) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "Storage space does not exist");
        }
        return mapStorageSpace(entity);
    }

    private StorageSpaceDTO queryStorageSpaceById(Long id) {
        FileStorageSpaceEntity entity = storageSpaceRepository.findByIdWithUsage(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Storage space does not exist");
        }
        return mapStorageSpace(entity);
    }

    private boolean shouldRetainStoredFile(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return false;
        }
        return Boolean.TRUE.equals(storageSpaceRepository.shouldRetainStoredFile(bucket));
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
        FileStorageSpaceEntity entity = storageSpaceRepository.findByStorageKey(storageKey);
        if (entity != null) {
            return mapStorageSpace(entity);
        }
        if (CompetitionStorageSpace.isCompetitionStorageKey(storageKey)) {
            throw visibleBizException(ErrorCode.NOT_FOUND, "Competition storage space does not exist");
        }
        log.warn("Upload storage space '{}' is missing, falling back to default storage space", storageKey);
        return getDefaultStorageSpace();
    }
    private long maxFileSizeBytes(Integer maxFileSizeMb) {
        int safeMaxFileSizeMb = maxFileSizeMb == null || maxFileSizeMb <= 0
                ? requiredPolicyInteger(DICT_RUNTIME_DEFAULT, "MAX_FILE_SIZE_MB") : maxFileSizeMb;
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
        storageSpaceRepository.clearDefaultStorage();
    }

    private void ensureOneDefaultStorage() {
        Long count = storageSpaceRepository.countDefaultStorage();
        if (count != null && count > 0) {
            return;
        }
        storageSpaceRepository.ensureFirstDefaultStorage();
    }

    private void ensureDefaultStorageSpaces() {
        ensureOneDefaultStorage();
    }
    private String relativePathFromPublicUrl(String publicUrl) {
        return publicUrl;
    }

    private String resolvePreviewMode(String extension, String contentType) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        for (FileBusinessPolicyRepository.Item rule : businessPolicyRepository.findEnabledItems(DICT_PREVIEW_EXTENSION)) {
            if (normalizedExtension.equalsIgnoreCase(rule.value())) {
                return rule.label();
            }
        }
        for (FileBusinessPolicyRepository.Item rule : businessPolicyRepository.findEnabledItems(DICT_PREVIEW_CONTENT_TYPE)) {
            boolean prefix = "PREFIX".equalsIgnoreCase(rule.remark());
            if ((prefix && normalizedContentType.startsWith(rule.value().toLowerCase(Locale.ROOT)))
                    || (!prefix && normalizedContentType.equalsIgnoreCase(rule.value()))) {
                return rule.label();
            }
        }
        return requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "UNSUPPORTED_PREVIEW_MODE");
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

    private String trimToNull(String value) {
        return normalizeText(value);
    }

    private String normalizeLower(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
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
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted acting user resolver is unavailable");
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
        Long simulatedRoleId = currentUser.getSimulatedRoleId();
        if (simulatedRoleId != null && simulatedRoleId <= 0) {
            simulatedRoleId = null;
        }
        PermissionSnapshotDTO permissionSnapshot = simulatedRoleId == null
                ? internalApi.permissionSnapshot(userId, userSnapshot.userUuid().trim())
                : internalApi.simulatedRolePermissionSnapshot(userId, userSnapshot.userUuid().trim(), simulatedRoleId);
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
        String rootPath = defaultIfBlank(request.getRootPath(), existing == null ? requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "ROOT_PATH") : existing.rootPath());
        String bucketName = defaultIfBlank(request.getBucketName(), existing == null ? "" : existing.bucketName());
        String endpoint = defaultIfBlank(request.getEndpoint(), existing == null ? "" : existing.endpoint());
        String region = defaultIfBlank(request.getRegion(), existing == null ? "" : existing.region());
        String accessKeyId = defaultIfBlank(request.getAccessKeyId(), existing == null ? "" : existing.accessKeyId());
        String accessKeySecret = StringUtils.hasText(request.getAccessKeySecret()) ? request.getAccessKeySecret().trim() : null;
        if (existing != null && !StringUtils.hasText(accessKeySecret)) {
            accessKeySecret = decryptSecret(storageSpaceRepository.findAccessKeySecret(existing.id()));
        }
        String renameStrategy = normalizeRenameStrategy(defaultIfBlank(request.getRenameStrategy(), existing == null ? requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "RENAME_STRATEGY") : existing.renameStrategy()));
        Integer maxFileSizeMb = request.getMaxFileSizeMb() == null ? (existing == null ? requiredPolicyInteger(DICT_RUNTIME_DEFAULT, "MAX_FILE_SIZE_MB") : existing.maxFileSizeMb()) : request.getMaxFileSizeMb();
        String allowedMimeTypes = defaultIfBlank(request.getAllowedMimeTypes(), existing == null ? requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "ALLOWED_MIME_TYPES") : existing.allowedMimeTypes());
        boolean defaultStorage = request.getDefaultStorage() == null ? existing == null || Boolean.TRUE.equals(existing.defaultStorage()) : request.getDefaultStorage();
        boolean retain = request.getRetainFileOnRecordDelete() == null ? existing != null && Boolean.TRUE.equals(existing.retainFileOnRecordDelete()) : request.getRetainFileOnRecordDelete();
        boolean anonymousAccessAllowed = request.getAnonymousAccessAllowed() == null ? existing != null && Boolean.TRUE.equals(existing.anonymousAccessAllowed()) : request.getAnonymousAccessAllowed();
        String status = defaultIfBlank(request.getStatus(), existing == null
                ? requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "STORAGE_STATUS") : existing.status()).toUpperCase(Locale.ROOT);
        if (businessPolicyRepository.findEnabledItem(DICT_STORAGE_STATUS, status).isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unsupported storage status");
        }
        if (maxFileSizeMb == null || maxFileSizeMb < 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "文件大小限制最小为 1MB");
        }
        return new StoragePayload(title, storageKey, provider, rootPath, bucketName, endpoint, region, accessKeyId, accessKeySecret, renameStrategy, maxFileSizeMb, allowedMimeTypes, defaultStorage, retain, anonymousAccessAllowed, status);
    }

    private String normalizeProvider(String provider) {
        String normalized = defaultIfBlank(provider, requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "STORAGE_PROVIDER")).toUpperCase(Locale.ROOT);
        if (businessPolicyRepository.findEnabledItem(DICT_STORAGE_PROVIDER, normalized).isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "不支持的存储类型");
        }
        return normalized;
    }

    private String normalizeRenameStrategy(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (businessPolicyRepository.findEnabledItem(DICT_RENAME_STRATEGY, normalized).isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Unsupported file rename strategy");
        }
        return normalized;
    }

    private String normalizeStorageKey(String value) {
        String normalized = defaultIfBlank(value, requiredPolicyLabel(DICT_RUNTIME_DEFAULT, "STORAGE_KEY"))
                .trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (!normalized.matches("^[a-z][a-z0-9_]*$")) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间标识必须以英文字母开头，且只包含英文、数字和下划线");
        }
        return normalized;
    }

    private String providerLabel(String provider) {
        return businessPolicyRepository.findEnabledItem(DICT_STORAGE_PROVIDER, provider)
                .map(FileBusinessPolicyRepository.Item::label)
                .orElseThrow(() -> new BizException(ErrorCode.BIZ_ERROR, "Storage provider dictionary is not configured"));
    }

    private String requiredPolicyLabel(String dictionaryCode, String value) {
        return businessPolicyRepository.findEnabledItem(dictionaryCode, value)
                .map(FileBusinessPolicyRepository.Item::label)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR,
                        "File business dictionary is not configured: " + dictionaryCode + "/" + value));
    }

    private int requiredPolicyInteger(String dictionaryCode, String value) {
        try {
            return Integer.parseInt(requiredPolicyLabel(dictionaryCode, value));
        } catch (NumberFormatException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "File business dictionary value must be an integer: " + dictionaryCode + "/" + value);
        }
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

}
