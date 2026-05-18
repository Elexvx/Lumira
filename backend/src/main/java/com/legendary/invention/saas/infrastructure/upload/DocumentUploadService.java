package com.legendary.invention.saas.infrastructure.upload;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DocumentUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "ppt",
            "pptx"
    );
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of("html", "htm", "js", "mjs", "svg", "xml", "xhtml");

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
        validateDocumentContent(file, extension);

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
            if (FORBIDDEN_EXTENSIONS.contains(normalizedExtension)) {
                return "";
            }
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

    private void validateDocumentContent(MultipartFile file, String extension) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        try {
            byte[] header = readHeader(file, 16);
            if (looksLikeTextMarkup(header)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "不允许将 HTML、脚本或 SVG 作为文档上传");
            }
            switch (normalizedExtension) {
                case "pdf" -> requirePdf(header);
                case "docx" -> requireOfficeOpenXml(file, "word/");
                case "xlsx" -> requireOfficeOpenXml(file, "xl/");
                case "pptx" -> requireOfficeOpenXml(file, "ppt/");
                case "doc", "xls", "ppt" -> requireOleDocument(header);
                default -> throw new BizException(ErrorCode.BAD_REQUEST, "仅允许上传 PDF、Word、Excel、PPT 文件");
            }
        } catch (IOException exception) {
            throw new BizException(ErrorCode.BAD_REQUEST, "无法读取文件内容，请重新上传");
        }
    }

    private byte[] readHeader(MultipartFile file, int size) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[size];
            int length = inputStream.read(header);
            if (length < 0) {
                return new byte[0];
            }
            if (length == size) {
                return header;
            }
            byte[] actual = new byte[length];
            System.arraycopy(header, 0, actual, 0, length);
            return actual;
        }
    }

    private boolean looksLikeTextMarkup(byte[] header) {
        String prefix = new String(header, java.nio.charset.StandardCharsets.UTF_8)
                .stripLeading()
                .toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html")
                || prefix.startsWith("<html")
                || prefix.startsWith("<script")
                || prefix.startsWith("<svg")
                || prefix.startsWith("<?xml")
                || prefix.startsWith("javascript:");
    }

    private void requirePdf(byte[] header) {
        if (header.length < 4 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
            throw new BizException(ErrorCode.BAD_REQUEST, "PDF 文件头校验失败");
        }
    }

    private void requireOleDocument(byte[] header) {
        byte[] oleHeader = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        if (header.length < oleHeader.length) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Office 文档文件头校验失败");
        }
        for (int index = 0; index < oleHeader.length; index++) {
            if (header[index] != oleHeader[index]) {
                throw new BizException(ErrorCode.BAD_REQUEST, "Office 文档文件头校验失败");
            }
        }
    }

    private void requireOfficeOpenXml(MultipartFile file, String requiredPrefix) throws IOException {
        boolean hasZipHeader = false;
        boolean hasContentTypes = false;
        boolean hasRequiredOfficePart = false;
        try (InputStream inputStream = file.getInputStream(); ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                hasZipHeader = true;
                String entryName = entry.getName();
                if ("[Content_Types].xml".equals(entryName)) {
                    hasContentTypes = true;
                }
                if (entryName != null && entryName.startsWith(requiredPrefix)) {
                    hasRequiredOfficePart = true;
                }
                if (hasContentTypes && hasRequiredOfficePart) {
                    return;
                }
            }
        }
        if (!hasZipHeader || !hasContentTypes || !hasRequiredOfficePart) {
            throw new BizException(ErrorCode.BAD_REQUEST, "Office OpenXML 文件结构校验失败");
        }
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

    public static final class StoredDocument {
        private final String originalFileName;
        private final String storedFileName;
        private final String fileExtension;
        private final String contentType;
        private final long fileSizeBytes;
        private final String relativePath;
        private final String publicUrl;
        private final String previewMode;
        private final boolean previewable;

        public StoredDocument(
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
            this.originalFileName = originalFileName;
            this.storedFileName = storedFileName;
            this.fileExtension = fileExtension;
            this.contentType = contentType;
            this.fileSizeBytes = fileSizeBytes;
            this.relativePath = relativePath;
            this.publicUrl = publicUrl;
            this.previewMode = previewMode;
            this.previewable = previewable;
        }

        public String getOriginalFileName() {
            return originalFileName;
        }

        public String getStoredFileName() {
            return storedFileName;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getContentType() {
            return contentType;
        }

        public long getFileSizeBytes() {
            return fileSizeBytes;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public String getPublicUrl() {
            return publicUrl;
        }

        public String getPreviewMode() {
            return previewMode;
        }

        public boolean isPreviewable() {
            return previewable;
        }
    }
}
