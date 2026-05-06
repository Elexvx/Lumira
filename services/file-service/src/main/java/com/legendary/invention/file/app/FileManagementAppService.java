package com.legendary.invention.file.app;

import com.legendary.invention.api.file.FileObjectDTO;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.common.vo.PageResponse;
import com.legendary.invention.file.config.UploadProperties;
import com.legendary.invention.file.upload.DocumentUploadService;
import com.legendary.invention.file.upload.ImageUploadService;
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
    public FileObjectDTO uploadDocument(CurrentUser currentUser, MultipartFile file, String category, String tags, String remark) {
        DocumentUploadService.StoredDocument storedDocument = documentUploadService.upload(file);
        Long tenantId = currentTenantId(currentUser);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                "LOCAL",
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
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                "LOCAL",
                storedImage.relativePath(),
                storedImage.originalFileName(),
                normalizeText(StringUtils.getFilenameExtension(storedImage.originalFileName())),
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
        deleteStoredFile(file.storagePath());
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
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENABLED', ?, ?, ?, ?, 0)
                        """,
                tenantId,
                storageType,
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
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            return 1001L;
        }
        return currentUser.getCurrentTenantId();
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
