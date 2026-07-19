package com.lumira.file.upload;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.file.config.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service("fileDocumentUploadService")
public class DocumentUploadService {

    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "rar", "7z", "tar", "gz", "tar.gz", "tgz");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "txt",
            "zip", "rar", "7z", "tar", "gz", "tar.gz", "tgz"
    );
    private static final Map<String, String> EXPECTED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("md", "text/markdown"),
            Map.entry("txt", "text/plain"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip"),
            Map.entry("tar.gz", "application/gzip"),
            Map.entry("tgz", "application/gzip")
    );
    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    private static final Set<String> OPEN_XML_EXTENSIONS = Set.of("docx", "xlsx", "pptx");

    private final UploadProperties uploadProperties;
    private final FileStorageMetrics storageMetrics;
    private final ZipSafetyValidator zipSafetyValidator;

    public DocumentUploadService(UploadProperties uploadProperties, FileStorageMetrics storageMetrics, ZipSafetyValidator zipSafetyValidator) {
        this.uploadProperties = uploadProperties;
        this.storageMetrics = storageMetrics;
        this.zipSafetyValidator = zipSafetyValidator;
    }

    public static boolean supports(String originalFilename, String contentType) {
        String extension = resolveExtension(originalFilename);
        if (StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension)) {
            return true;
        }
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT).trim();
        return EXPECTED_CONTENT_TYPES.containsValue(normalizedContentType)
                || Set.of("application/x-zip-compressed", "application/x-rar-compressed", "application/x-gzip").contains(normalizedContentType);
    }

    public StoredDocument upload(MultipartFile file) {
        return upload(file, null);
    }

    public StoredDocument upload(MultipartFile file, String storageSubPath) {
        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        String publicPath = normalizePublicPath(uploadProperties.getPublicPath());
        String normalizedSubPath = normalizeStorageSubPath(storageSubPath);
        if (StringUtils.hasText(normalizedSubPath)) {
            storageRoot = storageRoot.resolve(normalizedSubPath).normalize();
            publicPath = publicPath + "/" + normalizedSubPath;
        }
        return upload(file, storageRoot, publicPath);
    }

    public StoredDocument upload(MultipartFile file, Path storageRoot, String publicPath) {
        return upload(file, storageRoot, publicPath, uploadProperties.getMaxDocumentSizeBytes());
    }

    public StoredDocument upload(MultipartFile file, Path storageRoot, String publicPath, long maxSizeBytes) {
        return upload(file, storageRoot, publicPath, maxSizeBytes, "APPEND_RANDOM_ID", "*");
    }

    public StoredDocument upload(MultipartFile file, Path storageRoot, String publicPath, long maxSizeBytes, String renameStrategy, String allowedMimeTypes) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请先选择文档文件");
        }
        if (file.getSize() > maxSizeBytes) {
            throw badRequest("文件不能超过 " + readableSize(maxSizeBytes));
        }

        byte[] bytes = readBytes(file);
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = validateExtension(originalFilename);
        String contentType = validateContentType(file.getContentType(), extension);
        validateAllowedMimeType(contentType, allowedMimeTypes);
        validateFileContent(bytes, extension);

        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String generatedName = buildStoredFileName(originalFilename, extension, renameStrategy);
        String relativePath = dateFolder + "/" + generatedName;

        Path normalizedStorageRoot = storageRoot == null
                ? Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize()
                : storageRoot.toAbsolutePath().normalize();
        Path target = normalizedStorageRoot.resolve(relativePath).normalize();
        if (!target.startsWith(normalizedStorageRoot)) {
            throw badRequest("文件存储路径无效");
        }
        Instant writeStartedAt = Instant.now();
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            storageMetrics.recordSucceeded("write", "local", Duration.between(writeStartedAt, Instant.now()));
        } catch (IOException exception) {
            storageMetrics.recordFailed("write", "local", Duration.between(writeStartedAt, Instant.now()));
            String message = "文件上传失败，请检查存储空间配置或稍后重试";
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传失败: " + exception.getMessage(), message);
        }

        String publicUrl = normalizePublicPath(StringUtils.hasText(publicPath) ? publicPath : uploadProperties.getPublicPath()) + "/" + relativePath;
        String previewMode = resolvePreviewMode(extension);

        return new StoredDocument(
                StringUtils.hasText(originalFilename) ? originalFilename : generatedName,
                generatedName,
                extension,
                contentType,
                file.getSize(),
                relativePath,
                publicUrl,
                previewMode,
                isPreviewable(previewMode)
        );
    }

    private String normalizeStorageSubPath(String storageSubPath) {
        if (!StringUtils.hasText(storageSubPath)) {
            return null;
        }
        String normalized = storageSubPath.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains(":")) {
            throw badRequest("文件存储路径无效");
        }
        try {
            Path subPath = Path.of(normalized).normalize();
            if (subPath.isAbsolute() || subPath.startsWith("..")) {
                throw badRequest("文件存储路径无效");
            }
            return subPath.toString().replace('\\', '/');
        } catch (InvalidPathException exception) {
            throw badRequest("文件存储路径无效");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw badRequest("读取文档文件失败");
        }
    }

    private String validateExtension(String originalFilename) {
        String extension = resolveExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            throw badRequest("文件必须包含格式后缀");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest("仅允许上传图片、PDF、Word、Excel、PPT、Markdown、TXT 或压缩包文件");
        }
        return extension;
    }

    private String validateContentType(String contentType, String extension) {
        String expectedContentType = EXPECTED_CONTENT_TYPES.get(extension);
        if (!StringUtils.hasText(contentType)) {
            return expectedContentType;
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT).trim();
        if (OCTET_STREAM_CONTENT_TYPE.equals(normalizedContentType)) {
            return expectedContentType;
        }
        if (ARCHIVE_EXTENSIONS.contains(extension)) {
            if (isAcceptedArchiveContentType(extension, normalizedContentType)) {
                return expectedContentType;
            }
            throw badRequest("压缩包 Content-Type 与文件格式不一致");
        }
        if (OPEN_XML_EXTENSIONS.contains(extension) && "application/zip".equals(normalizedContentType)) {
            return expectedContentType;
        }
        if ("md".equals(extension) && Set.of("text/markdown", "text/x-markdown", "text/plain").contains(normalizedContentType)) {
            return normalizedContentType;
        }
        if (Set.of("md", "txt").contains(extension) && normalizedContentType.startsWith("text/")) {
            return normalizedContentType;
        }
        if (!expectedContentType.equals(normalizedContentType)) {
            throw badRequest("文档 Content-Type 与文件格式不一致");
        }
        return normalizedContentType;
    }

    private void validateAllowedMimeType(String contentType, String allowedMimeTypes) {
        if (!StringUtils.hasText(allowedMimeTypes) || "*".equals(allowedMimeTypes.trim())) {
            return;
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT).trim();
        for (String allowed : allowedMimeTypes.split("[,，;\\s]+")) {
            String normalizedAllowed = allowed.trim().toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(normalizedAllowed)) {
                continue;
            }
            if ("*".equals(normalizedAllowed)
                    || normalizedContentType.equals(normalizedAllowed)
                    || (normalizedAllowed.endsWith("/*") && normalizedContentType.startsWith(normalizedAllowed.substring(0, normalizedAllowed.length() - 1)))) {
                return;
            }
        }
        throw badRequest("当前存储空间不允许上传该文件类型");
    }

    private String buildStoredFileName(String originalFilename, String extension, String renameStrategy) {
        String normalizedStrategy = StringUtils.hasText(renameStrategy) ? renameStrategy.trim().toUpperCase(Locale.ROOT) : "APPEND_RANDOM_ID";
        String safeOriginalName = safeOriginalFilename(originalFilename, extension);
        String baseName = safeOriginalName.substring(0, safeOriginalName.length() - extension.length() - 1);
        return switch (normalizedStrategy) {
            case "KEEP_ORIGINAL" -> safeOriginalName;
            case "RANDOM_STRING" -> UUID.randomUUID().toString().replace("-", "") + "." + extension;
            default -> baseName + "_" + shortId() + "." + extension;
        };
    }

    private String safeOriginalFilename(String originalFilename, String extension) {
        String fallback = "document." + extension;
        if (!StringUtils.hasText(originalFilename)) {
            return fallback;
        }
        String filename = Path.of(originalFilename).getFileName().toString();
        filename = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]").matcher(filename).replaceAll("_");
        if (!StringUtils.hasText(filename) || ".".equals(filename) || "..".equals(filename)) {
            return fallback;
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            String baseName = StringUtils.stripFilenameExtension(filename);
            filename = (StringUtils.hasText(baseName) ? baseName : "document") + "." + extension;
        }
        return filename;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void validateFileContent(byte[] bytes, String extension) {
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D);
            case "doc", "xls", "ppt" -> isOleCompoundFile(bytes);
            case "docx" -> isOpenXmlPackage(bytes, "word/");
            case "xlsx" -> isOpenXmlPackage(bytes, "xl/");
            case "pptx" -> isOpenXmlPackage(bytes, "ppt/");
            case "md", "txt" -> isUtf8LikeText(bytes);
            case "zip" -> isZipArchive(bytes);
            case "rar" -> startsWith(bytes, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07);
            case "7z" -> startsWith(bytes, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C);
            case "tar" -> hasAsciiAt(bytes, 257, "ustar");
            case "gz", "tar.gz", "tgz" -> startsWith(bytes, 0x1F, 0x8B);
            default -> false;
        };
        if (!valid) {
            throw badRequest(ARCHIVE_EXTENSIONS.contains(extension)
                    ? "压缩包内容与声明格式不一致"
                    : "文档文件内容与声明格式不一致");
        }
    }

    private boolean isAcceptedArchiveContentType(String extension, String contentType) {
        return switch (extension) {
            case "zip" -> Set.of("application/zip", "application/x-zip-compressed").contains(contentType);
            case "rar" -> Set.of("application/vnd.rar", "application/x-rar-compressed").contains(contentType);
            case "7z" -> "application/x-7z-compressed".equals(contentType);
            case "tar" -> "application/x-tar".equals(contentType);
            case "gz", "tar.gz", "tgz" -> Set.of("application/gzip", "application/x-gzip").contains(contentType);
            default -> false;
        };
    }

    private boolean isZipArchive(byte[] bytes) {
        if (!startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)
                && !startsWith(bytes, 0x50, 0x4B, 0x05, 0x06)
                && !startsWith(bytes, 0x50, 0x4B, 0x07, 0x08)) {
            return false;
        }
        zipSafetyValidator.validateArchive(bytes);
        return true;
    }

    private boolean hasAsciiAt(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if ((bytes[offset + index] & 0xFF) != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static String resolveExtension(String originalFilename) {
        String normalizedFilename = originalFilename == null ? "" : originalFilename.trim().toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".tar.gz")) {
            return "tar.gz";
        }
        String extension = StringUtils.getFilenameExtension(normalizedFilename);
        return StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ROOT) : "";
    }

    private BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message, message);
    }

    private boolean isUtf8LikeText(byte[] bytes) {
        if (bytes.length == 0) {
            return true;
        }
        int controlChars = 0;
        for (byte current : bytes) {
            int value = current & 0xFF;
            if (value == 0) {
                return false;
            }
            if (value < 0x20 && value != '\n' && value != '\r' && value != '\t') {
                controlChars++;
            }
        }
        return controlChars <= Math.max(4, bytes.length / 100);
    }

    private boolean isOleCompoundFile(byte[] bytes) {
        return startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
    }

    private boolean isOpenXmlPackage(byte[] bytes, String expectedDirectory) {
        try {
            zipSafetyValidator.validateOpenXmlPackage(bytes, expectedDirectory);
            return true;
        } catch (BizException exception) {
            return false;
        }
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String resolvePreviewMode(String extension) {
        if ("pdf".equals(extension)) {
            return "PDF";
        }
        return "UNSUPPORTED";
    }

    private boolean isPreviewable(String previewMode) {
        return "PDF".equals(previewMode);
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

    private String readableSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return (bytes / (1024L * 1024L)) + "MB";
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + "KB";
        }
        return bytes + "B";
    }

    public record StoredDocument(
            String originalFileName,
            String storedFileName,
            String fileExtension,
            String contentType,
            long fileSizeBytes,
            String relativePath,
            String publicUrl,
            String previewMode,
            boolean previewable
    ) {
    }
}
