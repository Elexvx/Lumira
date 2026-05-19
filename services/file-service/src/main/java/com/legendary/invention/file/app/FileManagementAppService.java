package com.legendary.invention.file.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.legendary.invention.api.file.FileObjectDTO;
import com.legendary.invention.api.file.StorageSpaceDTO;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.security.data.DataPermissionDecision;
import com.legendary.invention.common.security.data.DataPermissionResolver;
import com.legendary.invention.common.security.data.DataScopeType;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.file.config.UploadProperties;
import com.legendary.invention.file.dto.FileStorageSpaceRequest;
import com.legendary.invention.file.entity.FileObjectEntity;
import com.legendary.invention.file.entity.FileStorageSpaceEntity;
import com.legendary.invention.file.mapper.FileObjectMapper;
import com.legendary.invention.file.mapper.FileStorageSpaceMapper;
import com.legendary.invention.file.upload.DocumentUploadService;
import com.legendary.invention.file.upload.ImageUploadService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileManagementAppService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final String RESOURCE_FILE_OBJECT = "file:object";
    private static final String DEFAULT_SORT_COLUMN = "created_at";
    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_TENANT = "tenant";
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
    private final UploadProperties uploadProperties;
    private final DocumentUploadService documentUploadService;
    private final ImageUploadService imageUploadService;

    public FileManagementAppService(
            FileObjectMapper fileObjectMapper,
            FileStorageSpaceMapper fileStorageSpaceMapper,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService
    ) {
        this.fileObjectMapper = fileObjectMapper;
        this.fileStorageSpaceMapper = fileStorageSpaceMapper;
        this.uploadProperties = uploadProperties;
        this.documentUploadService = documentUploadService;
        this.imageUploadService = imageUploadService;
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
        boolean tenantScope = SCOPE_TENANT.equalsIgnoreCase(scope);
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, currentUser, tenantScope);
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
        Long total = fileObjectMapper.selectCount(queryWrapper.clone());
        List<FileObjectDTO> records = fileObjectMapper.selectList(queryWrapper
                        .orderBy(true, ascending, sortColumn)
                        .last("limit " + safePageSize + " offset " + ((safePageNo - 1L) * safePageSize)))
                .stream()
                .map(this::mapFileObject)
                .map(this::enrich)
                .toList();
        PageResponse<FileObjectDTO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope);
        return enrich(file);
    }

    @Transactional
    public FileObjectDTO uploadFile(CurrentUser currentUser, MultipartFile file, String category, String tags, String remark) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先选择上传文件");
        }
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (ImageUploadService.supports(originalFilename, contentType)) {
            return uploadImage(currentUser, file, category, remark);
        }
        if (DocumentUploadService.supports(originalFilename, contentType)) {
            return uploadDocument(currentUser, file, category, tags, remark);
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "仅允许上传图片、PDF、Word、Excel、PPT 文件");
    }

    @Transactional
    public FileObjectDTO uploadDocument(CurrentUser currentUser, MultipartFile file, String category, String tags, String remark) {
        DocumentUploadService.StoredDocument storedDocument = documentUploadService.upload(file);
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceDTO storageSpace = getDefaultStorageSpace(tenantId);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                storageSpace.provider(),
                storageSpace.storageKey(),
                storedDocument.relativePath(),
                storedDocument.originalFileName(),
                storedDocument.fileExtension(),
                storedDocument.contentType(),
                storedDocument.fileSizeBytes(),
                storedDocument.publicUrl(),
                storedDocument.previewMode(),
                storedDocument.previewable(),
                StringUtils.hasText(category) ? category : "我的文件",
                tags,
                remark
        );
        return getInsertedFile(tenantId, insertedId);
    }

    @Transactional
    public FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file, String category, String remark) {
        ImageUploadService.StoredImage storedImage = imageUploadService.upload(file);
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceDTO storageSpace = getDefaultStorageSpace(tenantId);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                storageSpace.provider(),
                storageSpace.storageKey(),
                storedImage.relativePath(),
                storedImage.originalFileName(),
                normalizeText(storedImage.fileExtension().replaceFirst("^\\.", "")),
                storedImage.contentType(),
                storedImage.fileSizeBytes(),
                storedImage.publicUrl(),
                resolvePreviewMode(storedImage.fileExtension(), storedImage.contentType()),
                true,
                StringUtils.hasText(category) ? category : "图片",
                null,
                remark
        );
        return getInsertedFile(tenantId, insertedId);
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope);
        if (!shouldRetainStoredFile(currentTenantId(currentUser), file.bucket())) {
            deleteStoredFile(file.storagePath());
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
    }

    public PageResponse<StorageSpaceDTO> listStorageSpaces(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        Long total = fileStorageSpaceMapper.selectCount(new LambdaQueryWrapper<FileStorageSpaceEntity>()
                .eq(FileStorageSpaceEntity::getTenantId, tenantId)
                .eq(FileStorageSpaceEntity::getDeleted, 0));
        List<StorageSpaceDTO> records = fileStorageSpaceMapper
                .listWithUsage(tenantId, safePageSize, (safePageNo - 1L) * safePageSize)
                .stream()
                .map(this::mapStorageSpace)
                .toList();
        PageResponse<StorageSpaceDTO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public StorageSpaceDTO getStorageSpace(CurrentUser currentUser, String storageKey) {
        return queryStorageSpace(currentTenantId(currentUser), normalizeStorageKey(storageKey));
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
                        .set(FileStorageSpaceEntity::getAccessKeySecret, payload.accessKeySecret())
                        .set(FileStorageSpaceEntity::getRenameStrategy, payload.renameStrategy())
                        .set(FileStorageSpaceEntity::getMaxFileSizeMb, payload.maxFileSizeMb())
                        .set(FileStorageSpaceEntity::getAllowedMimeTypes, payload.allowedMimeTypes())
                        .set(FileStorageSpaceEntity::getDefaultFlag, payload.defaultStorage() ? 1 : 0)
                        .set(FileStorageSpaceEntity::getRetainFileOnRecordDelete, payload.retainFileOnRecordDelete() ? 1 : 0)
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
        Long fileCount = fileObjectMapper.selectCount(new LambdaQueryWrapper<FileObjectEntity>()
                .eq(FileObjectEntity::getTenantId, tenantId)
                .eq(FileObjectEntity::getBucket, existing.storageKey())
                .eq(FileObjectEntity::getDeleted, 0));
        if (fileCount != null && fileCount > 0) {
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

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileObjectDTO file = queryFile(currentUser, fileId, tenantScope);
        Path target = resolveFilePath(file.storagePath());
        if (target == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件路径无效");
        }
        return target;
    }

    private FileObjectDTO queryFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        Long tenantId = currentTenantId(currentUser);
        QueryWrapper<FileObjectEntity> queryWrapper = new QueryWrapper<FileObjectEntity>()
                .eq("id", fileId)
                .eq("tenant_id", tenantId)
                .eq("deleted", 0);
        applyFileDataPermission(queryWrapper, currentUser, tenantScope);
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

    private void deleteStoredFile(String relativePath) {
        Path target = resolveFilePath(relativePath);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (Exception ignored) {
            // Keep metadata cleanup resilient even when filesystem cleanup fails.
        }
    }

    private Path resolveFilePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        Path directPath = Path.of(relativePath);
        if (directPath.isAbsolute()) {
            return directPath.normalize();
        }
        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        Path target = storageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(storageRoot)) {
            return null;
        }
        return target;
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

    private void applyFileDataPermission(QueryWrapper<FileObjectEntity> queryWrapper, CurrentUser currentUser, boolean tenantScopeRequested) {
        if (!tenantScopeRequested) {
            queryWrapper.eq("uploaded_by", currentUser.getUserId());
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

    private StorageSpaceDTO getDefaultStorageSpace(Long tenantId) {
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findDefault(tenantId);
        if (entity != null) {
            return mapStorageSpace(entity);
        }
        return new StorageSpaceDTO(null, tenantId, "Local storage", "local", "LOCAL", "storage/uploads/", null, null, null, null, false, "APPEND_RANDOM_ID", 20, "*", true, false, "ENABLED", 0L, 0L, "0B", null, null);
    }

    private StorageSpaceDTO queryStorageSpace(Long tenantId, String storageKey) {
        FileStorageSpaceEntity entity = fileStorageSpaceMapper.findByStorageKey(tenantId, storageKey);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
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
        return com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
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
            accessKeySecret = fileStorageSpaceMapper.findAccessKeySecret(existing.tenantId(), existing.id());
        }
        String renameStrategy = normalizeRenameStrategy(defaultIfBlank(request.getRenameStrategy(), existing == null ? "APPEND_RANDOM_ID" : existing.renameStrategy()));
        Integer maxFileSizeMb = request.getMaxFileSizeMb() == null ? (existing == null ? 20 : existing.maxFileSizeMb()) : request.getMaxFileSizeMb();
        String allowedMimeTypes = defaultIfBlank(request.getAllowedMimeTypes(), existing == null ? "*" : existing.allowedMimeTypes());
        boolean defaultStorage = request.getDefaultStorage() == null ? existing == null || Boolean.TRUE.equals(existing.defaultStorage()) : request.getDefaultStorage();
        boolean retain = request.getRetainFileOnRecordDelete() == null ? existing != null && Boolean.TRUE.equals(existing.retainFileOnRecordDelete()) : request.getRetainFileOnRecordDelete();
        String status = "DISABLED".equalsIgnoreCase(request.getStatus()) ? "DISABLED" : "ENABLED";
        if (maxFileSizeMb == null || maxFileSizeMb < 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "文件大小限制最小为 1MB");
        }
        return new StoragePayload(title, storageKey, provider, rootPath, bucketName, endpoint, region, accessKeyId, accessKeySecret, renameStrategy, maxFileSizeMb, allowedMimeTypes, defaultStorage, retain, status);
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
        entity.setAccessKeySecret(payload.accessKeySecret());
        entity.setRenameStrategy(payload.renameStrategy());
        entity.setMaxFileSizeMb(payload.maxFileSizeMb());
        entity.setAllowedMimeTypes(payload.allowedMimeTypes());
        entity.setDefaultFlag(payload.defaultStorage() ? 1 : 0);
        entity.setRetainFileOnRecordDelete(payload.retainFileOnRecordDelete() ? 1 : 0);
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
            String status
    ) {
    }

}
