package com.lumira.saas.modules.system.workorder.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderHtmlSanitizerTest {

    @Test
    void sanitizeShouldRemoveExecutableMarkupAndUnsafeUrls() {
        String sanitized = WorkOrderHtmlSanitizer.sanitize(
                """
                        <p onclick="alert(1)">Hello</p>
                        <script>alert(1)</script>
                        <a href="javascript:alert(1)">bad</a>
                        <img src="https://cdn.example.com/image.png" onerror="alert(1)">
                        """
        );

        assertThat(sanitized)
                .contains("<p>Hello</p>")
                .contains("https://cdn.example.com/image.png")
                .doesNotContain("onclick", "<script", "javascript:", "onerror");
    }

    @Test
    void sanitizeShouldPreserveSupportedFormattingAndUploadedImagePaths() {
        String sanitized = WorkOrderHtmlSanitizer.sanitize(
                "<p><strong>Details</strong></p><img src=\"/api/v1/files/42/preview\" alt=\"proof\">"
        );

        assertThat(sanitized)
                .contains("<strong>Details</strong>")
                .contains("/api/v1/files/42/preview")
                .contains("alt=\"proof\"");
        assertThat(WorkOrderHtmlSanitizer.hasMeaningfulContent(sanitized)).isTrue();
    }

    @Test
    void scriptOnlyInputShouldNotCountAsMeaningfulContent() {
        String sanitized = WorkOrderHtmlSanitizer.sanitize("<script>alert(1)</script>");

        assertThat(WorkOrderHtmlSanitizer.hasMeaningfulContent(sanitized)).isFalse();
    }
}
