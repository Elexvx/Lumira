package com.lumira.file.preview;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

public final class DocumentHtmlPreview {

    private static final long MAX_OFFICE_FILE_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_PREVIEW_HTML_CHARS = 5_000_000;
    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );
    private static final String PREVIEW_HEAD = """
            <meta http-equiv="Content-Security-Policy"
                  content="default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data:">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              :root { color-scheme: light; }
              * { box-sizing: border-box; }
              html { min-height: 100%; background: #eef1f5; }
              body {
                width: min(920px, calc(100% - 32px));
                min-height: 1120px;
                margin: 24px auto;
                padding: 64px 72px;
                overflow-wrap: anywhere;
                color: #1f2329;
                background: #fff;
                box-shadow: 0 8px 28px rgba(31, 35, 41, .12);
                font: 15px/1.7 -apple-system, BlinkMacSystemFont, "Segoe UI",
                      "Microsoft YaHei", "PingFang SC", Arial, sans-serif;
              }
              h1, h2, h3, h4, h5, h6 { line-height: 1.35; }
              p { margin: .65em 0; white-space: pre-wrap; }
              table { width: 100%; margin: 1em 0; border-collapse: collapse; }
              th, td { padding: 8px 10px; border: 1px solid #c9cdd4; vertical-align: top; }
              th { background: #f5f6f7; font-weight: 600; }
              img { max-width: 100%; height: auto; }
              a { color: #1677ff; }
              @media (max-width: 700px) {
                html { background: #fff; }
                body {
                  width: 100%;
                  min-height: 100vh;
                  margin: 0;
                  padding: 24px 20px;
                  box-shadow: none;
                }
              }
            </style>
            """;

    private DocumentHtmlPreview() {
    }

    public static String render(Path source, String fileExtension, String mimeType) {
        if (source == null || !Files.isRegularFile(source)) {
            throw badRequest("预览文件不存在或已被移除");
        }
        String extension = normalize(fileExtension);
        String normalizedMimeType = normalize(mimeType);
        boolean officeDocument = OFFICE_EXTENSIONS.contains(extension)
                || normalizedMimeType.contains("word")
                || normalizedMimeType.contains("excel")
                || normalizedMimeType.contains("powerpoint");
        if (!officeDocument) {
            throw badRequest("当前文件格式不支持版式预览");
        }

        try {
            long size = Files.size(source);
            if (size == 0) {
                throw badRequest("文件中没有可预览的内容，请下载原文件查看");
            }
            if (size > MAX_OFFICE_FILE_BYTES) {
                throw badRequest("文件过大，无法在线生成版式预览，请下载后查看");
            }

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, source.getFileName().toString());
            ToXMLContentHandler handler = new ToXMLContentHandler();
            try (InputStream inputStream = Files.newInputStream(source)) {
                new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
            }
            String html = handler.toString();
            if (!StringUtils.hasText(html) || !StringUtils.hasText(stripMarkup(html))) {
                throw badRequest("文件中没有可预览的内容，请下载原文件查看");
            }
            if (html.length() > MAX_PREVIEW_HTML_CHARS) {
                throw badRequest("文档预览内容过大，请下载原文件查看");
            }
            return injectPreviewShell(html);
        } catch (BizException exception) {
            throw exception;
        } catch (IOException | TikaException | SAXException exception) {
            throw new BizException(
                    ErrorCode.BAD_REQUEST,
                    "Document layout preview failed: " + exception.getMessage(),
                    "文档版式预览生成失败，请下载原文件查看"
            );
        }
    }

    private static String injectPreviewShell(String html) {
        int headEnd = html.toLowerCase(Locale.ROOT).indexOf("</head>");
        if (headEnd >= 0) {
            return html.substring(0, headEnd) + PREVIEW_HEAD + html.substring(headEnd);
        }
        return "<!doctype html><html><head>" + PREVIEW_HEAD + "</head><body>" + html + "</body></html>";
    }

    private static String stripMarkup(String html) {
        return html
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message, message);
    }
}
