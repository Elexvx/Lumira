package com.legendary.invention.saas.modules.file.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.upload.DocumentUploadService;
import com.legendary.invention.saas.infrastructure.upload.UploadProperties;
import com.legendary.invention.saas.modules.file.vo.FileVO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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

    public FileManagementAppService(JdbcTemplate jdbcTemplate, UploadProperties uploadProperties, DocumentUploadService documentUploadService) {
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
                    f.tenant_id as tenantId,
                    f.uploaded_by as uploadedBy,
                    f.uploaded_by_name as uploadedByName,
                    f.original_filename as originalFileName,
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
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                "LOCAL",
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
        String relativePath = relativePathFromPublicUrl(publicUrl);
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? relativePath : file.getOriginalFilename());
        String extension = normalizeText(StringUtils.getFilenameExtension(originalFilename));
        String contentType = file.getContentType();
        String previewMode = resolvePreviewMode(extension, contentType);
        Long insertedId = insertFileObject(
                currentUser,
                tenantId,
                "LOCAL",
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
                "LOCAL_PATH",
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
        deleteStoredFile(file.getStoragePath());
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

    private FileVO.FileObjectVO getInsertedFile(Long tenantId, Long insertedId) {
        FileVO.FileObjectVO fileObject = queryFile(tenantId, insertedId, null);
        enrich(fileObject);
        return fileObject;
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
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            return com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
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
