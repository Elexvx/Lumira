package com.yourcompany.saas.infrastructure.upload;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/svg+xml",
            "image/x-icon",
            "image/vnd.microsoft.icon"
    );

    private final UploadProperties uploadProperties;

    public ImageUploadService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先选择图片文件");
        }
        if (file.getSize() > uploadProperties.getMaxImageSizeBytes()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片不能超过 " + readableSize(uploadProperties.getMaxImageSizeBytes()));
        }
        String originalFilename = file.getOriginalFilename();
        String extension = resolveExtension(originalFilename, file.getContentType());
        if (!StringUtils.hasText(extension)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持常见图片格式");
        }

        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String generatedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = storageRoot.resolve(dateFolder).resolve(generatedName).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传失败: " + exception.getMessage());
        }

        return normalizePublicPath(uploadProperties.getPublicPath()) + "/" + dateFolder + "/" + generatedName;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (StringUtils.hasText(extension)) {
            String normalizedExtension = extension.toLowerCase(Locale.ROOT);
            if (ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
                return "." + normalizedExtension;
            }
        }
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            return "";
        }
        return switch (normalizedContentType) {
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            case "image/svg+xml" -> ".svg";
            case "image/x-icon", "image/vnd.microsoft.icon" -> ".ico";
            default -> ".png";
        };
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
        if (bytes >= 1024 * 1024) {
            return (bytes / (1024 * 1024)) + "MB";
        }
        if (bytes >= 1024) {
            return (bytes / 1024) + "KB";
        }
        return bytes + "B";
    }
}
