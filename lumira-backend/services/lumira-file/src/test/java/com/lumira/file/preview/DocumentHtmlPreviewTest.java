package com.lumira.file.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.common.exception.BizException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentHtmlPreviewTest {

    @TempDir
    Path tempDir;

    @Test
    void renderShouldKeepDocxParagraphsAndTables() throws Exception {
        Path source = tempDir.resolve("application.docx");
        try (XWPFDocument document = new XWPFDocument();
             var output = Files.newOutputStream(source)) {
            document.createParagraph().createRun().setText("Website Information Form");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Website URL");
            table.getRow(0).getCell(1).setText("www.example.com");
            document.write(output);
        }

        String html = DocumentHtmlPreview.render(
                source,
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );

        assertThat(html)
                .contains("Website Information Form")
                .contains("Website URL")
                .contains("www.example.com")
                .contains("<table")
                .contains("Content-Security-Policy")
                .contains("Microsoft YaHei");
    }

    @Test
    void renderShouldSupportLegacyDocCompatibleContent() throws Exception {
        Path source = tempDir.resolve("legacy.doc");
        Files.writeString(
                source,
                "{\\rtf1\\ansi\\deff0 {\\fonttbl {\\f0 Times New Roman;}}"
                        + "\\f0\\fs24 Legacy Word layout preview\\par}"
        );

        assertThat(DocumentHtmlPreview.render(source, "doc", "application/msword"))
                .contains("Legacy Word layout preview");
    }

    @Test
    void renderShouldRejectUnsupportedFormats() throws Exception {
        Path source = tempDir.resolve("archive.zip");
        Files.write(source, new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> DocumentHtmlPreview.render(source, "zip", "application/zip"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持版式预览");
    }
}
