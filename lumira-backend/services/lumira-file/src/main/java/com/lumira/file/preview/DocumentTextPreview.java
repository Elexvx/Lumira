package com.lumira.file.preview;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.util.StringUtils;

public final class DocumentTextPreview {

    private static final long MAX_TEXT_FILE_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_OFFICE_FILE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_PREVIEW_CHARS = 200_000;
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "csv", "json", "log"
    );
    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );
    private static final String TRUNCATED_NOTICE = "\n\n—— 预览内容已截断，请下载原文件查看完整内容 ——";

    private DocumentTextPreview() {
    }

    public static String read(Path source, String fileExtension, String mimeType) {
        if (source == null || !Files.isRegularFile(source)) {
            throw badRequest("预览文件不存在或已被移除");
        }
        String extension = normalize(fileExtension);
        String normalizedMimeType = normalize(mimeType);
        boolean plainText = PLAIN_TEXT_EXTENSIONS.contains(extension) || normalizedMimeType.startsWith("text/");
        boolean officeDocument = OFFICE_EXTENSIONS.contains(extension)
                || normalizedMimeType.contains("word")
                || normalizedMimeType.contains("excel")
                || normalizedMimeType.contains("powerpoint");
        if (!plainText && !officeDocument) {
            throw badRequest("当前文件格式不支持文本预览");
        }

        try {
            long size = Files.size(source);
            if (size == 0) {
                throw badRequest("文件中没有可预览的文本内容，请下载原文件查看");
            }
            long maxSize = plainText ? MAX_TEXT_FILE_BYTES : MAX_OFFICE_FILE_BYTES;
            if (size > maxSize) {
                throw badRequest("文件过大，无法在线生成文本预览，请下载后查看");
            }
            String content = plainText
                    ? Files.readString(source, StandardCharsets.UTF_8)
                    : parseOfficeDocument(source);
            String normalized = normalizeContent(content);
            if (!StringUtils.hasText(normalized)) {
                throw badRequest("文件中没有可预览的文本内容，请下载原文件查看");
            }
            return normalized.length() > MAX_PREVIEW_CHARS
                    ? normalized.substring(0, MAX_PREVIEW_CHARS) + TRUNCATED_NOTICE
                    : normalized;
        } catch (BizException exception) {
            throw exception;
        } catch (IOException | TikaException exception) {
            throw new BizException(
                    ErrorCode.BAD_REQUEST,
                    "Document text preview failed: " + exception.getMessage(),
                    "文档解析失败，请下载原文件查看"
            );
        }
    }

    private static String parseOfficeDocument(Path source) throws IOException, TikaException {
        try (InputStream inputStream = Files.newInputStream(source)) {
            return new Tika().parseToString(inputStream);
        }
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+\\n", "\n")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message, message);
    }
}
