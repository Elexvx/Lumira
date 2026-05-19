package com.legendary.invention.saas.modules.file.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.upload.DocumentUploadService;
import com.legendary.invention.saas.infrastructure.upload.UploadProperties;
import com.legendary.invention.saas.modules.file.dto.FileStorageSpaceRequest;
import com.legendary.invention.saas.modules.file.vo.FileVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
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

    private final MyBatisQueryOperations jdbcTemplate;
    private final UploadProperties uploadProperties;
    private final DocumentUploadService documentUploadService;

    public FileManagementAppService(MyBatisQueryOperations jdbcTemplate, UploadProperties uploadProperties, DocumentUploadService documentUploadService) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadProperties = uploadProperties;
        this.documentUploadService = documentUploadService;
    }

    public PageResponse<FileVO.FileObjectVO> listFiles(
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
                    f.tenant_id as tenantId,
                    f.uploaded_by as uploadedBy,
                    f.uploaded_by_name as uploadedByName,
                    f.original_filename as originalFileName,
                    f.storage_type as storageType,
                    f.bucket,
                    f.object_key as storedFileName,
                    f.file_extension as fileExtension,
                    f.content_type as mimeType,
                    f.file_size as fileSizeBytes,
                    f.object_key as storagePath,
                    f.public_url as publicUrl,
                    f.public_url as previewUrl,
                    f.public_url as downloadUrl,
                    f.preview_mode as previewMode,
                    f.previewable_flag as previewable,
                    f.category,
                    f.tags,
                    f.remark,
                    f.status,
                    f.created_at as createdAt,
                    f.updated_at as updatedAt
                """ + baseSql
                + " order by " + sortColumn + " " + sortDirection
                + " limit ? offset ?";

        return pageQuery(selectSql, "select count(1) " + baseSql, FileVO.FileObjectVO.class, pageNo, pageSize, params);
    }

    public FileVO.FileObjectVO getFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileVO.FileObjectVO file = queryFile(currentTenantId(currentUser), fileId, tenantScope ? null : currentUser.getUserId());
        enrich(file);
        return file;
    }

    @Transactional
    public FileVO.FileObjectVO uploadFile(CurrentUser currentUser, MultipartFile file, String category, String tags, String remark) {
        DocumentUploadService.StoredDocument storedDocument = documentUploadService.upload(file);
        Long tenantId = currentTenantId(currentUser);
        FileVO.StorageSpaceVO storageSpace = getDefaultStorageSpace(tenantId);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                storageSpace.getProvider(),
                storageSpace.getStorageKey(),
                storedDocument.getRelativePath(),
                storedDocument.getOriginalFileName(),
                storedDocument.getFileExtension(),
                storedDocument.getContentType(),
                storedDocument.getFileSizeBytes(),
                storedDocument.getPublicUrl(),
                storedDocument.getPreviewMode(),
                storedDocument.isPreviewable(),
                StringUtils.hasText(category) ? category : "我的文件",
                tags,
                remark
        );
        return getInsertedFile(tenantId, insertedId);
    }

    @Transactional
    public FileVO.FileObjectVO recordUploadedPublicFile(CurrentUser currentUser, MultipartFile file, String publicUrl, String category, String remark) {
        Long tenantId = currentTenantId(currentUser);
        FileVO.StorageSpaceVO storageSpace = getDefaultStorageSpace(tenantId);
        String relativePath = relativePathFromPublicUrl(publicUrl);
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? relativePath : file.getOriginalFilename());
        String extension = normalizeText(StringUtils.getFilenameExtension(originalFilename));
        String contentType = file.getContentType();
        String previewMode = resolvePreviewMode(extension, contentType);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                storageSpace.getProvider(),
                storageSpace.getStorageKey(),
                relativePath,
                originalFilename,
                extension == null ? "" : extension.toLowerCase(Locale.ROOT),
                contentType,
                file.getSize(),
                publicUrl,
                previewMode,
                !"UNSUPPORTED".equals(previewMode),
                category,
                null,
                remark
        );
        return getInsertedFile(tenantId, insertedId);
    }

    @Transactional
    public FileVO.FileObjectVO recordUploadedLocalFile(CurrentUser currentUser, String originalFilename, Path filePath, String contentType, String category, String remark) {
        Long tenantId = currentTenantId(currentUser);
        FileVO.StorageSpaceVO storageSpace = getDefaultStorageSpace(tenantId);
        Path normalizedPath = filePath.toAbsolutePath().normalize();
        long fileSize = 0L;
        try {
            fileSize = Files.size(normalizedPath);
        } catch (Exception ignored) {
            // Best-effort metadata for non-document upload records.
        }
        String extension = normalizeText(StringUtils.getFilenameExtension(originalFilename));
        String previewMode = resolvePreviewMode(extension, contentType);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                storageSpace.getProvider(),
                storageSpace.getStorageKey(),
                normalizedPath.toString(),
                StringUtils.cleanPath(originalFilename == null ? normalizedPath.getFileName().toString() : originalFilename),
                extension == null ? "" : extension.toLowerCase(Locale.ROOT),
                contentType,
                fileSize,
                null,
                previewMode,
                !"UNSUPPORTED".equals(previewMode),
                category,
                null,
                remark
        );
        return getInsertedFile(tenantId, insertedId);
    }

    @Transactional
    public void deleteFile(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileVO.FileObjectVO file = queryFile(currentTenantId(currentUser), fileId, tenantScope ? null : currentUser.getUserId());
        if (!shouldRetainStoredFile(currentTenantId(currentUser), file.getBucket())) {
            deleteStoredFile(file.getStoragePath());
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

    public PageResponse<FileVO.StorageSpaceVO> listStorageSpaces(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        String selectSql = """
                select
                    s.id,
                    s.tenant_id as tenantId,
                    s.title,
                    s.storage_key as storageKey,
                    s.provider,
                    s.root_path as rootPath,
                    s.bucket_name as bucketName,
                    s.endpoint,
                    s.region,
                    s.access_key_id as accessKeyId,
                    case when s.access_key_secret is not null and s.access_key_secret <> '' then true else false end as secretConfigured,
                    s.rename_strategy as renameStrategy,
                    s.max_file_size_mb as maxFileSizeMb,
                    s.allowed_mime_types as allowedMimeTypes,
                    s.default_flag as defaultStorage,
                    s.retain_file_on_record_delete as retainFileOnRecordDelete,
                    s.status,
                    coalesce(files.file_count, 0) as fileCount,
                    coalesce(files.total_size_bytes, 0) as totalSizeBytes,
                    s.created_at as createdAt,
                    s.updated_at as updatedAt
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
                """;
        String countSql = "select count(1) from file_storage_space where tenant_id = ? and deleted = 0";
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, tenantId);
        List<FileVO.StorageSpaceVO> records = jdbcTemplate.query(
                selectSql,
                new BeanPropertyRowMapper<>(FileVO.StorageSpaceVO.class),
                tenantId,
                tenantId,
                safePageSize,
                (safePageNo - 1L) * safePageSize
        );
        records.forEach(this::enrichStorageSpace);
        PageResponse<FileVO.StorageSpaceVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    public FileVO.StorageSpaceVO getStorageSpace(CurrentUser currentUser, String storageKey) {
        return queryStorageSpace(currentTenantId(currentUser), normalizeStorageKey(storageKey));
    }

    @Transactional
    public FileVO.StorageSpaceVO createStorageSpace(CurrentUser currentUser, FileStorageSpaceRequest request) {
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
                    tenantId,
                    payload.title(),
                    payload.storageKey(),
                    payload.provider(),
                    payload.rootPath(),
                    payload.bucketName(),
                    payload.endpoint(),
                    payload.region(),
                    payload.accessKeyId(),
                    payload.accessKeySecret(),
                    payload.renameStrategy(),
                    payload.maxFileSizeMb(),
                    payload.allowedMimeTypes(),
                    payload.defaultStorage() ? 1 : 0,
                    payload.retainFileOnRecordDelete() ? 1 : 0,
                    payload.status(),
                    currentUser.getUserId(),
                    currentUser.getUserId()
            );
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间标识已存在");
        }
        ensureOneDefaultStorage(tenantId);
        return queryStorageSpace(tenantId, storageKey);
    }

    @Transactional
    public FileVO.StorageSpaceVO updateStorageSpace(CurrentUser currentUser, Long id, FileStorageSpaceRequest request) {
        Long tenantId = currentTenantId(currentUser);
        FileVO.StorageSpaceVO existing = queryStorageSpaceById(tenantId, id);
        StoragePayload payload = normalizeStoragePayload(request, existing.getProvider(), existing.getStorageKey(), existing);
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
                payload.title(),
                payload.rootPath(),
                payload.bucketName(),
                payload.endpoint(),
                payload.region(),
                payload.accessKeyId(),
                payload.accessKeySecret(),
                payload.renameStrategy(),
                payload.maxFileSizeMb(),
                payload.allowedMimeTypes(),
                payload.defaultStorage() ? 1 : 0,
                payload.retainFileOnRecordDelete() ? 1 : 0,
                payload.status(),
                currentUser.getUserId(),
                LocalDateTime.now(),
                id,
                tenantId
        );
        ensureOneDefaultStorage(tenantId);
        return queryStorageSpaceById(tenantId, id);
    }

    @Transactional
    public void deleteStorageSpace(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        FileVO.StorageSpaceVO existing = queryStorageSpaceById(tenantId, id);
        Long fileCount = jdbcTemplate.queryForObject(
                "select count(1) from file_object where tenant_id = ? and bucket = ? and deleted = 0",
                Long.class,
                tenantId,
                existing.getStorageKey()
        );
        if (fileCount != null && fileCount > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "存储空间下仍有文件，不能删除");
        }
        if (Boolean.TRUE.equals(existing.getDefaultStorage())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "默认存储空间不能删除");
        }
        jdbcTemplate.update(
                "update file_storage_space set deleted = 1, updated_by = ?, updated_at = ? where id = ? and tenant_id = ? and deleted = 0",
                currentUser.getUserId(),
                LocalDateTime.now(),
                id,
                tenantId
        );
    }

    public Path resolveFilePath(CurrentUser currentUser, Long fileId, boolean tenantScope) {
        FileVO.FileObjectVO file = queryFile(currentTenantId(currentUser), fileId, tenantScope ? null : currentUser.getUserId());
        Path target = resolveFilePath(file.getStoragePath());
        if (target == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件路径无效");
        }
        return target;
    }

    private FileVO.FileObjectVO queryFile(Long tenantId, Long fileId, Long uploadedBy) {
        StringBuilder sql = new StringBuilder("""
                select
                    f.id,
                    f.tenant_id as tenantId,
                    f.uploaded_by as uploadedBy,
                    f.uploaded_by_name as uploadedByName,
                    f.original_filename as originalFileName,
                    f.storage_type as storageType,
                    f.bucket,
                    f.object_key as storedFileName,
                    f.file_extension as fileExtension,
                    f.content_type as mimeType,
                    f.file_size as fileSizeBytes,
                    f.object_key as storagePath,
                    f.public_url as publicUrl,
                    f.public_url as previewUrl,
                    f.public_url as downloadUrl,
                    f.preview_mode as previewMode,
                    f.previewable_flag as previewable,
                    f.category,
                    f.tags,
                    f.remark,
                    f.status,
                    f.created_at as createdAt,
                    f.updated_at as updatedAt
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
        List<FileVO.FileObjectVO> list = jdbcTemplate.query(
                sql.toString(),
                new BeanPropertyRowMapper<>(FileVO.FileObjectVO.class),
                params.toArray()
        );
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return list.get(0);
    }

    private void enrich(FileVO.FileObjectVO file) {
        file.setFileSizeLabel(readableSize(file.getFileSizeBytes() == null ? 0L : file.getFileSizeBytes()));
        if (!StringUtils.hasText(file.getPreviewUrl())) {
            file.setPreviewUrl(file.getPublicUrl());
        }
        if (!StringUtils.hasText(file.getDownloadUrl())) {
            file.setDownloadUrl(file.getPublicUrl());
        }
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

    private FileVO.FileObjectVO getInsertedFile(Long tenantId, Long insertedId) {
        FileVO.FileObjectVO fileObject = queryFile(tenantId, insertedId, null);
        enrich(fileObject);
        return fileObject;
    }

    private FileVO.StorageSpaceVO getDefaultStorageSpace(Long tenantId) {
        List<FileVO.StorageSpaceVO> list = jdbcTemplate.query(
                """
                        select
                            id, tenant_id as tenantId, title, storage_key as storageKey, provider,
                            root_path as rootPath, bucket_name as bucketName, endpoint, region,
                            access_key_id as accessKeyId,
                            case when access_key_secret is not null and access_key_secret <> '' then true else false end as secretConfigured,
                            rename_strategy as renameStrategy, max_file_size_mb as maxFileSizeMb,
                            allowed_mime_types as allowedMimeTypes, default_flag as defaultStorage,
                            retain_file_on_record_delete as retainFileOnRecordDelete, status,
                            created_at as createdAt, updated_at as updatedAt
                        from file_storage_space
                        where tenant_id = ? and deleted = 0
                        order by default_flag desc, id asc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(FileVO.StorageSpaceVO.class),
                tenantId
        );
        if (!list.isEmpty()) {
            return list.get(0);
        }
        FileVO.StorageSpaceVO fallback = new FileVO.StorageSpaceVO();
        fallback.setProvider("LOCAL");
        fallback.setStorageKey("local");
        fallback.setTitle("Local storage");
        fallback.setDefaultStorage(Boolean.TRUE);
        fallback.setRetainFileOnRecordDelete(Boolean.FALSE);
        return fallback;
    }

    private FileVO.StorageSpaceVO queryStorageSpace(Long tenantId, String storageKey) {
        List<FileVO.StorageSpaceVO> list = jdbcTemplate.query(
                storageSpaceSelectSql("where s.tenant_id = ? and s.storage_key = ? and s.deleted = 0"),
                new BeanPropertyRowMapper<>(FileVO.StorageSpaceVO.class),
                tenantId,
                tenantId,
                storageKey
        );
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        FileVO.StorageSpaceVO item = list.get(0);
        enrichStorageSpace(item);
        return item;
    }

    private FileVO.StorageSpaceVO queryStorageSpaceById(Long tenantId, Long id) {
        List<FileVO.StorageSpaceVO> list = jdbcTemplate.query(
                storageSpaceSelectSql("where s.tenant_id = ? and s.id = ? and s.deleted = 0"),
                new BeanPropertyRowMapper<>(FileVO.StorageSpaceVO.class),
                tenantId,
                tenantId,
                id
        );
        if (list.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "存储空间不存在");
        }
        FileVO.StorageSpaceVO item = list.get(0);
        enrichStorageSpace(item);
        return item;
    }

    private String storageSpaceSelectSql(String whereClause) {
        return """
                select
                    s.id,
                    s.tenant_id as tenantId,
                    s.title,
                    s.storage_key as storageKey,
                    s.provider,
                    s.root_path as rootPath,
                    s.bucket_name as bucketName,
                    s.endpoint,
                    s.region,
                    s.access_key_id as accessKeyId,
                    case when s.access_key_secret is not null and s.access_key_secret <> '' then true else false end as secretConfigured,
                    s.rename_strategy as renameStrategy,
                    s.max_file_size_mb as maxFileSizeMb,
                    s.allowed_mime_types as allowedMimeTypes,
                    s.default_flag as defaultStorage,
                    s.retain_file_on_record_delete as retainFileOnRecordDelete,
                    s.status,
                    coalesce(files.file_count, 0) as fileCount,
                    coalesce(files.total_size_bytes, 0) as totalSizeBytes,
                    s.created_at as createdAt,
                    s.updated_at as updatedAt
                from file_storage_space s
                left join (
                    select bucket, count(1) as file_count, sum(file_size) as total_size_bytes
                    from file_object
                    where tenant_id = ? and deleted = 0
                    group by bucket
                ) files on files.bucket = s.storage_key
                """ + whereClause;
    }

    private void enrichStorageSpace(FileVO.StorageSpaceVO item) {
        item.setTotalSizeLabel(readableSize(item.getTotalSizeBytes() == null ? 0L : item.getTotalSizeBytes()));
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
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from file_storage_space where tenant_id = ? and deleted = 0 and default_flag = 1",
                Long.class,
                tenantId
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                """
                        update file_storage_space
                        set default_flag = 1
                        where tenant_id = ? and deleted = 0
                        order by id asc
                        limit 1
                        """,
                tenantId
        );
    }

    private StoragePayload normalizeStoragePayload(FileStorageSpaceRequest request, String providerFallback, String storageKeyFallback, FileVO.StorageSpaceVO existing) {
        String provider = normalizeProvider(StringUtils.hasText(request.getProvider()) ? request.getProvider() : providerFallback);
        String storageKey = normalizeStorageKey(StringUtils.hasText(request.getStorageKey()) ? request.getStorageKey() : storageKeyFallback);
        String title = defaultIfBlank(request.getTitle(), existing == null ? providerLabel(provider) : existing.getTitle());
        String rootPath = defaultIfBlank(request.getRootPath(), existing == null ? "storage/uploads/" : existing.getRootPath());
        String bucketName = defaultIfBlank(request.getBucketName(), existing == null ? "" : existing.getBucketName());
        String endpoint = defaultIfBlank(request.getEndpoint(), existing == null ? "" : existing.getEndpoint());
        String region = defaultIfBlank(request.getRegion(), existing == null ? "" : existing.getRegion());
        String accessKeyId = defaultIfBlank(request.getAccessKeyId(), existing == null ? "" : existing.getAccessKeyId());
        String accessKeySecret = StringUtils.hasText(request.getAccessKeySecret()) ? request.getAccessKeySecret().trim() : null;
        if (existing != null && !StringUtils.hasText(accessKeySecret)) {
            accessKeySecret = jdbcTemplate.queryForObject(
                    "select access_key_secret from file_storage_space where id = ? and tenant_id = ?",
                    String.class,
                    existing.getId(),
                    existing.getTenantId()
            );
        }
        String renameStrategy = normalizeRenameStrategy(defaultIfBlank(request.getRenameStrategy(), existing == null ? "APPEND_RANDOM_ID" : existing.getRenameStrategy()));
        Integer maxFileSizeMb = request.getMaxFileSizeMb() == null ? (existing == null ? 20 : existing.getMaxFileSizeMb()) : request.getMaxFileSizeMb();
        String allowedMimeTypes = defaultIfBlank(request.getAllowedMimeTypes(), existing == null ? "*" : existing.getAllowedMimeTypes());
        boolean defaultStorage = request.getDefaultStorage() == null ? existing == null || Boolean.TRUE.equals(existing.getDefaultStorage()) : request.getDefaultStorage();
        boolean retain = request.getRetainFileOnRecordDelete() == null ? existing != null && Boolean.TRUE.equals(existing.getRetainFileOnRecordDelete()) : request.getRetainFileOnRecordDelete();
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

    private String relativePathFromPublicUrl(String publicUrl) {
        String publicPath = normalizePublicPath(uploadProperties.getPublicPath());
        if (!StringUtils.hasText(publicUrl) || !publicUrl.startsWith(publicPath + "/")) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件访问路径无效");
        }
        return publicUrl.substring(publicPath.length() + 1);
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

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        List<Object> pagedParams = new ArrayList<>(params);
        pagedParams.add(safePageSize);
        pagedParams.add((safePageNo - 1L) * safePageSize);
        List<T> records = jdbcTemplate.query(selectSql, new BeanPropertyRowMapper<>(voClass), pagedParams.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }
}
