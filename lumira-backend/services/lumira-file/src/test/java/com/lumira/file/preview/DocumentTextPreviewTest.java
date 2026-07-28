package com.lumira.file.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.common.exception.BizException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentTextPreviewTest {

    @TempDir
    Path tempDir;

    @Test
    void readShouldPreviewUtf8TextAndNormalizeLineEndings() throws Exception {
        Path source = tempDir.resolve("notes.md");
        Files.writeString(source, "标题\r\n\r\n正文  \r\n");

        assertThat(DocumentTextPreview.read(source, "md", "text/markdown"))
                .isEqualTo("标题\n\n正文");
    }

    @Test
    void readShouldRejectUnsupportedBinaryFormats() throws Exception {
        Path source = tempDir.resolve("archive.zip");
        Files.write(source, new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> DocumentTextPreview.read(source, "zip", "application/zip"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持文本预览");
    }

    @Test
    void readShouldExtractTextFromDocx() throws Exception {
        Path source = tempDir.resolve("application.docx");
        try (XWPFDocument document = new XWPFDocument();
             var output = Files.newOutputStream(source)) {
            document.createParagraph().createRun().setText("Website Information Form");
            document.write(output);
        }

        assertThat(DocumentTextPreview.read(
                source,
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )).contains("Website Information Form");
    }

    @Test
    void readShouldUseTikaForLegacyDocCompatibleContent() throws Exception {
        Path source = tempDir.resolve("legacy.doc");
        Files.writeString(
                source,
                "{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Times New Roman;}}"
                        + "\\f0\\fs24 Legacy Word document preview\\par}"
        );

        assertThat(DocumentTextPreview.read(source, "doc", "application/msword"))
                .contains("Legacy Word document preview");
    }

    @Test
    void readShouldReportEmptyOfficeContent() throws Exception {
        Path source = tempDir.resolve("empty.doc");
        Files.write(source, new byte[0]);

        assertThatThrownBy(() -> DocumentTextPreview.read(source, "doc", "application/msword"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("没有可预览的文本内容");
    }
}
