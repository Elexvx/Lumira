package com.legendary.invention.file.upload;

import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.file.config.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service("fileImageUploadService")
public class ImageUploadService {

    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final int MAX_IMAGE_WIDTH = 10_000;
    private static final int MAX_IMAGE_HEIGHT = 10_000;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "bmp", "image/bmp"
    );

    private final UploadProperties uploadProperties;

    public ImageUploadService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public static boolean supports(String originalFilename, String contentType) {
        String extension = StringUtils.getFilenameExtension(originalFilename == null ? "" : originalFilename);
        if (StringUtils.hasText(extension) && ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).trim().startsWith("image/");
    }

    public StoredImage upload(MultipartFile file) {
        return upload(file, null);
    }

    public StoredImage upload(MultipartFile file, String storageSubPath) {
        Path storageRoot = Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize();
        String publicPath = normalizePublicPath(uploadProperties.getPublicPath());
        String normalizedSubPath = normalizeStorageSubPath(storageSubPath);
        if (StringUtils.hasText(normalizedSubPath)) {
            storageRoot = storageRoot.resolve(normalizedSubPath).normalize();
            publicPath = publicPath + "/" + normalizedSubPath;
        }
        return upload(file, storageRoot, publicPath);
    }

    public StoredImage upload(MultipartFile file, Path storageRoot, String publicPath) {
        return upload(file, storageRoot, publicPath, uploadProperties.getMaxImageSizeBytes());
    }

    public StoredImage upload(MultipartFile file, Path storageRoot, String publicPath, long maxSizeBytes) {
        return upload(file, storageRoot, publicPath, maxSizeBytes, "APPEND_RANDOM_ID", "*");
    }

    public StoredImage upload(MultipartFile file, Path storageRoot, String publicPath, long maxSizeBytes, String renameStrategy, String allowedMimeTypes) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先选择图片文件");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片不能超过 " + readableSize(maxSizeBytes));
        }

        byte[] bytes = readBytes(file);
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = validateExtension(originalFilename);
        String contentType = validateContentType(file.getContentType(), extension);
        validateAllowedMimeType(contentType, allowedMimeTypes);
        validateMagicBytes(bytes, extension);
        validateDecodedImage(bytes, extension);

        Path normalizedStorageRoot = storageRoot == null
                ? Path.of(uploadProperties.getStorageRoot()).toAbsolutePath().normalize()
                : storageRoot.toAbsolutePath().normalize();
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String normalizedExtension = "jpeg".equals(extension) ? "jpg" : extension;
        String generatedName = buildStoredFileName(originalFilename, normalizedExtension, renameStrategy);
        Path target = normalizedStorageRoot
                .resolve(dateFolder)
                .resolve(generatedName)
                .normalize();
        if (!target.startsWith(normalizedStorageRoot)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片存储路径无效");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传失败: " + exception.getMessage());
        }

        return new StoredImage(
                StringUtils.hasText(originalFilename) ? originalFilename : generatedName,
                generatedName,
                "." + normalizedExtension,
                contentType,
                file.getSize(),
                dateFolder + "/" + generatedName,
                normalizePublicPath(StringUtils.hasText(publicPath) ? publicPath : uploadProperties.getPublicPath()) + "/" + dateFolder + "/" + generatedName
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
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "读取图片文件失败");
        }
    }

    private String validateExtension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片文件必须包含格式后缀");
        }
        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持 PNG、JPG、GIF、BMP 图片，禁止上传 SVG");
        }
        return normalizedExtension;
    }

    private String validateContentType(String contentType, String extension) {
        if (!StringUtils.hasText(contentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片 Content-Type 不能为空");
        }
        String normalizedContentType = contentType.toLowerCase(Locale.ROOT).trim();
        String expectedContentType = EXTENSION_CONTENT_TYPES.get(extension);
        if (!expectedContentType.equals(normalizedContentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片 Content-Type 与文件格式不一致");
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
        throw new BizException(ErrorCode.BAD_REQUEST, "当前存储空间不允许上传该文件类型");
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
        String fallback = "image." + extension;
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
            filename = (StringUtils.hasText(baseName) ? baseName : "image") + "." + extension;
        }
        return filename;
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void validateMagicBytes(byte[] bytes, String extension) {
        boolean valid = switch (extension) {
            case "png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                    || startsWith(bytes, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61);
            case "bmp" -> startsWith(bytes, 0x42, 0x4D);
            default -> false;
        };
        if (!valid) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片文件内容与声明格式不一致");
        }
    }

    private void validateDecodedImage(byte[] bytes, String extension) {
        String normalizedExtension = "jpg".equals(extension) ? "jpeg" : extension;
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(normalizedExtension);
            if (!readers.hasNext()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "当前运行环境不支持解析该图片格式");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_WIDTH || height > MAX_IMAGE_HEIGHT || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "图片尺寸超出允许范围");
                }
                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                if (decoded == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "图片内容无法被安全解析");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片内容无法被安全解析");
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

    public record StoredImage(
            String originalFileName,
            String storedFileName,
            String fileExtension,
            String contentType,
            long fileSizeBytes,
            String relativePath,
            String publicUrl
    ) {
    }
}
