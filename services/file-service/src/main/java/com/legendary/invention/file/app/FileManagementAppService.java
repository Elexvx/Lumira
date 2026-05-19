package com.legendary.invention.file.app;

import com.legendary.invention.api.file.FileObjectDTO;
import com.legendary.invention.api.file.StorageSpaceDTO;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.file.config.UploadProperties;
import com.legendary.invention.file.dto.FileStorageSpaceRequest;
import com.legendary.invention.file.upload.DocumentUploadService;
import com.legendary.invention.file.upload.ImageUploadService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileManagementAppService {

    private static final long MAX_PAGE_SIZE = 100L;
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

    private final JdbcTemplate jdbcTemplate;
    private final UploadProperties uploadProperties;
    private final DocumentUploadService documentUploadService;
    private final ImageUploadService imageUploadService;

    public FileManagementAppService(
            JdbcTemplate jdbcTemplate,
            UploadProperties uploadProperties,
            DocumentUploadService documentUploadService,
            ImageUploadService imageUploadService
    ) {
        this.jdbcTemplate = jdbcTemplate;
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
        StringBuilder baseSql = new StringBuilder("""
                from file_object f
                where f.tenant_id = ?
                  and f.deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (!tenantScope) {
            baseSql.append(" and f.uploaded_by = ?");
            params.add(currentUser.getUserId());
        }
        if (StringUtils.hasText(keyword)) {
            baseSql.append(" and (f.original_filename like ? or f.category like ? or f.tags like ? or f.remark like ?)");
            String likeKeyword = like(keyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        if (StringUtils.hasText(category)) {
            baseSql.append(" and f.category = ?");
            params.add(category.trim());
        }
        if (StringUtils.hasText(fileExtension)) {
            baseSql.append(" and f.file_extension = ?");
            params.add(fileExtension.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(previewMode)) {
            baseSql.append(" and f.preview_mode = ?");
            params.add(previewMode.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(bucket)) {
            baseSql.append(" and f.bucket = ?");
            params.add(normalizeStorageKey(bucket));
        }

        String sortColumn = StringUtils.hasText(sortField)
                ? SORT_COLUMN_MAPPING.getOrDefault(sortField, DEFAULT_SORT_COLUMN)
                : DEFAULT_SORT_COLUMN;
        String sortDirection = "ascend".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        String selectSql = """
                select
                    f.id,
                    f.tenant_id,
                    f.uploaded_by,
                    f.uploaded_by_name,
                    f.original_filename,
                    f.storage_type,
                    f.bucket,
                    f.object_key,
                    f.file_extension,
                    f.content_type,
                    f.file_size,
                    f.object_key as storage_path,
                    f.public_url,
                    f.public_url as preview_url,
                    f.public_url as download_url,
                    f.preview_mode,
                    f.previewable_flag,
                    f.category,
                    f.tags,
                    f.remark,
                    f.status,
                    f.created_at,
                    f.updated_at
                """ + baseSql
                + " order by " + sortColumn + " " + sortDirection
                + " limit ? offset ?";
        return pageQuery(selectSql, "select count(1) " + baseSql, pageNo, pageSize, params);
    }

    public FileObjectDTO getFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileObjectDTO file = queryFile(currentTenantId(currentUser), fileId, tenantScope ? null : currentUser.getUserId());
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
        FileObjectDTO file = queryFile(currentTenantId(currentUser), fileId, tenantScope ? null : currentUser.getUserId());
        if (!shouldRetainStoredFile(currentTenantId(currentUser), file.bucket())) {
            deleteStoredFile(file.storagePath());
        }
        jdbcTemplate.update(
                """
                        update file_object
                        set deleted = 1, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                currentUser.getUserId(),
                LocalDateTime.now(),
                fileId,
                currentTenantId(currentUser)
        );
    }

    public PageResponse<StorageSpaceDTO> listStorageSpaces(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        Long total = jdbcTemplate.queryForObject("select count(1) from file_storage_space where tenant_id = ? and deleted = 0", Long.class, tenantId);
        List<StorageSpaceDTO> records = jdbcTemplate.query(
                """
                        select s.*, coalesce(files.file_count, 0) as file_count, coalesce(files.total_size_bytes, 0) as total_size_bytes
                        from file_storage_space s
                        left join (
                            select bucket, count(1) as file_count, sum(file_size) as total_size_bytes
                            from file_object
                            where tenant_id = ? and deleted = 0
                            group by bucket
                        ) files on files.bucket = s.storage_key
                        where s.tenant_id = ? and s.deleted = 0
                        order by s.default_flag desc, s.id asc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> mapStorageSpace(rs),
                tenantId,
                tenantId,
                safePageSize,
                (safePageNo - 1L) * safePageSize
        );
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
            jdbcTemplate.update(
                    """
                            insert into file_storage_space (
                                tenant_id, title, storage_key, provider, root_path, bucket_name, endpoint, region,
                                access_key_id, access_key_secret, rename_strategy, max_file_size_mb, allowed_mime_types,
                                default_flag, retain_file_on_record_delete, status, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    tenantId, payload.title(), payload.storageKey(), payload.provider(), payload.rootPath(), payload.bucketName(),
                    payload.endpoint(), payload.region(), payload.accessKeyId(), payload.accessKeySecret(), payload.renameStrategy(),
                    payload.maxFileSizeMb(), payload.allowedMimeTypes(), payload.defaultStorage() ? 1 : 0,
                    payload.retainFileOnRecordDelete() ? 1 : 0, payload.status(), currentUser.getUserId(), currentUser.getUserId()
            );
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
        jdbcTemplate.update(
                """
                        update file_storage_space
                        set title = ?, root_path = ?, bucket_name = ?, endpoint = ?, region = ?,
                            access_key_id = ?, access_key_secret = ?, rename_strategy = ?, max_file_size_mb = ?,
                            allowed_mime_types = ?, default_flag = ?, retain_file_on_record_delete = ?,
                            status = ?, updated_by = ?, updated_at = ?
                        where id = ? and tenant_id = ? and deleted = 0
                        """,
                payload.title(), payload.rootPath(), payload.bucketName(), payload.endpoint(), payload.region(),
                payload.accessKeyId(), payload.accessKeySecret(), payload.renameStrategy(), payload.maxFileSizeMb(),
                payload.allowedMimeTypes(), payload.defaultStorage() ? 1 : 0, payload.retainFileOnRecordDelete() ? 1 : 0,
                payload.status(), currentUser.getUserId(), LocalDateTime.now(), id, tenantId
        );
        ensureOneDefaultStorage(tenantId);
        return queryStorageSpaceById(tenantId, id);
    }

    @Transactional
    public void deleteStorageSpace(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        StorageSpaceDTO existing = queryStorageSpaceById(tenantId, id);
        Long fileCount = jdbcTemplate.queryForObject("select count(1) from file_object where tenant_id = ? and bucket = ? and deleted = 0", Long.class, tenantId, existing.storageKey());
        if (fileCount != null && fileCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间下仍有文件，不能删除");
        }
        if (Boolean.TRUE.equals(existing.defaultStorage())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "默认存储空间不能删除");
        }
        jdbcTemplate.update("update file_storage_space set deleted = 1, updated_by = ?, updated_at = ? where id = ? and tenant_id = ? and deleted = 0", currentUser.getUserId(), LocalDateTime.now(), id, tenantId);
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileObjectDTO file = queryFile(currentTenantId(currentUser), fileId, tenantScope ? null : currentUser.getUserId());
        Path target = resolveFilePath(file.storagePath());
        if (target == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件路径无效");
        }
        return target;
    }

    private FileObjectDTO queryFile(Long tenantId, Long fileId, Long uploadedBy) {
        StringBuilder sql = new StringBuilder("""
                select
                    f.id,
                    f.tenant_id,
                    f.uploaded_by,
                    f.uploaded_by_name,
                    f.original_filename,
                    f.storage_type,
                    f.bucket,
                    f.object_key,
                    f.file_extension,
                    f.content_type,
                    f.file_size,
                    f.object_key as storage_path,
                    f.public_url,
                    f.public_url as preview_url,
                    f.public_url as download_url,
                    f.preview_mode,
                    f.previewable_flag,
                    f.category,
                    f.tags,
                    f.remark,
                    f.status,
                    f.created_at,
                    f.updated_at
                from file_object f
                where f.id = ? and f.tenant_id = ? and f.deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(fileId);
        params.add(tenantId);
        if (uploadedBy != null) {
            sql.append(" and f.uploaded_by = ?");
            params.add(uploadedBy);
        }
        List<FileObjectDTO> list = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapFileObject(rs), params.toArray());
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return list.get(0);
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
        jdbcTemplate.update(
                """
                        insert into file_object (
                            tenant_id,
                            storage_type,
                            bucket,
                            object_key,
                            uploaded_by,
                            uploaded_by_name,
                            original_filename,
                            file_extension,
                            content_type,
                            file_size,
                            public_url,
                            preview_mode,
                            previewable_flag,
                            category,
                            tags,
                            remark,
                            status,
                            created_by,
                            created_at,
                            updated_by,
                            updated_at,
                            deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?, ?, ?, 0)
                        """,
                tenantId,
                storageType,
                bucket,
                objectKey,
                currentUser.getUserId(),
                currentUser.getUsername(),
                originalFilename,
                fileExtension,
                contentType,
                fileSizeBytes,
                publicUrl,
                previewMode,
                previewable ? 1 : 0,
                normalizeText(category),
                normalizeText(normalizeTags(tags)),
                normalizeText(remark),
                currentUser.getUserId(),
                now,
                currentUser.getUserId(),
                now
        );

        Long insertedId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        if (insertedId == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传记录保存失败");
        }
        return insertedId;
    }

    private FileObjectDTO getInsertedFile(Long tenantId, Long insertedId) {
        FileObjectDTO fileObject = queryFile(tenantId, insertedId, null);
        return enrich(fileObject);
    }

    private StorageSpaceDTO getDefaultStorageSpace(Long tenantId) {
        List<StorageSpaceDTO> list = jdbcTemplate.query(
                """
                        select s.*, 0 as file_count, 0 as total_size_bytes
                        from file_storage_space s
                        where s.tenant_id = ? and s.deleted = 0
                        order by s.default_flag desc, s.id asc
                        limit 1
                        """,
                (rs, rowNum) -> mapStorageSpace(rs),
                tenantId
        );
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return new StorageSpaceDTO(null, tenantId, "Local storage", "local", "LOCAL", "storage/uploads/", null, null, null, null, false, "APPEND_RANDOM_ID", 20, "*", true, false, "ENABLED", 0L, 0L, "0B", null, null);
    }

    private StorageSpaceDTO queryStorageSpace(Long tenantId, String storageKey) {
        List<StorageSpaceDTO> list = jdbcTemplate.query(storageSpaceSelectSql("where s.tenant_id = ? and s.storage_key = ? and s.deleted = 0"), (rs, rowNum) -> mapStorageSpace(rs), tenantId, tenantId, storageKey);
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        return list.get(0);
    }

    private StorageSpaceDTO queryStorageSpaceById(Long tenantId, Long id) {
        List<StorageSpaceDTO> list = jdbcTemplate.query(storageSpaceSelectSql("where s.tenant_id = ? and s.id = ? and s.deleted = 0"), (rs, rowNum) -> mapStorageSpace(rs), tenantId, tenantId, id);
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        return list.get(0);
    }

    private String storageSpaceSelectSql(String whereClause) {
        return """
                select s.*, coalesce(files.file_count, 0) as file_count, coalesce(files.total_size_bytes, 0) as total_size_bytes
                from file_storage_space s
                left join (
                    select bucket, count(1) as file_count, sum(file_size) as total_size_bytes
                    from file_object
                    where tenant_id = ? and deleted = 0
                    group by bucket
                ) files on files.bucket = s.storage_key
                """ + whereClause;
    }

    private boolean shouldRetainStoredFile(Long tenantId, String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return false;
        }
        try {
            Boolean retain = jdbcTemplate.queryForObject(
                    "select retain_file_on_record_delete from file_storage_space where tenant_id = ? and storage_key = ? and deleted = 0 limit 1",
                    Boolean.class,
                    tenantId,
                    bucket
            );
            return Boolean.TRUE.equals(retain);
        } catch (EmptyResultDataAccessException exception) {
            return false;
        }
    }

    private void clearDefaultStorage(Long tenantId) {
        jdbcTemplate.update("update file_storage_space set default_flag = 0 where tenant_id = ? and deleted = 0", tenantId);
    }

    private void ensureOneDefaultStorage(Long tenantId) {
        Long count = jdbcTemplate.queryForObject("select count(1) from file_storage_space where tenant_id = ? and deleted = 0 and default_flag = 1", Long.class, tenantId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("update file_storage_space set default_flag = 1 where tenant_id = ? and deleted = 0 order by id asc limit 1", tenantId);
    }

    private String relativePathFromPublicUrl(String publicUrl) {
        return publicUrl;
    }

    private String resolvePreviewMode(String extension, String contentType) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (List.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico").contains(normalizedExtension)
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

    private String like(String value) {
        return "%" + value.trim() + "%";
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
            accessKeySecret = jdbcTemplate.queryForObject("select access_key_secret from file_storage_space where id = ? and tenant_id = ?", String.class, existing.id(), existing.tenantId());
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

    private FileObjectDTO mapFileObject(ResultSet rs) throws SQLException {
        Long fileSize = rs.getLong("file_size");
        if (rs.wasNull()) {
            fileSize = null;
        }
        Long tenantId = rs.getLong("tenant_id");
        if (rs.wasNull()) {
            tenantId = null;
        }
        Long uploadedBy = rs.getLong("uploaded_by");
        if (rs.wasNull()) {
            uploadedBy = null;
        }
        Boolean previewable = rs.getBoolean("previewable_flag");
        if (rs.wasNull()) {
            previewable = null;
        }
        return new FileObjectDTO(
                rs.getLong("id"),
                tenantId,
                uploadedBy,
                rs.getString("uploaded_by_name"),
                rs.getString("original_filename"),
                rs.getString("object_key"),
                rs.getString("storage_type"),
                rs.getString("bucket"),
                rs.getString("file_extension"),
                rs.getString("content_type"),
                fileSize,
                null,
                rs.getString("storage_path"),
                rs.getString("public_url"),
                rs.getString("preview_url"),
                rs.getString("download_url"),
                rs.getString("preview_mode"),
                previewable,
                rs.getString("category"),
                rs.getString("tags"),
                rs.getString("remark"),
                rs.getString("status"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        );
    }

    private StorageSpaceDTO mapStorageSpace(ResultSet rs) throws SQLException {
        Long totalSizeBytes = rs.getLong("total_size_bytes");
        if (rs.wasNull()) {
            totalSizeBytes = 0L;
        }
        Long fileCount = rs.getLong("file_count");
        if (rs.wasNull()) {
            fileCount = 0L;
        }
        String secret = rs.getString("access_key_secret");
        return new StorageSpaceDTO(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("title"),
                rs.getString("storage_key"),
                rs.getString("provider"),
                rs.getString("root_path"),
                rs.getString("bucket_name"),
                rs.getString("endpoint"),
                rs.getString("region"),
                rs.getString("access_key_id"),
                StringUtils.hasText(secret),
                rs.getString("rename_strategy"),
                rs.getInt("max_file_size_mb"),
                rs.getString("allowed_mime_types"),
                rs.getBoolean("default_flag"),
                rs.getBoolean("retain_file_on_record_delete"),
                rs.getString("status"),
                fileCount,
                totalSizeBytes,
                readableSize(totalSizeBytes),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
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

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        List<Object> pagedParams = new ArrayList<>(params);
        pagedParams.add(safePageSize);
        pagedParams.add((safePageNo - 1L) * safePageSize);
        List<FileObjectDTO> records = jdbcTemplate.query(selectSql, (rs, rowNum) -> enrich(mapFileObject(rs)), pagedParams.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords((List<T>) records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }
}
