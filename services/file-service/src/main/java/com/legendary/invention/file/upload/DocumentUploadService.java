package com.legendary.invention.file.upload;

import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.file.config.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx");
    private static final Set<String> OFFICE_CONTENT_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final UploadProperties uploadProperties;

    public DocumentUploadService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public StoredDocument upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先选择文档文件");
        }
        if (file.getSize() > uploadProperties.getMaxDocumentSizeBytes()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件不能超过 " + readableSize(uploadProperties.getMaxDocumentSizeBytes()));
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = resolveExtension(originalFilename, file.getContentType());
        if (!StringUtils.hasText(extension)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅允许上传 PDF、Word、Excel、PPT 文件");
        }

        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String generatedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = dateFolder + "/" + generatedName;

        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        Path target = storageRoot.resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件上传失败: " + exception.getMessage());
        }

        String publicUrl = normalizePublicPath(uploadProperties.getPublicPath()) + "/" + relativePath;
        String previewMode = resolvePreviewMode(extension, file.getContentType());

        return new StoredDocument(
                originalFilename,
                generatedName,
                extension,
                file.getContentType(),
                file.getSize(),
                relativePath,
                publicUrl,
                previewMode,
                isPreviewable(previewMode)
        );
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (StringUtils.hasText(extension)) {
            String normalizedExtension = extension.toLowerCase(Locale.ROOT);
            if (ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
                return normalizedExtension;
            }
            return "";
        }
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT).trim();
        if ("application/pdf".equals(normalizedContentType)) {
            return "pdf";
        }
        if (OFFICE_CONTENT_TYPES.contains(normalizedContentType)) {
            return switch (normalizedContentType) {
                case "application/msword" -> "doc";
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
                case "application/vnd.ms-excel" -> "xls";
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
                case "application/vnd.ms-powerpoint" -> "ppt";
                case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
                default -> "";
            };
        }
        return "";
    }

    private String resolvePreviewMode(String extension, String contentType) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if ("pdf".equals(normalizedExtension) || "application/pdf".equals(normalizedContentType)) {
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
