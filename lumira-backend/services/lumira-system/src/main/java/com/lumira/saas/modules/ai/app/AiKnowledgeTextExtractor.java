package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Service
public class AiKnowledgeTextExtractor {

    private static final int MAX_EXTRACTED_CHARS = 800_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "md", "markdown", "txt"
    );

    private Tika tika;

    public ExtractedText extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请先选择知识库文件");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = resolveExtension(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw badRequest("仅支持 PDF、Word、Excel、PPT、Markdown、TXT 文件构建知识库");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw badRequest("读取知识库文件失败");
        }

        String text;
        if ("md".equals(extension) || "markdown".equals(extension) || "txt".equals(extension)) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            try {
                text = tika().parseToString(new ByteArrayInputStream(bytes));
            } catch (IOException | TikaException exception) {
                throw badRequest("解析知识库文件失败: " + safeMessage(exception));
            }
        }

        String normalized = normalizeText(text);
        if (normalized.isBlank()) {
            throw badRequest("文件未解析出可用于知识库的文本内容");
        }
        if (normalized.length() > MAX_EXTRACTED_CHARS) {
            normalized = normalized.substring(0, MAX_EXTRACTED_CHARS);
        }
        return new ExtractedText(extension, normalized);
    }

    private String resolveExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw badRequest("知识库文件必须包含格式后缀");
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT).trim();
    }

    private BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message, message);
    }

    private Tika tika() {
        if (tika == null) {
            tika = new Tika();
        }
        return tika;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public record ExtractedText(String extension, String text) {
    }
}
